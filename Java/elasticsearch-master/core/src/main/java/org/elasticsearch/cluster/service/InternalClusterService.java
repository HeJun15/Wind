/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.cluster.service;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.*;
import org.elasticsearch.cluster.ClusterState.Builder;
import org.elasticsearch.cluster.block.ClusterBlock;
import org.elasticsearch.cluster.block.ClusterBlocks;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.metadata.ProcessClusterEventTimeoutException;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodeService;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.routing.OperationRouting;
import org.elasticsearch.cluster.routing.RoutingTable;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Priority;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.text.StringText;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.*;
import org.elasticsearch.common.util.iterable.Iterables;
import org.elasticsearch.discovery.Discovery;
import org.elasticsearch.discovery.DiscoveryService;
import org.elasticsearch.node.settings.NodeSettingsService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.elasticsearch.common.util.concurrent.EsExecutors.daemonThreadFactory;

/**
 *
 */
public class InternalClusterService extends AbstractLifecycleComponent<ClusterService> implements ClusterService {

    public static final String SETTING_CLUSTER_SERVICE_SLOW_TASK_LOGGING_THRESHOLD = "cluster.service.slow_task_logging_threshold";
    public static final String SETTING_CLUSTER_SERVICE_RECONNECT_INTERVAL = "cluster.service.reconnect_interval";

    public static final String UPDATE_THREAD_NAME = "clusterService#updateTask";
    private final ThreadPool threadPool;

    private final DiscoveryService discoveryService;

    private final OperationRouting operationRouting;

    private final TransportService transportService;

    private final NodeSettingsService nodeSettingsService;
    private final DiscoveryNodeService discoveryNodeService;
    private final Version version;

    private final TimeValue reconnectInterval;

    private TimeValue slowTaskLoggingThreshold;

    private volatile PrioritizedEsThreadPoolExecutor updateTasksExecutor;

    /**
     * Those 3 state listeners are changing infrequently - CopyOnWriteArrayList is just fine
     */
    private final Collection<ClusterStateListener> priorityClusterStateListeners = new CopyOnWriteArrayList<>();
    private final Collection<ClusterStateListener> clusterStateListeners = new CopyOnWriteArrayList<>();
    private final Collection<ClusterStateListener> lastClusterStateListeners = new CopyOnWriteArrayList<>();
    private final Map<ClusterStateTaskExecutor, List<UpdateTask>> updateTasksPerExecutor = new HashMap<>();
    // TODO this is rather frequently changing I guess a Synced Set would be better here and a dedicated remove API
    private final Collection<ClusterStateListener> postAppliedListeners = new CopyOnWriteArrayList<>();
    private final Iterable<ClusterStateListener> preAppliedListeners = Iterables.concat(priorityClusterStateListeners, clusterStateListeners, lastClusterStateListeners);

    private final LocalNodeMasterListeners localNodeMasterListeners;

    private final Queue<NotifyTimeout> onGoingTimeouts = ConcurrentCollections.newQueue();

    private volatile ClusterState clusterState;

    private final ClusterBlocks.Builder initialBlocks;

    private volatile ScheduledFuture reconnectToNodes;

    @Inject
    public InternalClusterService(Settings settings, DiscoveryService discoveryService, OperationRouting operationRouting, TransportService transportService,
                                  NodeSettingsService nodeSettingsService, ThreadPool threadPool, ClusterName clusterName, DiscoveryNodeService discoveryNodeService, Version version) {
        super(settings);
        this.operationRouting = operationRouting;
        this.transportService = transportService;
        this.discoveryService = discoveryService;
        this.threadPool = threadPool;
        this.nodeSettingsService = nodeSettingsService;
        this.discoveryNodeService = discoveryNodeService;
        this.version = version;

        // will be replaced on doStart.
        this.clusterState = ClusterState.builder(clusterName).build();

        this.nodeSettingsService.setClusterService(this);
        this.nodeSettingsService.addListener(new ApplySettings());

        this.reconnectInterval = this.settings.getAsTime(SETTING_CLUSTER_SERVICE_RECONNECT_INTERVAL, TimeValue.timeValueSeconds(10));

        this.slowTaskLoggingThreshold = this.settings.getAsTime(SETTING_CLUSTER_SERVICE_SLOW_TASK_LOGGING_THRESHOLD, TimeValue.timeValueSeconds(30));

        localNodeMasterListeners = new LocalNodeMasterListeners(threadPool);

        initialBlocks = ClusterBlocks.builder().addGlobalBlock(discoveryService.getNoMasterBlock());
    }

