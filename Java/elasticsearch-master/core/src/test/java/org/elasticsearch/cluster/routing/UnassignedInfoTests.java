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

package org.elasticsearch.cluster.routing;

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.randomizedtesting.generators.RandomPicks;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.metadata.SnapshotId;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.routing.allocation.AllocationService;
import org.elasticsearch.cluster.routing.allocation.FailedRerouteAllocation;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.test.ESAllocationTestCase;

import java.util.Collections;
import java.util.EnumSet;

import static org.elasticsearch.cluster.routing.ShardRoutingState.*;
import static org.hamcrest.Matchers.*;

/**
 */
public class UnassignedInfoTests extends ESAllocationTestCase {
    public void testReasonOrdinalOrder() {
        UnassignedInfo.Reason[] order = new UnassignedInfo.Reason[]{
                UnassignedInfo.Reason.INDEX_CREATED,
                UnassignedInfo.Reason.CLUSTER_RECOVERED,
                UnassignedInfo.Reason.INDEX_REOPENED,
                UnassignedInfo.Reason.DANGLING_INDEX_IMPORTED,
                UnassignedInfo.Reason.NEW_INDEX_RESTORED,
                UnassignedInfo.Reason.EXISTING_INDEX_RESTORED,
                UnassignedInfo.Reason.REPLICA_ADDED,
                UnassignedInfo.Reason.ALLOCATION_FAILED,
                UnassignedInfo.Reason.NODE_LEFT,
                UnassignedInfo.Reason.REROUTE_CANCELLED,
                UnassignedInfo.Reason.REINITIALIZED,
                UnassignedInfo.Reason.REALLOCATED_REPLICA};
        for (int i = 0; i < order.length; i++) {
            assertThat(order[i].ordinal(), equalTo(i));
        }
        assertThat(UnassignedInfo.Reason.values().length, equalTo(order.length));
    }

    public void testSerialization() throws Exception {
        UnassignedInfo meta = new UnassignedInfo(RandomPicks.randomFrom(getRandom(), UnassignedInfo.Reason.values()), randomBoolean() ? randomAsciiOfLength(4) : null);
        BytesStreamOutput out = new BytesStreamOutput();
        meta.writeTo(out);
        out.close();

        UnassignedInfo read = new UnassignedInfo(StreamInput.wrap(out.bytes()));
        assertThat(read.getReason(), equalTo(meta.getReason()));
        assertThat(read.getUnassignedTimeInMillis(), equalTo(meta.getUnassignedTimeInMillis()));
        assertThat(read.getMessage(), equalTo(meta.getMessage()));
        assertThat(read.getDetails(), equalTo(meta.getDetails()));
    }

    public void testIndexCreated() {
        MetaData metaData = MetaData.builder()
                .put(IndexMetaData.builder("test").settings(settings(Version.CURRENT)).numberOfShards(randomIntBetween(1, 3)).numberOfReplicas(randomIntBetween(0, 3)))
                .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
                .metaData(metaData)
                .routingTable(RoutingTable.builder().addAsNew(metaData.index("test")).build()).build();
        for (ShardRouting shard : clusterState.getRoutingNodes().shardsWithState(UNASSIGNED)) {
            assertThat(shard.unassignedInfo().getReason(), equalTo(UnassignedInfo.Reason.INDEX_CREATED));
        }
    }

    public void testClusterRecovered() {
        MetaData metaData = MetaData.builder()
                .put(IndexMetaData.builder("test").settings(settings(Version.CURRENT)).numberOfShards(randomIntBetween(1, 3)).numberOfReplicas(randomIntBetween(0, 3)))
                .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
                .metaData(metaData)
                .routingTable(RoutingTable.builder().addAsRecovery(metaData.index("test")).build()).build();
        for (ShardRouting shard : clusterState.getRoutingNodes().shardsWithState(UNASSIGNED)) {
            assertThat(shard.unassignedInfo().getReason(), equalTo(UnassignedInfo.Reason.CLUSTER_RECOVERED));
        }
    }

    public void testIndexReopened() {
        MetaData metaData = MetaData.builder()
                .put(IndexMetaData.builder("test").settings(settings(Version.CURRENT)).numberOfShards(randomIntBetween(1, 3)).numberOfReplicas(randomIntBetween(0, 3)))
                .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
                .metaData(metaData)
                .routingTable(RoutingTable.builder().addAsFromCloseToOpen(metaData.index("test")).build()).build();
        for (ShardRouting shard : clusterState.getRoutingNodes().shardsWithState(UNASSIGNED)) {
            assertThat(shard.unassignedInfo().getReason(), equalTo(UnassignedInfo.Reason.INDEX_REOPENED));
        }
    }

