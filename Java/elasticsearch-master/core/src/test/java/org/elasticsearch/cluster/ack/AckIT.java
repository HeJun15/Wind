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

package org.elasticsearch.cluster.ack;

import com.carrotsearch.hppc.cursors.ObjectObjectCursor;

import org.elasticsearch.action.admin.cluster.reroute.ClusterRerouteResponse;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse;
import org.elasticsearch.action.admin.indices.close.CloseIndexResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsResponse;
import org.elasticsearch.action.admin.indices.warmer.delete.DeleteWarmerResponse;
import org.elasticsearch.action.admin.indices.warmer.get.GetWarmersResponse;
import org.elasticsearch.action.admin.indices.warmer.put.PutWarmerResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.cluster.metadata.AliasOrIndex;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.IndexMetaData.State;
import org.elasticsearch.cluster.routing.RoutingNode;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.ShardRoutingState;
import org.elasticsearch.cluster.routing.allocation.command.MoveAllocationCommand;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.discovery.DiscoverySettings;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.warmer.IndexWarmersMetaData;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_REPLICAS;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_SHARDS;
import static org.elasticsearch.common.settings.Settings.settingsBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@ClusterScope(minNumDataNodes = 2)
public class AckIT extends ESIntegTestCase {

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        //to test that the acknowledgement mechanism is working we better disable the wait for publish
        //otherwise the operation is most likely acknowledged even if it doesn't support ack
        return Settings.builder().put(super.nodeSettings(nodeOrdinal))
                .put(DiscoverySettings.PUBLISH_TIMEOUT, 0).build();
    }

    public void testUpdateSettingsAcknowledgement() {
        createIndex("test");

        assertAcked(client().admin().indices().prepareUpdateSettings("test")
                .setSettings(Settings.builder().put("refresh_interval", 9999, TimeUnit.MILLISECONDS)));

        for (Client client : clients()) {
            String refreshInterval = getLocalClusterState(client).metaData().index("test").getSettings().get("index.refresh_interval");
            assertThat(refreshInterval, equalTo("9999ms"));
        }
    }

    public void testUpdateSettingsNoAcknowledgement() {
        createIndex("test");
        UpdateSettingsResponse updateSettingsResponse = client().admin().indices().prepareUpdateSettings("test").setTimeout("0s")
                .setSettings(Settings.builder().put("refresh_interval", 9999, TimeUnit.MILLISECONDS)).get();
        assertThat(updateSettingsResponse.isAcknowledged(), equalTo(false));
    }

    public void testPutWarmerAcknowledgement() {
        createIndex("test");
        // make sure one shard is started so the search during put warmer will not fail
        index("test", "type", "1", "f", 1);

        assertAcked(client().admin().indices().preparePutWarmer("custom_warmer")
                .setSearchRequest(client().prepareSearch("test").setTypes("test").setQuery(QueryBuilders.matchAllQuery())));

        for (Client client : clients()) {
            GetWarmersResponse getWarmersResponse = client.admin().indices().prepareGetWarmers().setLocal(true).get();
            assertThat(getWarmersResponse.warmers().size(), equalTo(1));
            ObjectObjectCursor<String, List<IndexWarmersMetaData.Entry>> entry = getWarmersResponse.warmers().iterator().next();
            assertThat(entry.key, equalTo("test"));
            assertThat(entry.value.size(), equalTo(1));
            assertThat(entry.value.get(0).name(), equalTo("custom_warmer"));
        }
    }

    public void testPutWarmerNoAcknowledgement() throws InterruptedException {
        createIndex("test");
        // make sure one shard is started so the search during put warmer will not fail
        index("test", "type", "1", "f", 1);

        PutWarmerResponse putWarmerResponse = client().admin().indices().preparePutWarmer("custom_warmer").setTimeout("0s")
                .setSearchRequest(client().prepareSearch("test").setTypes("test").setQuery(QueryBuilders.matchAllQuery()))
                .get();
        assertThat(putWarmerResponse.isAcknowledged(), equalTo(false));
        /* Since we don't wait for the ack here we have to wait until the search request has been executed from the master
         * otherwise the test infra might have already deleted the index and the search request fails on all shards causing
         * the test to fail too. We simply wait until the the warmer has been installed and also clean it up afterwards.*/
        assertTrue(awaitBusy(() -> {
            for (Client client : clients()) {
                GetWarmersResponse getWarmersResponse = client.admin().indices().prepareGetWarmers().setLocal(true).get();
                if (getWarmersResponse.warmers().size() != 1) {
                    return false;
                }
            }
            return true;
        }));
        assertAcked(client().admin().indices().prepareDeleteWarmer().setIndices("test").setNames("custom_warmer"));
    }

    public void testDeleteWarmerAcknowledgement() {
        createIndex("test");
        index("test", "type", "1", "f", 1);

        assertAcked(client().admin().indices().preparePutWarmer("custom_warmer")
                .setSearchRequest(client().prepareSearch("test").setTypes("test").setQuery(QueryBuilders.matchAllQuery())));

        assertAcked(client().admin().indices().prepareDeleteWarmer().setIndices("test").setNames("custom_warmer"));

        for (Client client : clients()) {
            GetWarmersResponse getWarmersResponse = client.admin().indices().prepareGetWarmers().setLocal(true).get();
            assertThat(getWarmersResponse.warmers().size(), equalTo(0));
        }
    }

    public void testDeleteWarmerNoAcknowledgement() throws InterruptedException {
        createIndex("test");
        index("test", "type", "1", "f", 1);

        assertAcked(client().admin().indices().preparePutWarmer("custom_warmer")
                .setSearchRequest(client().prepareSearch("test").setTypes("test").setQuery(QueryBuilders.matchAllQuery())));

        DeleteWarmerResponse deleteWarmerResponse = client().admin().indices().prepareDeleteWarmer().setIndices("test").setNames("custom_warmer").setTimeout("0s").get();
        assertFalse(deleteWarmerResponse.isAcknowledged());
        assertTrue(awaitBusy(() -> {
            for (Client client : clients()) {
                GetWarmersResponse getWarmersResponse = client.admin().indices().prepareGetWarmers().setLocal(true).get();
                if (getWarmersResponse.warmers().size() > 0) {
                    return false;
                }
            }
            return true;
        }));
    }

    public void testClusterRerouteAcknowledgement() throws InterruptedException {
        assertAcked(prepareCreate("test").setSettings(Settings.builder()
                        .put(indexSettings())
                        .put(SETTING_NUMBER_OF_SHARDS, between(cluster().numDataNodes(), DEFAULT_MAX_NUM_SHARDS))
                        .put(SETTING_NUMBER_OF_REPLICAS, 0)
        ));
        ensureGreen();

        MoveAllocationCommand moveAllocationCommand = getAllocationCommand();

        assertAcked(client().admin().cluster().prepareReroute().add(moveAllocationCommand));

        for (Client client : clients()) {
            ClusterState clusterState = getLocalClusterState(client);
            for (ShardRouting shardRouting : clusterState.getRoutingNodes().routingNodeIter(moveAllocationCommand.fromNode())) {
                //if the shard that we wanted to move is still on the same node, it must be relocating
                if (shardRouting.shardId().equals(moveAllocationCommand.shardId())) {
                    assertThat(shardRouting.relocating(), equalTo(true));
                }

            }

            boolean found = false;
            for (ShardRouting shardRouting : clusterState.getRoutingNodes().routingNodeIter(moveAllocationCommand.toNode())) {
                if (shardRouting.shardId().equals(moveAllocationCommand.shardId())) {
                    assertThat(shardRouting.state(), anyOf(equalTo(ShardRoutingState.INITIALIZING), equalTo(ShardRoutingState.STARTED)));
                    found = true;
                    break;
                }
            }
            assertThat(found, equalTo(true));
        }
    }

    public void testClusterRerouteNoAcknowledgement() throws InterruptedException {
        client().admin().indices().prepareCreate("test")
                .setSettings(settingsBuilder()
                        .put(SETTING_NUMBER_OF_SHARDS, between(cluster().numDataNodes(), DEFAULT_MAX_NUM_SHARDS))
                        .put(SETTING_NUMBER_OF_REPLICAS, 0)).get();
        ensureGreen();

        MoveAllocationCommand moveAllocationCommand = getAllocationCommand();

        ClusterRerouteResponse clusterRerouteResponse = client().admin().cluster().prepareReroute().setTimeout("0s").add(moveAllocationCommand).get();
        assertThat(clusterRerouteResponse.isAcknowledged(), equalTo(false));
    }

    public void testClusterRerouteAcknowledgementDryRun() throws InterruptedException {
        client().admin().indices().prepareCreate("test")
                .setSettings(settingsBuilder()
                        .put(SETTING_NUMBER_OF_SHARDS, between(cluster().numDataNodes(), DEFAULT_MAX_NUM_SHARDS))
                        .put(SETTING_NUMBER_OF_REPLICAS, 0)).get();
        ensureGreen();

        MoveAllocationCommand moveAllocationCommand = getAllocationCommand();

        assertAcked(client().admin().cluster().prepareReroute().setDryRun(true).add(moveAllocationCommand));

        //testing only on master with the latest cluster state as we didn't make any change thus we cannot guarantee that
        //all nodes hold the same cluster state version. We only know there was no need to change anything, thus no need for ack on this update.
        ClusterStateResponse clusterStateResponse = client().admin().cluster().prepareState().get();
        boolean found = false;
        for (ShardRouting shardRouting : clusterStateResponse.getState().getRoutingNodes().routingNodeIter(moveAllocationCommand.fromNode())) {
            //the shard that we wanted to move is still on the same node, as we had dryRun flag
            if (shardRouting.shardId().equals(moveAllocationCommand.shardId())) {
                assertThat(shardRouting.started(), equalTo(true));
                found = true;
                break;
            }
        }
        assertThat(found, equalTo(true));

        for (ShardRouting shardRouting : clusterStateResponse.getState().getRoutingNodes().routingNodeIter(moveAllocationCommand.toNode())) {
            if (shardRouting.shardId().equals(moveAllocationCommand.shardId())) {
                fail("shard [" + shardRouting + "] shouldn't be on node [" + moveAllocationCommand.toString() + "]");
            }
        }
    }

    public void testClusterRerouteNoAcknowledgementDryRun() throws InterruptedException {
        client().admin().indices().prepareCreate("test")
                .setSettings(settingsBuilder()
                        .put(SETTING_NUMBER_OF_SHARDS, between(cluster().numDataNodes(), DEFAULT_MAX_NUM_SHARDS))
                        .put(SETTING_NUMBER_OF_REPLICAS, 0)).get();
        ensureGreen();

        MoveAllocationCommand moveAllocationCommand = getAllocationCommand();

        ClusterRerouteResponse clusterRerouteResponse = client().admin().cluster().prepareReroute().setTimeout("0s").setDryRun(true).add(moveAllocationCommand).get();
        //acknowledged anyway as no changes were made
        assertThat(clusterRerouteResponse.isAcknowledged(), equalTo(true));
    }

    private MoveAllocationCommand getAllocationCommand() {
        String fromNodeId = null;
        String toNodeId = null;
        ShardRouting shardToBeMoved = null;
        ClusterStateResponse clusterStateResponse = client().admin().cluster().prepareState().get();
        for (RoutingNode routingNode : clusterStateResponse.getState().getRoutingNodes()) {
            if (routingNode.node().isDataNode()) {
                if (fromNodeId == null && routingNode.numberOfOwningShards() > 0) {
                    fromNodeId = routingNode.nodeId();
                    shardToBeMoved = routingNode.get(randomInt(routingNode.size() - 1));
                } else {
                    toNodeId = routingNode.nodeId();
                }

                if (toNodeId != null && fromNodeId != null) {
                    break;
                }
            }
        }

        assertNotNull(fromNodeId);
        assertNotNull(toNodeId);
        assertNotNull(shardToBeMoved);

        logger.info("==> going to move shard [{}] from [{}] to [{}]", shardToBeMoved, fromNodeId, toNodeId);
        return new MoveAllocationCommand(shardToBeMoved.shardId(), fromNodeId, toNodeId);
    }

    public void testIndicesAliasesAcknowledgement() {
        createIndex("test");

        //testing acknowledgement when trying to submit an existing alias too
        //in that case it would not make any change, but we are sure about the cluster state
        //as the previous operation was acknowledged
        for (int i = 0; i < 2; i++) {
            assertAcked(client().admin().indices().prepareAliases().addAlias("test", "alias"));

            for (Client client : clients()) {
                AliasMetaData aliasMetaData = ((AliasOrIndex.Alias) getLocalClusterState(client).metaData().getAliasAndIndexLookup().get("alias")).getFirstAliasMetaData();
                assertThat(aliasMetaData.alias(), equalTo("alias"));
            }
        }
    }

    public void testIndicesAliasesNoAcknowledgement() {
        createIndex("test");

        IndicesAliasesResponse indicesAliasesResponse = client().admin().indices().prepareAliases().addAlias("test", "alias").setTimeout("0s").get();
        assertThat(indicesAliasesResponse.isAcknowledged(), equalTo(false));
    }

    public void testCloseIndexAcknowledgement() {
        createIndex("test");
        ensureGreen();

        assertAcked(client().admin().indices().prepareClose("test"));

        for (Client client : clients()) {
            IndexMetaData indexMetaData = getLocalClusterState(client).metaData().indices().get("test");
            assertThat(indexMetaData.getState(), equalTo(State.CLOSE));
        }
    }

    public void testCloseIndexNoAcknowledgement() {
        createIndex("test");
        ensureGreen();

        CloseIndexResponse closeIndexResponse = client().admin().indices().prepareClose("test").setTimeout("0s").get();
        assertThat(closeIndexResponse.isAcknowledged(), equalTo(false));
    }

    public void testOpenIndexAcknowledgement() {
        createIndex("test");
        ensureGreen();

        assertAcked(client().admin().indices().prepareClose("test"));

        assertAcked(client().admin().indices().prepareOpen("test"));

        for (Client client : clients()) {
            IndexMetaData indexMetaData = getLocalClusterState(client).metaData().indices().get("test");
            assertThat(indexMetaData.getState(), equalTo(State.OPEN));
        }
    }

    public void testPutMappingAcknowledgement() {
        createIndex("test");
        ensureGreen();

        assertAcked(client().admin().indices().preparePutMapping("test").setType("test").setSource("field", "type=string,index=not_analyzed"));

        for (Client client : clients()) {
            assertThat(getLocalClusterState(client).metaData().indices().get("test").mapping("test"), notNullValue());
        }
    }

    public void testPutMappingNoAcknowledgement() {
        createIndex("test");
        ensureGreen();

        PutMappingResponse putMappingResponse = client().admin().indices().preparePutMapping("test").setType("test").setSource("field", "type=string,index=not_analyzed").setTimeout("0s").get();
        assertThat(putMappingResponse.isAcknowledged(), equalTo(false));
    }

    public void testCreateIndexAcknowledgement() {
        createIndex("test");

        for (Client client : clients()) {
            assertThat(getLocalClusterState(client).metaData().indices().containsKey("test"), equalTo(true));
        }

        //let's wait for green, otherwise there can be issues with after test checks (mock directory wrapper etc.)
        //but we do want to check that the new index is on all nodes cluster state even before green
        ensureGreen();
    }

    public void testCreateIndexNoAcknowledgement() {
        CreateIndexResponse createIndexResponse = client().admin().indices().prepareCreate("test").setTimeout("0s").get();
        assertThat(createIndexResponse.isAcknowledged(), equalTo(false));

        //let's wait for green, otherwise there can be issues with after test checks (mock directory wrapper etc.)
        ensureGreen();
    }

    private static ClusterState getLocalClusterState(Client client) {
        return client.admin().cluster().prepareState().setLocal(true).get().getState();
    }
}
