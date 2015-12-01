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

package org.elasticsearch.action.admin.cluster.snapshots.status;

import org.elasticsearch.cluster.SnapshotsInProgress.State;
import org.elasticsearch.cluster.metadata.SnapshotId;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.unmodifiableMap;

/**
 * Status of a snapshot
 */
public class SnapshotStatus implements ToXContent, Streamable {

    private SnapshotId snapshotId;

    private State state;

    private List<SnapshotIndexShardStatus> shards;

    private Map<String, SnapshotIndexStatus> indicesStatus;

    private SnapshotShardsStats shardsStats;

    private SnapshotStats stats;


    SnapshotStatus(SnapshotId snapshotId, State state, List<SnapshotIndexShardStatus> shards) {
        this.snapshotId = snapshotId;
        this.state = state;
        this.shards = shards;
        shardsStats = new SnapshotShardsStats(shards);
        updateShardStats();
    }

    SnapshotStatus() {
    }

    /**
     * Returns snapshot id
     */
    public SnapshotId getSnapshotId() {
        return snapshotId;
    }

    /**
     * Returns snapshot state
     */
    public State getState() {
        return state;
    }

    /**
     * Returns list of snapshot shards
     */
    public List<SnapshotIndexShardStatus> getShards() {
        return shards;
    }

    public SnapshotShardsStats getShardsStats() {
        return shardsStats;
    }

    /**
     * Returns list of snapshot indices
     */
    public Map<String, SnapshotIndexStatus> getIndices() {
        if (this.indicesStatus != null) {
            return this.indicesStatus;
        }

        Map<String, SnapshotIndexStatus> indicesStatus = new HashMap<>();

        Set<String> indices = new HashSet<>();
        for (SnapshotIndexShardStatus shard : shards) {
            indices.add(shard.getIndex());
        }

        for (String index : indices) {
            List<SnapshotIndexShardStatus> shards = new ArrayList<>();
            for (SnapshotIndexShardStatus shard : this.shards) {
                if (shard.getIndex().equals(index)) {
                    shards.add(shard);
                }
            }
            indicesStatus.put(index, new SnapshotIndexStatus(index, shards));
        }
        this.indicesStatus = unmodifiableMap(indicesStatus);
        return this.indicesStatus;

    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        snapshotId = SnapshotId.readSnapshotId(in);
        state = State.fromValue(in.readByte());
        int size = in.readVInt();
        List<SnapshotIndexShardStatus> builder = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            builder.add(SnapshotIndexShardStatus.readShardSnapshotStatus(in));
        }
        shards = Collections.unmodifiableList(builder);
        updateShardStats();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        snapshotId.writeTo(out);
        out.writeByte(state.value());
        out.writeVInt(shards.size());
        for (SnapshotIndexShardStatus shard : shards) {
            shard.writeTo(out);
        }
    }

    /**
     * Reads snapshot status from stream input
     *
     * @param in stream input
     * @return deserialized snapshot status
     */
    public static SnapshotStatus readSnapshotStatus(StreamInput in) throws IOException {
        SnapshotStatus snapshotInfo = new SnapshotStatus();
        snapshotInfo.readFrom(in);
        return snapshotInfo;
    }

    @Override
    public String toString() {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder().prettyPrint();
            builder.startObject();
            toXContent(builder, EMPTY_PARAMS);
            builder.endObject();
            return builder.string();
        } catch (IOException e) {
            return "{ \"error\" : \"" + e.getMessage() + "\"}";
        }
    }


    /**
     * Returns number of files in the snapshot
     */
    public SnapshotStats getStats() {
        return stats;
    }

    static final class Fields {
        static final XContentBuilderString SNAPSHOT = new XContentBuilderString("snapshot");
        static final XContentBuilderString REPOSITORY = new XContentBuilderString("repository");
        static final XContentBuilderString STATE = new XContentBuilderString("state");
        static final XContentBuilderString INDICES = new XContentBuilderString("indices");
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(Fields.SNAPSHOT, snapshotId.getSnapshot());
        builder.field(Fields.REPOSITORY, snapshotId.getRepository());
        builder.field(Fields.STATE, state.name());
        shardsStats.toXContent(builder, params);
        stats.toXContent(builder, params);
        builder.startObject(Fields.INDICES);
        for (SnapshotIndexStatus indexStatus : getIndices().values()) {
            indexStatus.toXContent(builder, params);
        }
        builder.endObject();
        builder.endObject();
        return builder;
    }

    private void updateShardStats() {
        stats = new SnapshotStats();
        shardsStats = new SnapshotShardsStats(shards);
        for (SnapshotIndexShardStatus shard : shards) {
            stats.add(shard.getStats());
        }
    }
}
