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

package org.elasticsearch.action.admin.indices.warmer.get;

import com.carrotsearch.hppc.cursors.ObjectObjectCursor;

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.search.warmer.IndexWarmersMetaData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds a warmer-name to a list of {@link IndexWarmersMetaData} mapping for each warmer specified
 * in the {@link GetWarmersRequest}. This information is fetched from the current master since the metadata
 * is contained inside the cluster-state
 */
public class GetWarmersResponse extends ActionResponse {

    private ImmutableOpenMap<String, List<IndexWarmersMetaData.Entry>> warmers = ImmutableOpenMap.of();

    GetWarmersResponse(ImmutableOpenMap<String, List<IndexWarmersMetaData.Entry>> warmers) {
        this.warmers = warmers;
    }

    GetWarmersResponse() {
    }

    public ImmutableOpenMap<String, List<IndexWarmersMetaData.Entry>> warmers() {
        return warmers;
    }

    public ImmutableOpenMap<String, List<IndexWarmersMetaData.Entry>> getWarmers() {
        return warmers();
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        int size = in.readVInt();
        ImmutableOpenMap.Builder<String, List<IndexWarmersMetaData.Entry>> indexMapBuilder = ImmutableOpenMap.builder();
        for (int i = 0; i < size; i++) {
            String key = in.readString();
            int valueSize = in.readVInt();
            List<IndexWarmersMetaData.Entry> warmerEntryBuilder = new ArrayList<>();
            for (int j = 0; j < valueSize; j++) {
                String name = in.readString();
                String[] types = in.readStringArray();
                IndexWarmersMetaData.SearchSource source = null;
                if (in.readBoolean()) {
                    source = new IndexWarmersMetaData.SearchSource(in);
                }
                Boolean queryCache = null;
                queryCache = in.readOptionalBoolean();
                warmerEntryBuilder.add(new IndexWarmersMetaData.Entry(
                                name,
                                types,
                                queryCache,
                                source)
                );
            }
            indexMapBuilder.put(key, Collections.unmodifiableList(warmerEntryBuilder));
        }
        warmers = indexMapBuilder.build();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeVInt(warmers.size());
        for (ObjectObjectCursor<String, List<IndexWarmersMetaData.Entry>> indexEntry : warmers) {
            out.writeString(indexEntry.key);
            out.writeVInt(indexEntry.value.size());
            for (IndexWarmersMetaData.Entry warmerEntry : indexEntry.value) {
                out.writeString(warmerEntry.name());
                out.writeStringArray(warmerEntry.types());
                boolean hasWarmerSource = warmerEntry != null;
                out.writeBoolean(hasWarmerSource);
                if (hasWarmerSource) {
                    warmerEntry.source().writeTo(out);
                }
                out.writeOptionalBoolean(warmerEntry.requestCache());
            }
        }
    }
}
