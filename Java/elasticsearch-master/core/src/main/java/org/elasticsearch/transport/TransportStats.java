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

package org.elasticsearch.transport;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;

import java.io.IOException;

public class TransportStats implements Streamable, ToXContent {

    private long serverOpen;
    private long rxCount;
    private long rxSize;
    private long txCount;
    private long txSize;

    TransportStats() {

    }

    public TransportStats(long serverOpen, long rxCount, long rxSize, long txCount, long txSize) {
        this.serverOpen = serverOpen;
        this.rxCount = rxCount;
        this.rxSize = rxSize;
        this.txCount = txCount;
        this.txSize = txSize;
    }

    public long serverOpen() {
        return this.serverOpen;
    }

    public long getServerOpen() {
        return serverOpen();
    }

    public long rxCount() {
        return rxCount;
    }

    public long getRxCount() {
        return rxCount();
    }

    public ByteSizeValue rxSize() {
        return new ByteSizeValue(rxSize);
    }

    public ByteSizeValue getRxSize() {
        return rxSize();
    }

    public long txCount() {
        return txCount;
    }

    public long getTxCount() {
        return txCount();
    }

    public ByteSizeValue txSize() {
        return new ByteSizeValue(txSize);
    }

    public ByteSizeValue getTxSize() {
        return txSize();
    }

    public static TransportStats readTransportStats(StreamInput in) throws IOException {
        TransportStats stats = new TransportStats();
        stats.readFrom(in);
        return stats;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        serverOpen = in.readVLong();
        rxCount = in.readVLong();
        rxSize = in.readVLong();
        txCount = in.readVLong();
        txSize = in.readVLong();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVLong(serverOpen);
        out.writeVLong(rxCount);
        out.writeVLong(rxSize);
        out.writeVLong(txCount);
        out.writeVLong(txSize);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(Fields.TRANSPORT);
        builder.field(Fields.SERVER_OPEN, serverOpen);
        builder.field(Fields.RX_COUNT, rxCount);
        builder.byteSizeField(Fields.RX_SIZE_IN_BYTES, Fields.RX_SIZE, rxSize);
        builder.field(Fields.TX_COUNT, txCount);
        builder.byteSizeField(Fields.TX_SIZE_IN_BYTES, Fields.TX_SIZE, txSize);
        builder.endObject();
        return builder;
    }

    static final class Fields {
        static final XContentBuilderString TRANSPORT = new XContentBuilderString("transport");
        static final XContentBuilderString SERVER_OPEN = new XContentBuilderString("server_open");
        static final XContentBuilderString RX_COUNT = new XContentBuilderString("rx_count");
        static final XContentBuilderString RX_SIZE = new XContentBuilderString("rx_size");
        static final XContentBuilderString RX_SIZE_IN_BYTES = new XContentBuilderString("rx_size_in_bytes");
        static final XContentBuilderString TX_COUNT = new XContentBuilderString("tx_count");
        static final XContentBuilderString TX_SIZE = new XContentBuilderString("tx_size");
        static final XContentBuilderString TX_SIZE_IN_BYTES = new XContentBuilderString("tx_size_in_bytes");
    }
}