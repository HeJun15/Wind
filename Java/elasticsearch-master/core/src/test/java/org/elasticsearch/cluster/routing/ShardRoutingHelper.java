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

/**
 * A helper class that allows access to package private APIs for testing.
 */
public class ShardRoutingHelper {

    public static void relocate(ShardRouting routing, String nodeId) {
        relocate(routing, nodeId, -1);
    }

    public static void relocate(ShardRouting routing, String nodeId, long expectedByteSize) {
        routing.relocate(nodeId, expectedByteSize);
    }

    public static void moveToStarted(ShardRouting routing) {
        routing.moveToStarted();
    }

    public static void initialize(ShardRouting routing, String nodeId) {
        initialize(routing, nodeId, -1);
    }

    public static void initialize(ShardRouting routing, String nodeId, long expectedSize) {
        routing.initialize(nodeId, expectedSize);
    }

    public static void reinit(ShardRouting routing) {
        routing.reinitializeShard();
    }

    public static void moveToUnassigned(ShardRouting routing, UnassignedInfo info) {
        routing.moveToUnassigned(info);
    }

    public static ShardRouting newWithRestoreSource(ShardRouting routing, RestoreSource restoreSource) {
        return new ShardRouting(routing.index(), routing.shardId().id(), routing.currentNodeId(), routing.relocatingNodeId(), restoreSource, routing.primary(), routing.state(), routing.version(), routing.unassignedInfo(), routing.allocationId(), true, routing.getExpectedShardSize());
    }
}