    public NodeSettingsService settingsService() {
        return this.nodeSettingsService;
    }

    @Override
    public void addInitialStateBlock(ClusterBlock block) throws IllegalStateException {
        if (lifecycle.started()) {
            throw new IllegalStateException("can't set initial block when started");
        }
        initialBlocks.addGlobalBlock(block);
    }

    @Override
    public void removeInitialStateBlock(ClusterBlock block) throws IllegalStateException {
        if (lifecycle.started()) {
            throw new IllegalStateException("can't set initial block when started");
        }
        initialBlocks.removeGlobalBlock(block);
    }

    @Override
    protected void doStart() {
        add(localNodeMasterListeners);
        this.clusterState = ClusterState.builder(clusterState).blocks(initialBlocks).build();
        this.updateTasksExecutor = EsExecutors.newSinglePrioritizing(UPDATE_THREAD_NAME, daemonThreadFactory(settings, UPDATE_THREAD_NAME));
        this.reconnectToNodes = threadPool.schedule(reconnectInterval, ThreadPool.Names.GENERIC, new ReconnectToNodes());
        Map<String, String> nodeAttributes = discoveryNodeService.buildAttributes();
        // note, we rely on the fact that its a new id each time we start, see FD and "kill -9" handling
        final String nodeId = DiscoveryService.generateNodeId(settings);
        final TransportAddress publishAddress = transportService.boundAddress().publishAddress();
        DiscoveryNode localNode = new DiscoveryNode(settings.get("name"), nodeId, publishAddress, nodeAttributes, version);
        DiscoveryNodes.Builder nodeBuilder = DiscoveryNodes.builder().put(localNode).localNodeId(localNode.id());
        this.clusterState = ClusterState.builder(clusterState).nodes(nodeBuilder).blocks(initialBlocks).build();
        this.transportService.setLocalNode(localNode);
    }

    @Override
    protected void doStop() {
        FutureUtils.cancel(this.reconnectToNodes);
        for (NotifyTimeout onGoingTimeout : onGoingTimeouts) {
            onGoingTimeout.cancel();
            onGoingTimeout.listener.onClose();
        }
        ThreadPool.terminate(updateTasksExecutor, 10, TimeUnit.SECONDS);
        remove(localNodeMasterListeners);
    }

    @Override
    protected void doClose() {
    }

    @Override
    public DiscoveryNode localNode() {
        return clusterState.getNodes().localNode();
    }

    @Override
    public OperationRouting operationRouting() {
        return operationRouting;
    }

    @Override
    public ClusterState state() {
        return this.clusterState;
    }

    @Override
    public void addFirst(ClusterStateListener listener) {
        priorityClusterStateListeners.add(listener);
    }

    @Override
    public void addLast(ClusterStateListener listener) {
        lastClusterStateListeners.add(listener);
    }

    @Override
    public void add(ClusterStateListener listener) {
        clusterStateListeners.add(listener);
    }

    @Override
    public void remove(ClusterStateListener listener) {
        clusterStateListeners.remove(listener);
        priorityClusterStateListeners.remove(listener);
        lastClusterStateListeners.remove(listener);
        postAppliedListeners.remove(listener);
        for (Iterator<NotifyTimeout> it = onGoingTimeouts.iterator(); it.hasNext(); ) {
            NotifyTimeout timeout = it.next();
            if (timeout.listener.equals(listener)) {
                timeout.cancel();
                it.remove();
            }
        }
    }

