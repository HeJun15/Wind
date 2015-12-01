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

package org.elasticsearch.cluster.routing.allocation;

import org.elasticsearch.cluster.ClusterInfo;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.routing.RoutingNodes;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.allocation.decider.AllocationDeciders;

import java.util.List;

/**
 * This {@link RoutingAllocation} holds a list of started shards within a
 * cluster
 */
public class StartedRerouteAllocation extends RoutingAllocation {

    private final List<? extends ShardRouting> startedShards;

    public StartedRerouteAllocation(AllocationDeciders deciders, RoutingNodes routingNodes, DiscoveryNodes nodes, List<? extends ShardRouting> startedShards, ClusterInfo clusterInfo) {
        super(deciders, routingNodes, nodes, clusterInfo, System.nanoTime());
        this.startedShards = startedShards;
    }

    /**
     * Get started shards
     * @return list of started shards
     */
    public List<? extends ShardRouting> startedShards() {
        return startedShards;
    }
}