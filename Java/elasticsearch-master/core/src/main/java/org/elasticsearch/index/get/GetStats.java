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

package org.elasticsearch.index.get;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;

import java.io.IOException;

/**
 */
public class GetStats implements Streamable, ToXContent {

    private long existsCount;
    private long existsTimeInMillis;
    private long missingCount;
    private long missingTimeInMillis;
    private long current;

    public GetStats() {
    }

    public GetStats(long existsCount, long existsTimeInMillis, long missingCount, long missingTimeInMillis, long current) {
        this.existsCount = existsCount;
        this.existsTimeInMillis = existsTimeInMillis;
        this.missingCount = missingCount;
        this.missingTimeInMillis = missingTimeInMillis;
        this.current = current;
    }

    public void add(GetStats stats) {
        if (stats == null) {
            return;
        }
        current += stats.current;
        addTotals(stats);
    }

    public void addTotals(GetStats stats) {
        if (stats == null) {
            return;
        }
        existsCount += stats.existsCount;
        existsTimeInMillis += stats.existsTimeInMillis;
        missingCount += stats.missingCount;
        missingTimeInMillis += stats.missingTimeInMillis;
        current += stats.current;
    }

    public long getCount() {
        return existsCount + missingCount;
    }

    public long getTimeInMillis() {
        return existsTimeInMillis + missingTimeInMillis;
    }

    public TimeValue getTime() {
        return new TimeValue(getTimeInMillis());
    }

    public long getExistsCount() {
        return this.existsCount;
    }

    public long getExistsTimeInMillis() {
        return this.existsTimeInMillis;
    }

    public TimeValue getExistsTime() {
        return new TimeValue(existsTimeInMillis);
    }

    public long getMissingCount() {
        return this.missingCount;
    }

    public long getMissingTimeInMillis() {
        return this.missingTimeInMillis;
    }

    public TimeValue getMissingTime() {
        return new TimeValue(missingTimeInMillis);
    }

    public long current() {
        return this.current;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(Fields.GET);
        builder.field(Fields.TOTAL, getCount());
        builder.timeValueField(Fields.TIME_IN_MILLIS, Fields.TIME, getTimeInMillis());
        builder.field(Fields.EXISTS_TOTAL, existsCount);
        builder.timeValueField(Fields.EXISTS_TIME_IN_MILLIS, Fields.EXISTS_TIME, existsTimeInMillis);
        builder.field(Fields.MISSING_TOTAL, missingCount);
        builder.timeValueField(Fields.MISSING_TIME_IN_MILLIS, Fields.MISSING_TIME, missingTimeInMillis);
        builder.field(Fields.CURRENT, current);
        builder.endObject();
        return builder;
    }

    static final class Fields {
        static final XContentBuilderString GET = new XContentBuilderString("get");
        static final XContentBuilderString TOTAL = new XContentBuilderString("total");
        static final XContentBuilderString TIME = new XContentBuilderString("getTime");
        static final XContentBuilderString TIME_IN_MILLIS = new XContentBuilderString("time_in_millis");
        static final XContentBuilderString EXISTS_TOTAL = new XContentBuilderString("exists_total");
        static final XContentBuilderString EXISTS_TIME = new XContentBuilderString("exists_time");
        static final XContentBuilderString EXISTS_TIME_IN_MILLIS = new XContentBuilderString("exists_time_in_millis");
        static final XContentBuilderString MISSING_TOTAL = new XContentBuilderString("missing_total");
        static final XContentBuilderString MISSING_TIME = new XContentBuilderString("missing_time");
        static final XContentBuilderString MISSING_TIME_IN_MILLIS = new XContentBuilderString("missing_time_in_millis");
        static final XContentBuilderString CURRENT = new XContentBuilderString("current");
    }

    public static GetStats readGetStats(StreamInput in) throws IOException {
        GetStats stats = new GetStats();
        stats.readFrom(in);
        return stats;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        existsCount = in.readVLong();
        existsTimeInMillis = in.readVLong();
        missingCount = in.readVLong();
        missingTimeInMillis = in.readVLong();
        current = in.readVLong();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVLong(existsCount);
        out.writeVLong(existsTimeInMillis);
        out.writeVLong(missingCount);
        out.writeVLong(missingTimeInMillis);
        out.writeVLong(current);
    }
}
