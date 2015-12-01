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

package org.elasticsearch.action.admin.cluster.snapshots.get;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.SnapshotId;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.snapshots.Snapshot;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotsService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Transport Action for get snapshots operation
 */
public class TransportGetSnapshotsAction extends TransportMasterNodeAction<GetSnapshotsRequest, GetSnapshotsResponse> {
    private final SnapshotsService snapshotsService;

    @Inject
    public TransportGetSnapshotsAction(Settings settings, TransportService transportService, ClusterService clusterService,
                                       ThreadPool threadPool, SnapshotsService snapshotsService, ActionFilters actionFilters,
                                       IndexNameExpressionResolver indexNameExpressionResolver) {
        super(settings, GetSnapshotsAction.NAME, transportService, clusterService, threadPool, actionFilters, indexNameExpressionResolver, GetSnapshotsRequest::new);
        this.snapshotsService = snapshotsService;
    }

    @Override
    protected String executor() {
        return ThreadPool.Names.GENERIC;
    }

    @Override
    protected GetSnapshotsResponse newResponse() {
        return new GetSnapshotsResponse();
    }

    @Override
    protected ClusterBlockException checkBlock(GetSnapshotsRequest request, ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_READ);
    }

    @Override
    protected void masterOperation(final GetSnapshotsRequest request, ClusterState state, final ActionListener<GetSnapshotsResponse> listener) {
        try {
            List<SnapshotInfo> snapshotInfoBuilder = new ArrayList<>();
            if (isAllSnapshots(request.snapshots())) {
                List<Snapshot> snapshots = snapshotsService.snapshots(request.repository(), request.ignoreUnavailable());
                for (Snapshot snapshot : snapshots) {
                    snapshotInfoBuilder.add(new SnapshotInfo(snapshot));
                }
            } else if (isCurrentSnapshots(request.snapshots())) {
                List<Snapshot> snapshots = snapshotsService.currentSnapshots(request.repository());
                for (Snapshot snapshot : snapshots) {
                    snapshotInfoBuilder.add(new SnapshotInfo(snapshot));
                }
            } else {
                for (int i = 0; i < request.snapshots().length; i++) {
                    SnapshotId snapshotId = new SnapshotId(request.repository(), request.snapshots()[i]);
                    snapshotInfoBuilder.add(new SnapshotInfo(snapshotsService.snapshot(snapshotId)));
                }
            }
            listener.onResponse(new GetSnapshotsResponse(Collections.unmodifiableList(snapshotInfoBuilder)));
        } catch (Throwable t) {
            listener.onFailure(t);
        }
    }

    private boolean isAllSnapshots(String[] snapshots) {
        return (snapshots.length == 0) || (snapshots.length == 1 && GetSnapshotsRequest.ALL_SNAPSHOTS.equalsIgnoreCase(snapshots[0]));
    }

    private boolean isCurrentSnapshots(String[] snapshots) {
        return (snapshots.length == 1 && GetSnapshotsRequest.CURRENT_SNAPSHOT.equalsIgnoreCase(snapshots[0]));
    }
}