    public void testNewIndexRestored() {
        MetaData metaData = MetaData.builder()
                .put(IndexMetaData.builder("test").settings(settings(Version.CURRENT)).numberOfShards(randomIntBetween(1, 3)).numberOfReplicas(randomIntBetween(0, 3)))
                .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
                .metaData(metaData)
                .routingTable(RoutingTable.builder().addAsNewRestore(metaData.index("test"), new RestoreSource(new SnapshotId("rep1", "snp1"), Version.CURRENT, "test"), new IntHashSet()).build()).build();
        for (ShardRouting shard : clusterState.getRoutingNodes().shardsWithState(UNASSIGNED)) {
            assertThat(shard.unassignedInfo().getReason(), equalTo(UnassignedInfo.Reason.NEW_INDEX_RESTORED));
        }
    }

    public void testExistingIndexRestored() {
        MetaData metaData = MetaData.builder()
                .put(IndexMetaData.builder("test").settings(settings(Version.CURRENT)).numberOfShards(randomIntBetween(1, 3)).numberOfReplicas(randomIntBetween(0, 3)))
                .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
                .metaData(metaData)
                .routingTable(RoutingTable.builder().addAsRestore(metaData.index("test"), new RestoreSource(new SnapshotId("rep1", "snp1"), Version.CURRENT, "test")).build()).build();
        for (ShardRouting shard : clusterState.getRoutingNodes().shardsWithState(UNASSIGNED)) {
            assertThat(shard.unassignedInfo().getReason(), equalTo(UnassignedInfo.Reason.EXISTING_INDEX_RESTORED));
        }
    }

    public void testDanglingIndexImported() {
        MetaData metaData = MetaData.builder()
                .put(IndexMetaData.builder("test").settings(settings(Version.CURRENT)).numberOfShards(randomIntBetween(1, 3)).numberOfReplicas(randomIntBetween(0, 3)))
                .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
                .metaData(metaData)
                .routingTable(RoutingTable.builder().addAsFromDangling(metaData.index("test")).build()).build();
        for (ShardRouting shard : clusterState.getRoutingNodes().shardsWithState(UNASSIGNED)) {
            assertThat(shard.unassignedInfo().getReason(), equalTo(UnassignedInfo.Reason.DANGLING_INDEX_IMPORTED));
        }
    }