    @Override
    public void add(LocalNodeMasterListener listener) {
        localNodeMasterListeners.add(listener);
    }

    @Override
    public void remove(LocalNodeMasterListener listener) {
        localNodeMasterListeners.remove(listener);
    }

    @Override
    public void add(@Nullable final TimeValue timeout, final TimeoutClusterStateListener listener) {
        if (lifecycle.stoppedOrClosed()) {
            listener.onClose();
            return;
        }
        // call the post added notification on the same event thread
        try {
            updateTasksExecutor.execute(new SourcePrioritizedRunnable(Priority.HIGH, "_add_listener_") {
                @Override
                public void run() {
                    if (timeout != null) {
                        NotifyTimeout notifyTimeout = new NotifyTimeout(listener, timeout);
                        notifyTimeout.future = threadPool.schedule(timeout, ThreadPool.Names.GENERIC, notifyTimeout);
                        onGoingTimeouts.add(notifyTimeout);
                    }
                    postAppliedListeners.add(listener);
                    listener.postAdded();
                }
            });
        } catch (EsRejectedExecutionException e) {
            if (lifecycle.stoppedOrClosed()) {
                listener.onClose();
            } else {
                throw e;
            }
        }
    }

    @Override
    public void submitStateUpdateTask(final String source, final ClusterStateUpdateTask updateTask) {
        submitStateUpdateTask(source, updateTask, updateTask, updateTask, updateTask);
    }


    @Override
    public <T> void submitStateUpdateTask(final String source, final T task,
                                          final ClusterStateTaskConfig config,
                                          final ClusterStateTaskExecutor<T> executor,
                                          final ClusterStateTaskListener listener
    ) {
        if (!lifecycle.started()) {
            return;
        }
        try {
            final UpdateTask<T> updateTask = new UpdateTask<>(source, task, config, executor, listener);

            synchronized (updateTasksPerExecutor) {
                updateTasksPerExecutor.computeIfAbsent(executor, k -> new ArrayList<>()).add(updateTask);
            }

            if (config.timeout() != null) {
                updateTasksExecutor.execute(updateTask, threadPool.scheduler(), config.timeout(), () -> threadPool.generic().execute(() -> {
                    if (updateTask.processed.getAndSet(true) == false) {
                        listener.onFailure(source, new ProcessClusterEventTimeoutException(config.timeout(), source));
                    }}));
            } else {
                updateTasksExecutor.execute(updateTask);
            }
        } catch (EsRejectedExecutionException e) {
            // ignore cases where we are shutting down..., there is really nothing interesting
            // to be done here...
            if (!lifecycle.stoppedOrClosed()) {
                throw e;
            }
        }
    }

    @Override
    public List<PendingClusterTask> pendingTasks() {
        PrioritizedEsThreadPoolExecutor.Pending[] pendings = updateTasksExecutor.getPending();
        List<PendingClusterTask> pendingClusterTasks = new ArrayList<>(pendings.length);
        for (PrioritizedEsThreadPoolExecutor.Pending pending : pendings) {
            final String source;
            final long timeInQueue;
            // we have to capture the task as it will be nulled after execution and we don't want to change while we check things here.
            final Object task = pending.task;
            if (task == null) {
                continue;
            } else if (task instanceof SourcePrioritizedRunnable) {
                SourcePrioritizedRunnable runnable = (SourcePrioritizedRunnable) task;
                source = runnable.source();
                timeInQueue = runnable.getAgeInMillis();
            } else {
                assert false : "expected SourcePrioritizedRunnable got " + task.getClass();
                source = "unknown [" + task.getClass() + "]";
                timeInQueue = 0;
            }

            pendingClusterTasks.add(new PendingClusterTask(pending.insertionOrder, pending.priority, new StringText(source), timeInQueue, pending.executing));
        }
        return pendingClusterTasks;
    }

    @Override
    public int numberOfPendingTasks() {
        return updateTasksExecutor.getNumberOfPendingTasks();
    }

    @Override
    public TimeValue getMaxTaskWaitTime() {
        return updateTasksExecutor.getMaxTaskWaitTime();
    }


