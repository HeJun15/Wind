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

package org.elasticsearch.action.fieldstats;

import org.elasticsearch.action.support.broadcast.BroadcastShardResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.index.shard.ShardId;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 */
public class FieldStatsShardResponse extends BroadcastShardResponse {

    private Map<String, FieldStats> fieldStats;

    public FieldStatsShardResponse() {
    }

    public FieldStatsShardResponse(ShardId shardId, Map<String, FieldStats> fieldStats) {
        super(shardId);
        this.fieldStats = fieldStats;
    }

    public Map<String, FieldStats> getFieldStats() {
        return fieldStats;
    }


    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        final int size = in.readVInt();
        fieldStats = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = in.readString();
            FieldStats value = FieldStats.read(in);
            fieldStats.put(key, value);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeVInt(fieldStats.size());
        for (Map.Entry<String, FieldStats> entry : fieldStats.entrySet()) {
            out.writeString(entry.getKey());
            entry.getValue().writeTo(out);
        }
    }
}
