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
package org.elasticsearch.indices;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.ShardRoutingHelper;
import org.elasticsearch.cluster.routing.UnassignedInfo;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.DummyTransportAddress;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.NodeServicesProvider;
import org.elasticsearch.index.shard.IndexEventListener;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.indices.recovery.RecoveryState;
import org.elasticsearch.test.ESSingleNodeTestCase;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_REPLICAS;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_SHARDS;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

public class IndicesLifecycleListenerSingleNodeTests extends ESSingleNodeTestCase {

    public void testCloseDeleteCallback() throws Throwable {
        IndicesService indicesService = getInstanceFromNode(IndicesService.class);
        assertAcked(client().admin().indices().prepareCreate("test")
                .setSettings(SETTING_NUMBER_OF_SHARDS, 1, SETTING_NUMBER_OF_REPLICAS, 0));
        ensureGreen();
        IndexMetaData metaData = indicesService.indexService("test").getMetaData();
        ShardRouting shardRouting = indicesService.indexService("test").getShard(0).routingEntry();
        final AtomicInteger counter = new AtomicInteger(1);
        IndexEventListener countingListener = new IndexEventListener() {
            @Override
            public void afterIndexClosed(Index index, Settings indexSettings) {
                assertEquals(counter.get(), 5);
                counter.incrementAndGet();
            }

            @Override
            public void beforeIndexClosed(IndexService indexService) {
                assertEquals(counter.get(), 1);
                counter.incrementAndGet();
            }

            @Override
            public void afterIndexDeleted(Index index, Settings indexSettings) {
                assertEquals(counter.get(), 6);
                counter.incrementAndGet();
            }

            @Override
            public void beforeIndexDeleted(IndexService indexService) {
                assertEquals(counter.get(), 2);
                counter.incrementAndGet();
            }

            @Override
            public void beforeIndexShardDeleted(ShardId shardId, Settings indexSettings) {
                assertEquals(counter.get(), 3);
                counter.incrementAndGet();
            }

            @Override
            public void afterIndexShardDeleted(ShardId shardId, Settings indexSettings) {
                assertEquals(counter.get(), 4);
                counter.incrementAndGet();
            }
        };
        indicesService.deleteIndex("test", "simon says");
        try {
            NodeServicesProvider nodeServicesProvider = getInstanceFromNode(NodeServicesProvider.class);
            IndexService index = indicesService.createIndex(nodeServicesProvider, metaData, Arrays.asList(countingListener));
            ShardRouting newRouting = new ShardRouting(shardRouting);
            String nodeId = newRouting.currentNodeId();
            ShardRoutingHelper.moveToUnassigned(newRouting, new UnassignedInfo(UnassignedInfo.Reason.INDEX_CREATED, "boom"));
            ShardRoutingHelper.initialize(newRouting, nodeId);
            IndexShard shard = index.createShard(newRouting);
            shard.updateRoutingEntry(newRouting, true);
            final DiscoveryNode localNode = new DiscoveryNode("foo", DummyTransportAddress.INSTANCE, Version.CURRENT);
            shard.markAsRecovering("store", new RecoveryState(shard.shardId(), newRouting.primary(), RecoveryState.Type.SNAPSHOT, newRouting.restoreSource(), localNode));
            shard.recoverFromStore(localNode);
            newRouting = new ShardRouting(newRouting);
            ShardRoutingHelper.moveToStarted(newRouting);
            shard.updateRoutingEntry(newRouting, true);
        } finally {
            indicesService.deleteIndex("test", "simon says");
        }
        assertEquals(7, counter.get());
    }

}
