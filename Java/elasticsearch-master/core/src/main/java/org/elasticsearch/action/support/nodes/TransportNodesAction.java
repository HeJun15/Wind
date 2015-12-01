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

package org.elasticsearch.action.support.nodes;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.NoSuchNodeException;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;

/**
 *
 */
public abstract class TransportNodesAction<NodesRequest extends BaseNodesRequest, NodesResponse extends BaseNodesResponse, NodeRequest extends BaseNodeRequest, NodeResponse extends BaseNodeResponse> extends HandledTransportAction<NodesRequest, NodesResponse> {

    protected final ClusterName clusterName;
    protected final ClusterService clusterService;
    protected final TransportService transportService;

    final String transportNodeAction;

    protected TransportNodesAction(Settings settings, String actionName, ClusterName clusterName, ThreadPool threadPool,
                                   ClusterService clusterService, TransportService transportService, ActionFilters actionFilters,
                                   IndexNameExpressionResolver indexNameExpressionResolver, Supplier<NodesRequest> request, Supplier<NodeRequest> nodeRequest,
                                   String nodeExecutor) {
        super(settings, actionName, threadPool, transportService, actionFilters, indexNameExpressionResolver, request);
        this.clusterName = clusterName;
        this.clusterService = clusterService;
        this.transportService = transportService;

        this.transportNodeAction = actionName + "[n]";

        transportService.registerRequestHandler(transportNodeAction, nodeRequest, nodeExecutor, new NodeTransportHandler());
    }

    @Override
    protected void doExecute(NodesRequest request, ActionListener<NodesResponse> listener) {
        new AsyncAction(request, listener).start();
    }

    protected boolean transportCompress() {
        return false;
    }

    protected abstract NodesResponse newResponse(NodesRequest request, AtomicReferenceArray nodesResponses);

    protected abstract NodeRequest newNodeRequest(String nodeId, NodesRequest request);

    protected abstract NodeResponse newNodeResponse();

    protected abstract NodeResponse nodeOperation(NodeRequest request);

    protected abstract boolean accumulateExceptions();

    protected String[] filterNodeIds(DiscoveryNodes nodes, String[] nodesIds) {
        return nodesIds;
    }

    protected String[] resolveNodes(NodesRequest request, ClusterState clusterState) {
        return clusterState.nodes().resolveNodesIds(request.nodesIds());
    }


    private class AsyncAction {

        private final NodesRequest request;
        private final String[] nodesIds;
        private final DiscoveryNode[] nodes;
        private final ActionListener<NodesResponse> listener;
        private final AtomicReferenceArray<Object> responses;
        private final AtomicInteger counter = new AtomicInteger();

        private AsyncAction(NodesRequest request, ActionListener<NodesResponse> listener) {
            this.request = request;
            this.listener = listener;
            ClusterState clusterState = clusterService.state();
            String[] nodesIds = resolveNodes(request, clusterState);
            this.nodesIds = filterNodeIds(clusterState.nodes(), nodesIds);
            ImmutableOpenMap<String, DiscoveryNode> nodes = clusterState.nodes().nodes();
            this.nodes = new DiscoveryNode[nodesIds.length];
            for (int i = 0; i < nodesIds.length; i++) {
                this.nodes[i] = nodes.get(nodesIds[i]);
            }
            this.responses = new AtomicReferenceArray<>(this.nodesIds.length);
        }

        private void start() {
            if (nodesIds.length == 0) {
                // nothing to notify
                threadPool.generic().execute(new Runnable() {
                    @Override
                    public void run() {
                        listener.onResponse(newResponse(request, responses));
                    }
                });
                return;
            }
            TransportRequestOptions.Builder builder = TransportRequestOptions.builder();
            if (request.timeout() != null) {
                builder.withTimeout(request.timeout());
            }
            builder.withCompress(transportCompress());
            for (int i = 0; i < nodesIds.length; i++) {
                final String nodeId = nodesIds[i];
                final int idx = i;
                final DiscoveryNode node = nodes[i];
                try {
                    if (node == null) {
                        onFailure(idx, nodeId, new NoSuchNodeException(nodeId));
                    } else if (!clusterService.localNode().shouldConnectTo(node) && !clusterService.localNode().equals(node)) {
                        // the check "!clusterService.localNode().equals(node)" is to maintain backward comp. where before
                        // we allowed to connect from "local" client node to itself, certain tests rely on it, if we remove it, we need to fix
                        // those (and they randomize the client node usage, so tricky to find when)
                        onFailure(idx, nodeId, new NodeShouldNotConnectException(clusterService.localNode(), node));
                    } else {
                        NodeRequest nodeRequest = newNodeRequest(nodeId, request);
                        transportService.sendRequest(node, transportNodeAction, nodeRequest, builder.build(), new BaseTransportResponseHandler<NodeResponse>() {
                            @Override
                            public NodeResponse newInstance() {
                                return newNodeResponse();
                            }

                            @Override
                            public void handleResponse(NodeResponse response) {
                                onOperation(idx, response);
                            }

                            @Override
                            public void handleException(TransportException exp) {
                                onFailure(idx, node.id(), exp);
                            }

                            @Override
                            public String executor() {
                                return ThreadPool.Names.SAME;
                            }
                        });
                    }
                } catch (Throwable t) {
                    onFailure(idx, nodeId, t);
                }
            }
        }

        private void onOperation(int idx, NodeResponse nodeResponse) {
            responses.set(idx, nodeResponse);
            if (counter.incrementAndGet() == responses.length()) {
                finishHim();
            }
        }

        private void onFailure(int idx, String nodeId, Throwable t) {
            if (logger.isDebugEnabled() && !(t instanceof NodeShouldNotConnectException)) {
                logger.debug("failed to execute on node [{}]", t, nodeId);
            }
            if (accumulateExceptions()) {
                responses.set(idx, new FailedNodeException(nodeId, "Failed node [" + nodeId + "]", t));
            }
            if (counter.incrementAndGet() == responses.length()) {
                finishHim();
            }
        }

        private void finishHim() {
            NodesResponse finalResponse;
            try {
                finalResponse = newResponse(request, responses);
            } catch (Throwable t) {
                logger.debug("failed to combine responses from nodes", t);
                listener.onFailure(t);
                return;
            }
            listener.onResponse(finalResponse);
        }
    }

    class NodeTransportHandler implements TransportRequestHandler<NodeRequest> {

        @Override
        public void messageReceived(final NodeRequest request, final TransportChannel channel) throws Exception {
            channel.sendResponse(nodeOperation(request));
        }
    }
}
