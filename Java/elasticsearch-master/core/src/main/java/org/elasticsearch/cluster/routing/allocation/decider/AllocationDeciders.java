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

package org.elasticsearch.cluster.routing.allocation.decider;

import org.elasticsearch.cluster.routing.RoutingNode;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.allocation.RoutingAllocation;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;

import java.util.Set;

/**
 * A composite {@link AllocationDecider} combining the "decision" of multiple
 * {@link AllocationDecider} implementations into a single allocation decision.
 */
public class AllocationDeciders extends AllocationDecider {

    private final AllocationDecider[] allocations;

    public AllocationDeciders(Settings settings, AllocationDecider[] allocations) {
        super(settings);
        this.allocations = allocations;
    }

    @Inject
    public AllocationDeciders(Settings settings, Set<AllocationDecider> allocations) {
        this(settings, allocations.toArray(new AllocationDecider[allocations.size()]));
    }

    @Override
    public Decision canRebalance(ShardRouting shardRouting, RoutingAllocation allocation) {
        Decision.Multi ret = new Decision.Multi();
        for (AllocationDecider allocationDecider : allocations) {
            Decision decision = allocationDecider.canRebalance(shardRouting, allocation);
            // short track if a NO is returned.
            if (decision == Decision.NO) {
                if (!allocation.debugDecision()) {
                    return decision;
                } else {
                    ret.add(decision);
                }
            } else if (decision != Decision.ALWAYS) {
                ret.add(decision);
            }
        }
        return ret;
    }

    @Override
    public Decision canAllocate(ShardRouting shardRouting, RoutingNode node, RoutingAllocation allocation) {
        if (allocation.shouldIgnoreShardForNode(shardRouting.shardId(), node.nodeId())) {
            return Decision.NO;
        }
        Decision.Multi ret = new Decision.Multi();
        for (AllocationDecider allocationDecider : allocations) {
            Decision decision = allocationDecider.canAllocate(shardRouting, node, allocation);
            // short track if a NO is returned.
            if (decision == Decision.NO) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Can not allocate [{}] on node [{}] due to [{}]", shardRouting, node.nodeId(), allocationDecider.getClass().getSimpleName());
                }
                // short circuit only if debugging is not enabled
                if (!allocation.debugDecision()) {
                    return decision;
                } else {
                    ret.add(decision);
                }
            } else if (decision != Decision.ALWAYS) {
                // the assumption is that a decider that returns the static instance Decision#ALWAYS
                // does not really implements canAllocate
                ret.add(decision);
            }
        }
        return ret;
    }

    @Override
    public Decision canRemain(ShardRouting shardRouting, RoutingNode node, RoutingAllocation allocation) {
        if (allocation.shouldIgnoreShardForNode(shardRouting.shardId(), node.nodeId())) {
            if (logger.isTraceEnabled()) {
                logger.trace("Shard [{}] should be ignored for node [{}]", shardRouting, node.nodeId());
            }
            return Decision.NO;
        }
        Decision.Multi ret = new Decision.Multi();
        for (AllocationDecider allocationDecider : allocations) {
            Decision decision = allocationDecider.canRemain(shardRouting, node, allocation);
            // short track if a NO is returned.
            if (decision == Decision.NO) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Shard [{}] can not remain on node [{}] due to [{}]", shardRouting, node.nodeId(), allocationDecider.getClass().getSimpleName());
                }
                if (!allocation.debugDecision()) {
                    return decision;
                } else {
                    ret.add(decision);
                }
            } else if (decision != Decision.ALWAYS) {
                ret.add(decision);
            }
        }
        return ret;
    }

    @Override
    public Decision canAllocate(ShardRouting shardRouting, RoutingAllocation allocation) {
        Decision.Multi ret = new Decision.Multi();
        for (AllocationDecider allocationDecider : allocations) {
            Decision decision = allocationDecider.canAllocate(shardRouting, allocation);
            // short track if a NO is returned.
            if (decision == Decision.NO) {
                if (!allocation.debugDecision()) {
                    return decision;
                } else {
                    ret.add(decision);
                }
            } else if (decision != Decision.ALWAYS) {
                ret.add(decision);
            }
        }
        return ret;
    }

    @Override
    public Decision canAllocate(RoutingNode node, RoutingAllocation allocation) {
        Decision.Multi ret = new Decision.Multi();
        for (AllocationDecider allocationDecider : allocations) {
            Decision decision = allocationDecider.canAllocate(node, allocation);
            // short track if a NO is returned.
            if (decision == Decision.NO) {
                if (!allocation.debugDecision()) {
                    return decision;
                } else {
                    ret.add(decision);
                }
            } else if (decision != Decision.ALWAYS) {
                ret.add(decision);
            }
        }
        return ret;
    }

    @Override
    public Decision canRebalance(RoutingAllocation allocation) {
        Decision.Multi ret = new Decision.Multi();
        for (AllocationDecider allocationDecider : allocations) {
            Decision decision = allocationDecider.canRebalance(allocation);
            // short track if a NO is returned.
            if (decision == Decision.NO) {
                if (!allocation.debugDecision()) {
                    return decision;
                } else {
                    ret.add(decision);
                }
            } else if (decision != Decision.ALWAYS) {
                ret.add(decision);
            }
        }
        return ret;
    }
}
