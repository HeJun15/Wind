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

package org.elasticsearch.threadpool;

import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;
import org.elasticsearch.test.ESIntegTestCase.Scope;
import org.elasticsearch.test.ESSingleNodeTestCase;
import org.elasticsearch.test.InternalTestCluster;
import org.elasticsearch.test.hamcrest.RegexMatcher;
import org.elasticsearch.threadpool.ThreadPool.Names;
import org.elasticsearch.tribe.TribeIT;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.elasticsearch.common.settings.Settings.settingsBuilder;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertNoFailures;
import static org.hamcrest.Matchers.*;

/**
 */
@ClusterScope(scope = Scope.TEST, numDataNodes = 0, numClientNodes = 0)
public class SimpleThreadPoolIT extends ESIntegTestCase {
    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return Settings.settingsBuilder().build();
    }

    public void testThreadNames() throws Exception {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        Set<String> preNodeStartThreadNames = new HashSet<>();
        for (long l : threadBean.getAllThreadIds()) {
            ThreadInfo threadInfo = threadBean.getThreadInfo(l);
            if (threadInfo != null) {
                preNodeStartThreadNames.add(threadInfo.getThreadName());
            }
        }
        logger.info("pre node threads are {}", preNodeStartThreadNames);
        String node = internalCluster().startNode();
        logger.info("do some indexing, flushing, optimize, and searches");
        int numDocs = randomIntBetween(2, 100);
        IndexRequestBuilder[] builders = new IndexRequestBuilder[numDocs];
        for (int i = 0; i < numDocs; ++i) {
            builders[i] = client().prepareIndex("idx", "type").setSource(jsonBuilder()
                    .startObject()
                    .field("str_value", "s" + i)
                    .field("str_values", new String[]{"s" + (i * 2), "s" + (i * 2 + 1)})
                    .field("l_value", i)
                    .field("l_values", new int[]{i * 2, i * 2 + 1})
                    .field("d_value", i)
                    .field("d_values", new double[]{i * 2, i * 2 + 1})
                    .endObject());
        }
        indexRandom(true, builders);
        int numSearches = randomIntBetween(2, 100);
        for (int i = 0; i < numSearches; i++) {
            assertNoFailures(client().prepareSearch("idx").setQuery(QueryBuilders.termQuery("str_value", "s" + i)).get());
            assertNoFailures(client().prepareSearch("idx").setQuery(QueryBuilders.termQuery("l_value", i)).get());
        }
        Set<String> threadNames = new HashSet<>();
        for (long l : threadBean.getAllThreadIds()) {
            ThreadInfo threadInfo = threadBean.getThreadInfo(l);
            if (threadInfo != null) {
                threadNames.add(threadInfo.getThreadName());
            }
        }
        logger.info("post node threads are {}", threadNames);
        threadNames.removeAll(preNodeStartThreadNames);
        logger.info("post node *new* threads are {}", threadNames);
        for (String threadName : threadNames) {
            // ignore some shared threads we know that are created within the same VM, like the shared discovery one
            // or the ones that are occasionally come up from ESSingleNodeTestCase
            if (threadName.contains("[" + ESSingleNodeTestCase.nodeName() + "]")
                    || threadName.contains("Keep-Alive-Timer")) {
                continue;
            }
            String nodePrefix = "(" + Pattern.quote(InternalTestCluster.TRANSPORT_CLIENT_PREFIX) + ")?(" +
                    Pattern.quote(ESIntegTestCase.SUITE_CLUSTER_NODE_PREFIX) + "|" +
                    Pattern.quote(ESIntegTestCase.TEST_CLUSTER_NODE_PREFIX) + "|" +
                    Pattern.quote(TribeIT.SECOND_CLUSTER_NODE_PREFIX) + ")";
            assertThat(threadName, RegexMatcher.matches("\\[" + nodePrefix + "\\d+\\]"));
        }
    }

    public void testUpdatingThreadPoolSettings() throws Exception {
        internalCluster().startNodesAsync(2).get();
        ThreadPool threadPool = internalCluster().getDataNodeInstance(ThreadPool.class);
        // Check that settings are changed
        assertThat(((ThreadPoolExecutor) threadPool.executor(Names.SEARCH)).getQueue().remainingCapacity(), equalTo(1000));
        client().admin().cluster().prepareUpdateSettings().setTransientSettings(settingsBuilder().put("threadpool.search.queue_size", 2000).build()).execute().actionGet();
        assertThat(((ThreadPoolExecutor) threadPool.executor(Names.SEARCH)).getQueue().remainingCapacity(), equalTo(2000));

        // Make sure that threads continue executing when executor is replaced
        final CyclicBarrier barrier = new CyclicBarrier(2);
        Executor oldExecutor = threadPool.executor(Names.SEARCH);
        threadPool.executor(Names.SEARCH).execute(() -> {
                    try {
                        barrier.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (BrokenBarrierException ex) {
                        //
                    }
                });
        client().admin().cluster().prepareUpdateSettings().setTransientSettings(settingsBuilder().put("threadpool.search.queue_size", 1000).build()).execute().actionGet();
        assertThat(threadPool.executor(Names.SEARCH), not(sameInstance(oldExecutor)));
        assertThat(((ThreadPoolExecutor) oldExecutor).isShutdown(), equalTo(true));
        assertThat(((ThreadPoolExecutor) oldExecutor).isTerminating(), equalTo(true));
        assertThat(((ThreadPoolExecutor) oldExecutor).isTerminated(), equalTo(false));
        barrier.await(10, TimeUnit.SECONDS);

        // Make sure that new thread executor is functional
        threadPool.executor(Names.SEARCH).execute(() -> {
                    try {
                        barrier.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (BrokenBarrierException ex) {
                        //
                    }
                }
        );
        client().admin().cluster().prepareUpdateSettings().setTransientSettings(settingsBuilder().put("threadpool.search.queue_size", 500)).execute().actionGet();
        barrier.await(10, TimeUnit.SECONDS);

        // Check that node info is correct
        NodesInfoResponse nodesInfoResponse = client().admin().cluster().prepareNodesInfo().all().execute().actionGet();
        for (int i = 0; i < 2; i++) {
            NodeInfo nodeInfo = nodesInfoResponse.getNodes()[i];
            boolean found = false;
            for (ThreadPool.Info info : nodeInfo.getThreadPool()) {
                if (info.getName().equals(Names.SEARCH)) {
                    assertEquals(info.getThreadPoolType(), ThreadPool.ThreadPoolType.FIXED);
                    found = true;
                    break;
                }
            }
            assertThat(found, equalTo(true));
        }
    }

    public void testThreadPoolLeakingThreadsWithTribeNode() {
        Settings settings = Settings.builder()
                .put("node.name", "thread_pool_leaking_threads_tribe_node")
                .put("path.home", createTempDir())
                .put("tribe.t1.cluster.name", "non_existing_cluster")
                        //trigger initialization failure of one of the tribes (doesn't require starting the node)
                .put("tribe.t1.plugin.mandatory", "non_existing").build();

        try {
            NodeBuilder.nodeBuilder().settings(settings).build();
            fail("The node startup is supposed to fail");
        } catch(Throwable t) {
            //all good
            assertThat(t.getMessage(), containsString("mandatory plugins [non_existing]"));
        }
    }

    private Map<String, Object> getPoolSettingsThroughJson(ThreadPoolInfo info, String poolName) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        info.toXContent(builder, ToXContent.EMPTY_PARAMS);
        builder.endObject();
        builder.close();
        Map<String, Object> poolsMap;
        try (XContentParser parser = JsonXContent.jsonXContent.createParser(builder.string())) {
            poolsMap = parser.map();
        }
        return (Map<String, Object>) ((Map<String, Object>) poolsMap.get("thread_pool")).get(poolName);
    }

}
