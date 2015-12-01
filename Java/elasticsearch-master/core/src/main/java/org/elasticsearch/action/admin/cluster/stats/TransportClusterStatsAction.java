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

package org.elasticsearch.action.admin.cluster.stats;

import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.action.admin.indices.stats.CommonStats;
import org.elasticsearch.action.admin.indices.stats.CommonStatsFlags;
import org.elasticsearch.action.admin.indices.stats.ShardStats;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.nodes.BaseNodeRequest;
import org.elasticsearch.action.support.nodes.TransportNodesAction;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.health.ClusterStateHealth;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.node.service.NodeService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 *
 */
public class TransportClusterStatsAction extends TransportNodesAction<ClusterStatsRequest, ClusterStatsResponse,
        TransportClusterStatsAction.ClusterStatsNodeRequest, ClusterStatsNodeResponse> {

    private static final CommonStatsFlags SHARD_STATS_FLAGS = new CommonStatsFlags(CommonStatsFlags.Flag.Docs, CommonStatsFlags.Flag.Store,
            CommonStatsFlags.Flag.FieldData, CommonStatsFlags.Flag.QueryCache, CommonStatsFlags.Flag.Completion, CommonStatsFlags.Flag.Segments,
            CommonStatsFlags.Flag.Percolate);

    private final NodeService nodeService;
    private final IndicesService indicesService;


    @Inject
    public TransportClusterStatsAction(Settings settings, ClusterName clusterName, ThreadPool threadPool,
                                       ClusterService clusterService, TransportService transportService,
                                       NodeService nodeService, IndicesService indicesService,
                                       ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver) {
        super(settings, ClusterStatsAction.NAME, clusterName, threadPool, clusterService, transportService, actionFilters,
                indexNameExpressionResolver, ClusterStatsRequest::new, ClusterStatsNodeRequest::new, ThreadPool.Names.MANAGEMENT);
        this.nodeService = nodeService;
        this.indicesService = indicesService;
    }

    @Override
    protected ClusterStatsResponse newResponse(ClusterStatsRequest clusterStatsRequest, AtomicReferenceArray responses) {
        final List<ClusterStatsNodeResponse> nodeStats = new ArrayList<>(responses.length());
        for (int i = 0; i < responses.length(); i++) {
            Object resp = responses.get(i);
            if (resp instanceof ClusterStatsNodeResponse) {
                nodeStats.add((ClusterStatsNodeResponse) resp);
            }
        }
        return new ClusterStatsResponse(System.currentTimeMillis(), clusterName,
                clusterService.state().metaData().clusterUUID(), nodeStats.toArray(new ClusterStatsNodeResponse[nodeStats.size()]));
    }

    @Override
    protected ClusterStatsNodeRequest newNodeRequest(String nodeId, ClusterStatsRequest request) {
        return new ClusterStatsNodeRequest(nodeId, request);
    }

    @Override
    protected ClusterStatsNodeResponse newNodeResponse() {
        return new ClusterStatsNodeResponse();
    }

    @Override
    protected ClusterStatsNodeResponse nodeOperation(ClusterStatsNodeRequest nodeRequest) {
        NodeInfo nodeInfo = nodeService.info(false, true, false, true, false, true, false, true);
        NodeStats nodeStats = nodeService.stats(CommonStatsFlags.NONE, false, true, true, false, true, false, false, false, false, false);
        List<ShardStats> shardsStats = new ArrayList<>();
        for (IndexService indexService : indicesService) {
            for (IndexShard indexShard : indexService) {
                if (indexShard.routingEntry() != null && indexShard.routingEntry().active()) {
                    // only report on fully started shards
                    shardsStats.add(new ShardStats(indexShard.routingEntry(), indexShard.shardPath(), new CommonStats(indexShard, SHARD_STATS_FLAGS), indexShard.commitStats()));
                }
            }
        }

        ClusterHealthStatus clusterStatus = null;
        if (clusterService.state().nodes().localNodeMaster()) {
            clusterStatus = new ClusterStateHealth(clusterService.state()).getStatus();
        }

        return new ClusterStatsNodeResponse(nodeInfo.getNode(), clusterStatus, nodeInfo, nodeStats, shardsStats.toArray(new ShardStats[shardsStats.size()]));

    }

    @Override
    protected boolean accumulateExceptions() {
        return false;
    }

    public static class ClusterStatsNodeRequest extends BaseNodeRequest {

        ClusterStatsRequest request;

        public ClusterStatsNodeRequest() {
        }

        ClusterStatsNodeRequest(String nodeId, ClusterStatsRequest request) {
            super(request, nodeId);
            this.request = request;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);
            request = new ClusterStatsRequest();
            request.readFrom(in);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            request.writeTo(out);
        }
    }
}
