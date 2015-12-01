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
package org.elasticsearch.search.aggregations.bucket.geogrid;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;

import java.io.IOException;

/**
 * Creates an aggregation based on bucketing points into GeoHashes
 */
public class GeoHashGridBuilder extends AggregationBuilder<GeoHashGridBuilder> {


    private String field;
    private int precision = GeoHashGridParams.DEFAULT_PRECISION;
    private int requiredSize = GeoHashGridParams.DEFAULT_MAX_NUM_CELLS;
    private int shardSize = 0;

    /**
     * Sole constructor.
     */
    public GeoHashGridBuilder(String name) {
        super(name, InternalGeoHashGrid.TYPE.name());
    }

    /**
     * Set the field to use to get geo points.
     */
    public GeoHashGridBuilder field(String field) {
        this.field = field;
        return this;
    }

    /**
     * Set the geohash precision to use for this aggregation. The higher the
     * precision, the more fine-grained this aggregation will be.
     */
    public GeoHashGridBuilder precision(int precision) {
        this.precision = GeoHashGridParams.checkPrecision(precision);
        return this;
    }

    /**
     * Set the number of buckets to return.
     */
    public GeoHashGridBuilder size(int requiredSize) {
        this.requiredSize = requiredSize;
        return this;
    }

    /**
     * Expert: Set the number of buckets to get on each shard to improve
     * accuracy.
     */
    public GeoHashGridBuilder shardSize(int shardSize) {
        this.shardSize = shardSize;
        return this;
    }

    @Override
    protected XContentBuilder internalXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (field != null) {
            builder.field("field", field);
        }
        if (precision != GeoHashGridParams.DEFAULT_PRECISION) {
            builder.field(GeoHashGridParams.FIELD_PRECISION.getPreferredName(), precision);
        }
        if (requiredSize != GeoHashGridParams.DEFAULT_MAX_NUM_CELLS) {
            builder.field(GeoHashGridParams.FIELD_SIZE.getPreferredName(), requiredSize);
        }
        if (shardSize != 0) {
            builder.field(GeoHashGridParams.FIELD_SHARD_SIZE.getPreferredName(), shardSize);
        }

        return builder.endObject();
    }

}