    public void testReplicaAdded() {
        AllocationService allocation = createAllocationService();
        MetaData metaData = MetaData.builder()
                .put(IndexMetaData.builder("test").settings(settings(Version.CURRENT)).numberOfShards(1).numberOfReplicas(0))
                .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
                .metaData(metaData)
                .routingTable(RoutingTable.builder().addAsNew(metaData.index("test")).build()).build();
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder().put(newNode("node1"))).build();
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.reroute(clusterState, "reroute")).build();
        // starting primaries
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.applyStartedShards(clusterState, clusterState.getRoutingNodes().shardsWithState(INITIALIZING))).build();
        IndexRoutingTable.Builder builder = IndexRoutingTable.builder("test");
        for (IndexShardRoutingTable indexShardRoutingTable : clusterState.routingTable().index("test")) {
            builder.addIndexShard(indexShardRoutingTable);
        }
        builder.addReplica();
        clusterState = ClusterState.builder(clusterState).routingTable(RoutingTable.builder(clusterState.routingTable()).add(builder).build()).build();
        assertThat(clusterState.getRoutingNodes().shardsWithState(UNASSIGNED).size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().shardsWithState(UNASSIGNED).get(0).unassignedInfo(), notNullValue());
        assertThat(clusterState.getRoutingNodes().shardsWithState(UNASSIGNED).get(0).unassignedInfo().getReason(), equalTo(UnassignedInfo.Reason.REPLICA_ADDED));
    }

    /**
     * The unassigned meta is kept when a shard goes to INITIALIZING, but cleared when it moves to STARTED.
     */
    public void testStateTransitionMetaHandling() {
        ShardRouting shard = TestShardRouting.newShardRouting("test", 1, null, null, null, true, ShardRoutingState.UNASSIGNED, 1, new UnassignedInfo(UnassignedInfo.Reason.INDEX_CREATED, null));
        ShardRouting mutable = new ShardRouting(shard);
        assertThat(mutable.unassignedInfo(), notNullValue());
        mutable.initialize("test_node", -1);
        assertThat(mutable.state(), equalTo(ShardRoutingState.INITIALIZING));
        assertThat(mutable.unassignedInfo(), notNullValue());
        mutable.moveToStarted();
        assertThat(mutable.state(), equalTo(ShardRoutingState.STARTED));
        assertThat(mutable.unassignedInfo(), nullValue());
    }

    /**
     * Tests that during reroute when a node is detected as leaving the cluster, the right unassigned meta is set
     */
    public void testNodeLeave() {
        AllocationService allocation = createAllocationService();
        MetaData metaData = MetaData.builder()
                .put(IndexMetaData.builder("test").settings(settings(Version.CURRENT)).numberOfShards(1).numberOfReplicas(1))
                .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
                .metaData(metaData)
                .routingTable(RoutingTable.builder().addAsNew(metaData.index("test")).build()).build();
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder().put(newNode("node1")).put(newNode("node2"))).build();
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.reroute(clusterState, "reroute")).build();
        // starting primaries
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.applyStartedShards(clusterState, clusterState.getRoutingNodes().shardsWithState(INITIALIZING))).build();
        // starting replicas
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.applyStartedShards(clusterState, clusterState.getRoutingNodes().shardsWithState(INITIALIZING))).build();
        assertThat(clusterState.getRoutingNodes().unassigned().size() > 0, equalTo(false));
        // remove node2 and reroute
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder(clusterState.nodes()).remove("node2")).build();
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.reroute(clusterState, "reroute")).build();
        // verify that NODE_LEAVE is the reason for meta
        assertThat(clusterState.getRoutingNodes().unassigned().size() > 0, equalTo(true));
        assertThat(clusterState.getRoutingNodes().shardsWithState(UNASSIGNED).size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().shardsWithState(UNASSIGNED).get(0).unassignedInfo(), notNullValue());
        assertThat(clusterState.getRoutingNodes().shardsWithState(UNASSIGNED).get(0).unassignedInfo().getReason(), equalTo(UnassignedInfo.Reason.NODE_LEFT));
        assertThat(clusterState.getRoutingNodes().shardsWithState(UNASSIGNED).get(0).unassignedInfo().getUnassignedTimeInMillis(), greaterThan(0l));
    }

    /**
     * Verifies that when a shard fails, reason is properly set and details are preserved.
     */
    public void testFailedShard() {
        AllocationService allocation = createAllocationService();
        MetaData metaData = MetaData.builder()
                .put(IndexMetaData.builder("test").settings(settings(Version.CURRENT)).numberOfShards(1).numberOfReplicas(1))
                .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
                .metaData(metaData)
                .routingTable(RoutingTable.builder().addAsNew(metaData.index("test")).build()).build();
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder().put(newNode("node1")).put(newNode("node2"))).build();
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.reroute(clusterState, "reroute")).build();
        // starting primaries
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.applyStartedShards(clusterState, clusterState.getRoutingNodes().shardsWithState(INITIALIZING))).build();
        // starting replicas
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.applyStartedShards(clusterState, clusterState.getRoutingNodes().shardsWithState(INITIALIZING))).build();
        assertThat(clusterState.getRoutingNodes().unassigned().size() > 0, equalTo(false));
        // fail shard
        ShardRouting shardToFail = clusterState.getRoutingNodes().shardsWithState(STARTED).get(0);
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.applyFailedShards(clusterState, Collections.singletonList(new FailedRerouteAllocation.FailedShard(shardToFail, "test fail", null)))).build();
        // verify the reason and details
        assertThat(clusterState.getRoutingNodes().unassigned().size() > 0, equalTo(true));
        assertThat(clusterState.getRoutingNodes().shardsWithState(UNASSIGNED).size(), equalTo(1));
        assertThat(clusterState.getRoutingNodes().shardsWithState(UNASSIGNED).get(0).unassignedInfo(), notNullValue());
        assertThat(clusterState.getRoutingNodes().shardsWithState(UNASSIGNED).get(0).unassignedInfo().getReason(), equalTo(UnassignedInfo.Reason.ALLOCATION_FAILED));
        assertThat(clusterState.getRoutingNodes().shardsWithState(UNASSIGNED).get(0).unassignedInfo().getMessage(), equalTo("test fail"));
        assertThat(clusterState.getRoutingNodes().shardsWithState(UNASSIGNED).get(0).unassignedInfo().getDetails(), equalTo("test fail"));
        assertThat(clusterState.getRoutingNodes().shardsWithState(UNASSIGNED).get(0).unassignedInfo().getUnassignedTimeInMillis(), greaterThan(0l));
    }

    /**
     * Verifies that delayed allocation calculation are correct.
     */
    public void testUnassignedDelayedOnlyOnNodeLeft() throws Exception {
        final UnassignedInfo unassignedInfo = new UnassignedInfo(UnassignedInfo.Reason.NODE_LEFT, null);
        long delay = unassignedInfo.updateDelay(unassignedInfo.getUnassignedTimeInNanos() + 1, // add 1 tick delay
                Settings.builder().put(UnassignedInfo.INDEX_DELAYED_NODE_LEFT_TIMEOUT_SETTING, "10h").build(), Settings.EMPTY);
        long cachedDelay = unassignedInfo.getLastComputedLeftDelayNanos();
        assertThat(delay, equalTo(cachedDelay));
        assertThat(delay, equalTo(TimeValue.timeValueHours(10).nanos() - 1));
    }

    /**
     * Verifies that delayed allocation is only computed when the reason is NODE_LEFT.
     */
    public void testUnassignedDelayOnlyNodeLeftNonNodeLeftReason() throws Exception {
        EnumSet<UnassignedInfo.Reason> reasons = EnumSet.allOf(UnassignedInfo.Reason.class);
        reasons.remove(UnassignedInfo.Reason.NODE_LEFT);
        UnassignedInfo unassignedInfo = new UnassignedInfo(RandomPicks.randomFrom(getRandom(), reasons), null);
        long delay = unassignedInfo.updateDelay(unassignedInfo.getUnassignedTimeInNanos() + 1, // add 1 tick delay
                Settings.builder().put(UnassignedInfo.INDEX_DELAYED_NODE_LEFT_TIMEOUT_SETTING, "10h").build(), Settings.EMPTY);
        assertThat(delay, equalTo(0l));
        delay = unassignedInfo.getLastComputedLeftDelayNanos();
        assertThat(delay, equalTo(0l));
    }

    /**
     * Verifies that delayed allocation calculation are correct.
     */
    public void testLeftDelayCalculation() throws Exception {
        final long baseTime = System.nanoTime();
        final UnassignedInfo unassignedInfo = new UnassignedInfo(UnassignedInfo.Reason.NODE_LEFT, "test", null, baseTime, System.currentTimeMillis());
        final long totalDelayNanos = TimeValue.timeValueMillis(10).nanos();
        final Settings settings = Settings.builder().put(UnassignedInfo.INDEX_DELAYED_NODE_LEFT_TIMEOUT_SETTING, TimeValue.timeValueNanos(totalDelayNanos)).build();
        long delay = unassignedInfo.updateDelay(baseTime, settings, Settings.EMPTY);
        assertThat(delay, equalTo(totalDelayNanos));
        assertThat(delay, equalTo(unassignedInfo.getLastComputedLeftDelayNanos()));
        long delta1 = randomIntBetween(1, (int) (totalDelayNanos - 1));
        delay = unassignedInfo.updateDelay(baseTime + delta1, settings, Settings.EMPTY);
        assertThat(delay, equalTo(totalDelayNanos - delta1));
        assertThat(delay, equalTo(unassignedInfo.getLastComputedLeftDelayNanos()));
        delay = unassignedInfo.updateDelay(baseTime + totalDelayNanos, settings, Settings.EMPTY);
        assertThat(delay, equalTo(0L));
        assertThat(delay, equalTo(unassignedInfo.getLastComputedLeftDelayNanos()));
        delay = unassignedInfo.updateDelay(baseTime + totalDelayNanos + randomIntBetween(1, 20), settings, Settings.EMPTY);
        assertThat(delay, equalTo(0L));
        assertThat(delay, equalTo(unassignedInfo.getLastComputedLeftDelayNanos()));
    }


    public void testNumberOfDelayedUnassigned() throws Exception {
        MockAllocationService allocation = createAllocationService(Settings.EMPTY, new DelayedShardsMockGatewayAllocator());
        MetaData metaData = MetaData.builder()
                .put(IndexMetaData.builder("test1").settings(settings(Version.CURRENT)).numberOfShards(1).numberOfReplicas(1))
                .put(IndexMetaData.builder("test2").settings(settings(Version.CURRENT)).numberOfShards(1).numberOfReplicas(1))
                .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
                .metaData(metaData)
                .routingTable(RoutingTable.builder().addAsNew(metaData.index("test1")).addAsNew(metaData.index("test2")).build()).build();
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder().put(newNode("node1")).put(newNode("node2"))).build();
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.reroute(clusterState, "reroute")).build();
        assertThat(UnassignedInfo.getNumberOfDelayedUnassigned(clusterState), equalTo(0));
        // starting primaries
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.applyStartedShards(clusterState, clusterState.getRoutingNodes().shardsWithState(INITIALIZING))).build();
        // starting replicas
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.applyStartedShards(clusterState, clusterState.getRoutingNodes().shardsWithState(INITIALIZING))).build();
        assertThat(clusterState.getRoutingNodes().unassigned().size() > 0, equalTo(false));
        // remove node2 and reroute
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder(clusterState.nodes()).remove("node2")).build();
        // make sure both replicas are marked as delayed (i.e. not reallocated)
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.reroute(clusterState, "reroute")).build();
        assertThat(clusterState.prettyPrint(), UnassignedInfo.getNumberOfDelayedUnassigned(clusterState), equalTo(2));
    }

    public void testFindNextDelayedAllocation() {
        MockAllocationService allocation = createAllocationService(Settings.EMPTY, new DelayedShardsMockGatewayAllocator());
        final long baseTime = System.nanoTime();
        allocation.setNanoTimeOverride(baseTime);
        final TimeValue delayTest1 = TimeValue.timeValueMillis(randomIntBetween(1, 200));
        final TimeValue delayTest2 = TimeValue.timeValueMillis(randomIntBetween(1, 200));
        final long expectMinDelaySettingsNanos = Math.min(delayTest1.nanos(), delayTest2.nanos());

        MetaData metaData = MetaData.builder()
                .put(IndexMetaData.builder("test1").settings(settings(Version.CURRENT).put(UnassignedInfo.INDEX_DELAYED_NODE_LEFT_TIMEOUT_SETTING, delayTest1)).numberOfShards(1).numberOfReplicas(1))
                .put(IndexMetaData.builder("test2").settings(settings(Version.CURRENT).put(UnassignedInfo.INDEX_DELAYED_NODE_LEFT_TIMEOUT_SETTING, delayTest2)).numberOfShards(1).numberOfReplicas(1))
                .build();
        ClusterState clusterState = ClusterState.builder(ClusterName.DEFAULT)
                .metaData(metaData)
                .routingTable(RoutingTable.builder().addAsNew(metaData.index("test1")).addAsNew(metaData.index("test2")).build()).build();
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder().put(newNode("node1")).put(newNode("node2"))).build();
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.reroute(clusterState, "reroute")).build();
        assertThat(UnassignedInfo.getNumberOfDelayedUnassigned(clusterState), equalTo(0));
        // starting primaries
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.applyStartedShards(clusterState, clusterState.getRoutingNodes().shardsWithState(INITIALIZING))).build();
        // starting replicas
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.applyStartedShards(clusterState, clusterState.getRoutingNodes().shardsWithState(INITIALIZING))).build();
        assertThat(clusterState.getRoutingNodes().unassigned().size() > 0, equalTo(false));
        // remove node2 and reroute
        clusterState = ClusterState.builder(clusterState).nodes(DiscoveryNodes.builder(clusterState.nodes()).remove("node2")).build();
        clusterState = ClusterState.builder(clusterState).routingResult(allocation.reroute(clusterState, "reroute")).build();

        final long delta = randomBoolean() ? 0 : randomInt((int) expectMinDelaySettingsNanos);

        if (delta > 0) {
            allocation.setNanoTimeOverride(baseTime + delta);
            clusterState = ClusterState.builder(clusterState).routingResult(allocation.reroute(clusterState, "time moved")).build();
        }

        long minDelaySetting = UnassignedInfo.findSmallestDelayedAllocationSettingNanos(Settings.EMPTY, clusterState);
        assertThat(minDelaySetting, equalTo(expectMinDelaySettingsNanos));

        long nextDelay = UnassignedInfo.findNextDelayedAllocationIn(clusterState);
        assertThat(nextDelay, equalTo(expectMinDelaySettingsNanos - delta));
    }
}
