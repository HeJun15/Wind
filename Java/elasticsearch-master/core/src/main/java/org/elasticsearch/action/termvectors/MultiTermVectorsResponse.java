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

package org.elasticsearch.action.termvectors;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

public class MultiTermVectorsResponse extends ActionResponse implements Iterable<MultiTermVectorsItemResponse>, ToXContent {

    /**
     * Represents a failure.
     */
    public static class Failure implements Streamable {
        private String index;
        private String type;
        private String id;
        private Throwable cause;

        Failure() {

        }

        public Failure(String index, String type, String id, Throwable cause) {
            this.index = index;
            this.type = type;
            this.id = id;
            this.cause = cause;
        }

        /**
         * The index name of the action.
         */
        public String getIndex() {
            return this.index;
        }

        /**
         * The type of the action.
         */
        public String getType() {
            return type;
        }

        /**
         * The id of the action.
         */
        public String getId() {
            return id;
        }

        /**
         * The failure cause.
         */
        public Throwable getCause() {
            return this.cause;
        }

        public static Failure readFailure(StreamInput in) throws IOException {
            Failure failure = new Failure();
            failure.readFrom(in);
            return failure;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            index = in.readString();
            type = in.readOptionalString();
            id = in.readString();
            cause = in.readThrowable();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(index);
            out.writeOptionalString(type);
            out.writeString(id);
            out.writeThrowable(cause);
        }
    }

    private MultiTermVectorsItemResponse[] responses;

    MultiTermVectorsResponse() {
    }

    public MultiTermVectorsResponse(MultiTermVectorsItemResponse[] responses) {
        this.responses = responses;
    }

    public MultiTermVectorsItemResponse[] getResponses() {
        return this.responses;
    }

    @Override
    public Iterator<MultiTermVectorsItemResponse> iterator() {
        return Arrays.stream(responses).iterator();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startArray(Fields.DOCS);
        for (MultiTermVectorsItemResponse response : responses) {
            if (response.isFailed()) {
                builder.startObject();
                Failure failure = response.getFailure();
                builder.field(Fields._INDEX, failure.getIndex());
                builder.field(Fields._TYPE, failure.getType());
                builder.field(Fields._ID, failure.getId());
                ElasticsearchException.renderThrowable(builder, params, failure.getCause());
                builder.endObject();
            } else {
                TermVectorsResponse getResponse = response.getResponse();
                builder.startObject();
                getResponse.toXContent(builder, params);
                builder.endObject();
            }
        }
        builder.endArray();
        return builder;
    }

    static final class Fields {
        static final XContentBuilderString DOCS = new XContentBuilderString("docs");
        static final XContentBuilderString _INDEX = new XContentBuilderString("_index");
        static final XContentBuilderString _TYPE = new XContentBuilderString("_type");
        static final XContentBuilderString _ID = new XContentBuilderString("_id");
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        responses = new MultiTermVectorsItemResponse[in.readVInt()];
        for (int i = 0; i < responses.length; i++) {
            responses[i] = MultiTermVectorsItemResponse.readItemResponse(in);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeVInt(responses.length);
        for (MultiTermVectorsItemResponse response : responses) {
            response.writeTo(out);
        }
    }
}