    /** asserts that the current thread is the cluster state update thread */
    public boolean assertClusterStateThread() {
        assert Thread.currentThread().getName().contains(InternalClusterService.UPDATE_THREAD_NAME) : "not called from the cluster state update thread";
        return true;
    }

    static abstract class SourcePrioritizedRunnable extends PrioritizedRunnable {
        protected final String source;

        public SourcePrioritizedRunnable(Priority priority, String source) {
            super(priority);
            this.source = source;
        }

        public String source() {
            return source;
        }
    }

    <T> void runTasksForExecutor(ClusterStateTaskExecutor<T> executor) {
        final ArrayList<UpdateTask<T>> toExecute = new ArrayList<>();
        final ArrayList<String> sources = new ArrayList<>();
        synchronized (updateTasksPerExecutor) {
            List<UpdateTask> pending = updateTasksPerExecutor.remove(executor);
            if (pending != null) {
                for (UpdateTask<T> task : pending) {
                    if (task.processed.getAndSet(true) == false) {
                        logger.trace("will process [{}]", task.source);
                        toExecute.add(task);
                        sources.add(task.source);
                    } else {
                        logger.trace("skipping [{}], already processed", task.source);
                    }
                }
            }
        }
        if (toExecute.isEmpty()) {
            return;
        }
        final String source = Strings.collectionToCommaDelimitedString(sources);
        if (!lifecycle.started()) {
            logger.debug("processing [{}]: ignoring, cluster_service not started", source);
            return;
        }
        logger.debug("processing [{}]: execute", source);
        ClusterState previousClusterState = clusterState;
        if (!previousClusterState.nodes().localNodeMaster() && executor.runOnlyOnMaster()) {
            logger.debug("failing [{}]: local node is no longer master", source);
            toExecute.stream().forEach(task -> task.listener.onNoLongerMaster(task.source));
            return;
        }
        ClusterStateTaskExecutor.BatchResult<T> batchResult;
        long startTimeNS = System.nanoTime();
        try {
            List<T> inputs = toExecute.stream().map(tUpdateTask -> tUpdateTask.task).collect(Collectors.toList());
            batchResult = executor.execute(previousClusterState, inputs);
        } catch (Throwable e) {
            TimeValue executionTime = TimeValue.timeValueMillis(Math.max(0, TimeValue.nsecToMSec(System.nanoTime() - startTimeNS)));
            if (logger.isTraceEnabled()) {
                StringBuilder sb = new StringBuilder("failed to execute cluster state update in ").append(executionTime).append(", state:\nversion [").append(previousClusterState.version()).append("], source [").append(source).append("]\n");
                sb.append(previousClusterState.nodes().prettyPrint());
                sb.append(previousClusterState.routingTable().prettyPrint());
                sb.append(previousClusterState.getRoutingNodes().prettyPrint());
                logger.trace(sb.toString(), e);
            }
            warnAboutSlowTaskIfNeeded(executionTime, source);
            batchResult = ClusterStateTaskExecutor.BatchResult.<T>builder().failures(toExecute.stream().map(updateTask -> updateTask.task)::iterator, e).build(previousClusterState);
        }

        assert batchResult.executionResults != null;

        ClusterState newClusterState = batchResult.resultingState;
        final ArrayList<UpdateTask<T>> proccessedListeners = new ArrayList<>();
        // fail all tasks that have failed and extract those that are waiting for results
        for (UpdateTask<T> updateTask : toExecute) {
            assert batchResult.executionResults.containsKey(updateTask.task) : "missing " + updateTask.task.toString();
            final ClusterStateTaskExecutor.TaskResult executionResult =
                    batchResult.executionResults.get(updateTask.task);
            executionResult.handle(() -> proccessedListeners.add(updateTask), ex -> updateTask.listener.onFailure(updateTask.source, ex));
        }

        if (previousClusterState == newClusterState) {
            for (UpdateTask<T> task : proccessedListeners) {
                if (task.listener instanceof AckedClusterStateTaskListener) {
                    //no need to wait for ack if nothing changed, the update can be counted as acknowledged
                    ((AckedClusterStateTaskListener) task.listener).onAllNodesAcked(null);
                }
                task.listener.clusterStateProcessed(task.source, previousClusterState, newClusterState);
            }
            TimeValue executionTime = TimeValue.timeValueMillis(Math.max(0, TimeValue.nsecToMSec(System.nanoTime() - startTimeNS)));
            logger.debug("processing [{}]: took {} no change in cluster_state", source, executionTime);
            warnAboutSlowTaskIfNeeded(executionTime, source);
            return;
        }

        try {
            ArrayList<Discovery.AckListener> ackListeners = new ArrayList<>();
            if (newClusterState.nodes().localNodeMaster()) {
                // only the master controls the version numbers
                Builder builder = ClusterState.builder(newClusterState).incrementVersion();
                if (previousClusterState.routingTable() != newClusterState.routingTable()) {
                    builder.routingTable(RoutingTable.builder(newClusterState.routingTable()).version(newClusterState.routingTable().version() + 1).build());
                }
                if (previousClusterState.metaData() != newClusterState.metaData()) {
                    builder.metaData(MetaData.builder(newClusterState.metaData()).version(newClusterState.metaData().version() + 1));
                }
                newClusterState = builder.build();
                for (UpdateTask<T> task : proccessedListeners) {
                    if (task.listener instanceof AckedClusterStateTaskListener) {
                        final AckedClusterStateTaskListener ackedListener = (AckedClusterStateTaskListener) task.listener;
                        if (ackedListener.ackTimeout() == null || ackedListener.ackTimeout().millis() == 0) {
                            ackedListener.onAckTimeout();
                        } else {
                            try {
                                ackListeners.add(new AckCountDownListener(ackedListener, newClusterState.version(), newClusterState.nodes(), threadPool));
                            } catch (EsRejectedExecutionException ex) {
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Couldn't schedule timeout thread - node might be shutting down", ex);
                                }
                                //timeout straightaway, otherwise we could wait forever as the timeout thread has not started
                                ackedListener.onAckTimeout();
                            }
                        }
                    }
                }
            }
            final Discovery.AckListener ackListener = new DelegetingAckListener(ackListeners);

            newClusterState.status(ClusterState.ClusterStateStatus.BEING_APPLIED);

            if (logger.isTraceEnabled()) {
                StringBuilder sb = new StringBuilder("cluster state updated, source [").append(source).append("]\n");
                sb.append(newClusterState.prettyPrint());
                logger.trace(sb.toString());
            } else if (logger.isDebugEnabled()) {
                logger.debug("cluster state updated, version [{}], source [{}]", newClusterState.version(), source);
            }

            ClusterChangedEvent clusterChangedEvent = new ClusterChangedEvent(source, newClusterState, previousClusterState);
            // new cluster state, notify all listeners
            final DiscoveryNodes.Delta nodesDelta = clusterChangedEvent.nodesDelta();
            if (nodesDelta.hasChanges() && logger.isInfoEnabled()) {
                String summary = nodesDelta.shortSummary();
                if (summary.length() > 0) {
                    logger.info("{}, reason: {}", summary, source);
                }
            }

            // TODO, do this in parallel (and wait)
            for (DiscoveryNode node : nodesDelta.addedNodes()) {
                if (!nodeRequiresConnection(node)) {
                    continue;
                }
                try {
                    transportService.connectToNode(node);
                } catch (Throwable e) {
                    // the fault detection will detect it as failed as well
                    logger.warn("failed to connect to node [" + node + "]", e);
                }
            }

            // if we are the master, publish the new state to all nodes
            // we publish here before we send a notification to all the listeners, since if it fails
            // we don't want to notify
            if (newClusterState.nodes().localNodeMaster()) {
                logger.debug("publishing cluster state version [{}]", newClusterState.version());
                try {
                    discoveryService.publish(clusterChangedEvent, ackListener);
                } catch (Discovery.FailedToCommitClusterStateException t) {
                    logger.warn("failing [{}]: failed to commit cluster state version [{}]", t, source, newClusterState.version());
                    proccessedListeners.forEach(task -> task.listener.onFailure(task.source, t));
                    return;
                }
            }

            // update the current cluster state
            clusterState = newClusterState;
            logger.debug("set local cluster state to version {}", newClusterState.version());
            for (ClusterStateListener listener : preAppliedListeners) {
                try {
                    listener.clusterChanged(clusterChangedEvent);
                } catch (Exception ex) {
                    logger.warn("failed to notify ClusterStateListener", ex);
                }
            }

            for (DiscoveryNode node : nodesDelta.removedNodes()) {
                try {
                    transportService.disconnectFromNode(node);
                } catch (Throwable e) {
                    logger.warn("failed to disconnect to node [" + node + "]", e);
                }
            }

            newClusterState.status(ClusterState.ClusterStateStatus.APPLIED);

            for (ClusterStateListener listener : postAppliedListeners) {
                try {
                    listener.clusterChanged(clusterChangedEvent);
                } catch (Exception ex) {
                    logger.warn("failed to notify ClusterStateListener", ex);
                }
            }

            //manual ack only from the master at the end of the publish
            if (newClusterState.nodes().localNodeMaster()) {
                try {
                    ackListener.onNodeAck(newClusterState.nodes().localNode(), null);
                } catch (Throwable t) {
                    logger.debug("error while processing ack for master node [{}]", t, newClusterState.nodes().localNode());
                }
            }

            for (UpdateTask<T> task : proccessedListeners) {
                task.listener.clusterStateProcessed(task.source, previousClusterState, newClusterState);
            }

            TimeValue executionTime = TimeValue.timeValueMillis(Math.max(0, TimeValue.nsecToMSec(System.nanoTime() - startTimeNS)));
            logger.debug("processing [{}]: took {} done applying updated cluster_state (version: {}, uuid: {})", source, executionTime, newClusterState.version(), newClusterState.stateUUID());
            warnAboutSlowTaskIfNeeded(executionTime, source);
        } catch (Throwable t) {
            TimeValue executionTime = TimeValue.timeValueMillis(Math.max(0, TimeValue.nsecToMSec(System.nanoTime() - startTimeNS)));
            StringBuilder sb = new StringBuilder("failed to apply updated cluster state in ").append(executionTime).append(":\nversion [").append(newClusterState.version()).append("], uuid [").append(newClusterState.stateUUID()).append("], source [").append(source).append("]\n");
            sb.append(newClusterState.nodes().prettyPrint());
            sb.append(newClusterState.routingTable().prettyPrint());
            sb.append(newClusterState.getRoutingNodes().prettyPrint());
            logger.warn(sb.toString(), t);
            // TODO: do we want to call updateTask.onFailure here?
        }

    }

    class UpdateTask<T> extends SourcePrioritizedRunnable {

        public final T task;
        public final ClusterStateTaskConfig config;
        public final ClusterStateTaskExecutor<T> executor;
        public final ClusterStateTaskListener listener;
        public final AtomicBoolean processed = new AtomicBoolean();

        UpdateTask(String source, T task, ClusterStateTaskConfig config, ClusterStateTaskExecutor<T> executor, ClusterStateTaskListener listener) {
            super(config.priority(), source);
            this.task = task;
            this.config = config;
            this.executor = executor;
            this.listener = listener;
        }

        @Override
        public void run() {
            runTasksForExecutor(executor);
        }
    }

    private void warnAboutSlowTaskIfNeeded(TimeValue executionTime, String source) {
        if (executionTime.getMillis() > slowTaskLoggingThreshold.getMillis()) {
            logger.warn("cluster state update task [{}] took {} above the warn threshold of {}", source, executionTime, slowTaskLoggingThreshold);
        }
    }

    class NotifyTimeout implements Runnable {
        final TimeoutClusterStateListener listener;
        final TimeValue timeout;
        volatile ScheduledFuture future;

        NotifyTimeout(TimeoutClusterStateListener listener, TimeValue timeout) {
            this.listener = listener;
            this.timeout = timeout;
        }

        public void cancel() {
            FutureUtils.cancel(future);
        }

        @Override
        public void run() {
            if (future != null && future.isCancelled()) {
                return;
            }
            if (lifecycle.stoppedOrClosed()) {
                listener.onClose();
            } else {
                listener.onTimeout(this.timeout);
            }
            // note, we rely on the listener to remove itself in case of timeout if needed
        }
    }

    private class ReconnectToNodes implements Runnable {

        private ConcurrentMap<DiscoveryNode, Integer> failureCount = ConcurrentCollections.newConcurrentMap();

        @Override
        public void run() {
            // master node will check against all nodes if its alive with certain discoveries implementations,
            // but we can't rely on that, so we check on it as well
            for (DiscoveryNode node : clusterState.nodes()) {
                if (lifecycle.stoppedOrClosed()) {
                    return;
                }
                if (!nodeRequiresConnection(node)) {
                    continue;
                }
                if (clusterState.nodes().nodeExists(node.id())) { // we double check existence of node since connectToNode might take time...
                    if (!transportService.nodeConnected(node)) {
                        try {
                            transportService.connectToNode(node);
                        } catch (Exception e) {
                            if (lifecycle.stoppedOrClosed()) {
                                return;
                            }
                            if (clusterState.nodes().nodeExists(node.id())) { // double check here as well, maybe its gone?
                                Integer nodeFailureCount = failureCount.get(node);
                                if (nodeFailureCount == null) {
                                    nodeFailureCount = 1;
                                } else {
                                    nodeFailureCount = nodeFailureCount + 1;
                                }
                                // log every 6th failure
                                if ((nodeFailureCount % 6) == 0) {
                                    // reset the failure count...
                                    nodeFailureCount = 0;
                                    logger.warn("failed to reconnect to node {}", e, node);
                                }
                                failureCount.put(node, nodeFailureCount);
                            }
                        }
                    }
                }
            }
            // go over and remove failed nodes that have been removed
            DiscoveryNodes nodes = clusterState.nodes();
            for (Iterator<DiscoveryNode> failedNodesIt = failureCount.keySet().iterator(); failedNodesIt.hasNext(); ) {
                DiscoveryNode failedNode = failedNodesIt.next();
                if (!nodes.nodeExists(failedNode.id())) {
                    failedNodesIt.remove();
                }
            }
            if (lifecycle.started()) {
                reconnectToNodes = threadPool.schedule(reconnectInterval, ThreadPool.Names.GENERIC, this);
            }
        }
    }

    private boolean nodeRequiresConnection(DiscoveryNode node) {
        return localNode().shouldConnectTo(node);
    }

    private static class LocalNodeMasterListeners implements ClusterStateListener {

        private final List<LocalNodeMasterListener> listeners = new CopyOnWriteArrayList<>();
        private final ThreadPool threadPool;
        private volatile boolean master = false;

        private LocalNodeMasterListeners(ThreadPool threadPool) {
            this.threadPool = threadPool;
        }

        @Override
        public void clusterChanged(ClusterChangedEvent event) {
            if (!master && event.localNodeMaster()) {
                master = true;
                for (LocalNodeMasterListener listener : listeners) {
                    Executor executor = threadPool.executor(listener.executorName());
                    executor.execute(new OnMasterRunnable(listener));
                }
                return;
            }

            if (master && !event.localNodeMaster()) {
                master = false;
                for (LocalNodeMasterListener listener : listeners) {
                    Executor executor = threadPool.executor(listener.executorName());
                    executor.execute(new OffMasterRunnable(listener));
                }
            }
        }

        private void add(LocalNodeMasterListener listener) {
            listeners.add(listener);
        }

        private void remove(LocalNodeMasterListener listener) {
            listeners.remove(listener);
        }

        private void clear() {
            listeners.clear();
        }
    }

    private static class OnMasterRunnable implements Runnable {

        private final LocalNodeMasterListener listener;

        private OnMasterRunnable(LocalNodeMasterListener listener) {
            this.listener = listener;
        }

        @Override
        public void run() {
            listener.onMaster();
        }
    }

    private static class OffMasterRunnable implements Runnable {

        private final LocalNodeMasterListener listener;

        private OffMasterRunnable(LocalNodeMasterListener listener) {
            this.listener = listener;
        }

        @Override
        public void run() {
            listener.offMaster();
        }
    }

    private static class DelegetingAckListener implements Discovery.AckListener {

        final private List<Discovery.AckListener> listeners;

        private DelegetingAckListener(List<Discovery.AckListener> listeners) {
            this.listeners = listeners;
        }

        @Override
        public void onNodeAck(DiscoveryNode node, @Nullable Throwable t) {
            for (Discovery.AckListener listener : listeners) {
                listener.onNodeAck(node, t);
            }
        }

        @Override
        public void onTimeout() {
            throw new UnsupportedOperationException("no timeout delegation");
        }
    }

    private static class AckCountDownListener implements Discovery.AckListener {

        private static final ESLogger logger = Loggers.getLogger(AckCountDownListener.class);

        private final AckedClusterStateTaskListener ackedTaskListener;
        private final CountDown countDown;
        private final DiscoveryNodes nodes;
        private final long clusterStateVersion;
        private final Future<?> ackTimeoutCallback;
        private Throwable lastFailure;

        AckCountDownListener(AckedClusterStateTaskListener ackedTaskListener, long clusterStateVersion, DiscoveryNodes nodes, ThreadPool threadPool) {
            this.ackedTaskListener = ackedTaskListener;
            this.clusterStateVersion = clusterStateVersion;
            this.nodes = nodes;
            int countDown = 0;
            for (DiscoveryNode node : nodes) {
                if (ackedTaskListener.mustAck(node)) {
                    countDown++;
                }
            }
            //we always wait for at least 1 node (the master)
            countDown = Math.max(1, countDown);
            logger.trace("expecting {} acknowledgements for cluster_state update (version: {})", countDown, clusterStateVersion);
            this.countDown = new CountDown(countDown);
            this.ackTimeoutCallback = threadPool.schedule(ackedTaskListener.ackTimeout(), ThreadPool.Names.GENERIC, new Runnable() {
                @Override
                public void run() {
                    onTimeout();
                }
            });
        }

        @Override
        public void onNodeAck(DiscoveryNode node, @Nullable Throwable t) {
            if (!ackedTaskListener.mustAck(node)) {
                //we always wait for the master ack anyway
                if (!node.equals(nodes.masterNode())) {
                    return;
                }
            }
            if (t == null) {
                logger.trace("ack received from node [{}], cluster_state update (version: {})", node, clusterStateVersion);
            } else {
                this.lastFailure = t;
                logger.debug("ack received from node [{}], cluster_state update (version: {})", t, node, clusterStateVersion);
            }

            if (countDown.countDown()) {
                logger.trace("all expected nodes acknowledged cluster_state update (version: {})", clusterStateVersion);
                FutureUtils.cancel(ackTimeoutCallback);
                ackedTaskListener.onAllNodesAcked(lastFailure);
            }
        }

        @Override
        public void onTimeout() {
            if (countDown.fastForward()) {
                logger.trace("timeout waiting for acknowledgement for cluster_state update (version: {})", clusterStateVersion);
                ackedTaskListener.onAckTimeout();
            }
        }
    }

    class ApplySettings implements NodeSettingsService.Listener {
        @Override
        public void onRefreshSettings(Settings settings) {
            final TimeValue slowTaskLoggingThreshold = settings.getAsTime(SETTING_CLUSTER_SERVICE_SLOW_TASK_LOGGING_THRESHOLD, InternalClusterService.this.slowTaskLoggingThreshold);
            InternalClusterService.this.slowTaskLoggingThreshold = slowTaskLoggingThreshold;
        }
    }
}
