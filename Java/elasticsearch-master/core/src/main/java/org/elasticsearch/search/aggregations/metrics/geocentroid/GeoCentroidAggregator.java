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

package org.elasticsearch.search.aggregations.metrics.geocentroid;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.util.GeoHashUtils;
import org.apache.lucene.util.GeoUtils;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.lease.Releasables;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.LongArray;
import org.elasticsearch.index.fielddata.MultiGeoPointValues;
import org.elasticsearch.search.aggregations.Aggregator;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.LeafBucketCollector;
import org.elasticsearch.search.aggregations.LeafBucketCollectorBase;
import org.elasticsearch.search.aggregations.metrics.MetricsAggregator;
import org.elasticsearch.search.aggregations.metrics.geobounds.InternalGeoBounds;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;
import org.elasticsearch.search.aggregations.support.AggregationContext;
import org.elasticsearch.search.aggregations.support.ValuesSource;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregatorFactory;
import org.elasticsearch.search.aggregations.support.ValuesSourceConfig;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A geo metric aggregator that computes a geo-centroid from a {@code geo_point} type field
 */
public final class GeoCentroidAggregator extends MetricsAggregator {
    private final ValuesSource.GeoPoint valuesSource;
    LongArray centroids;
    LongArray counts;

    protected GeoCentroidAggregator(String name, AggregationContext aggregationContext, Aggregator parent,
                                    ValuesSource.GeoPoint valuesSource, List<PipelineAggregator> pipelineAggregators,
                                    Map<String, Object> metaData) throws IOException {
        super(name, aggregationContext, parent, pipelineAggregators, metaData);
        this.valuesSource = valuesSource;
        if (valuesSource != null) {
            final BigArrays bigArrays = context.bigArrays();
            centroids = bigArrays.newLongArray(1, true);
            counts = bigArrays.newLongArray(1, true);
        }
    }

    @Override
    public LeafBucketCollector getLeafCollector(LeafReaderContext ctx, LeafBucketCollector sub) throws IOException {
        if (valuesSource == null) {
            return LeafBucketCollector.NO_OP_COLLECTOR;
        }
        final BigArrays bigArrays = context.bigArrays();
        final MultiGeoPointValues values = valuesSource.geoPointValues(ctx);
        return new LeafBucketCollectorBase(sub, values) {
            @Override
            public void collect(int doc, long bucket) throws IOException {
                centroids = bigArrays.grow(centroids, bucket + 1);
                counts = bigArrays.grow(counts, bucket + 1);

                values.setDocument(doc);
                final int valueCount = values.count();
                if (valueCount > 0) {
                    double[] pt = new double[2];
                    // get the previously accumulated number of counts
                    long prevCounts = counts.get(bucket);
                    // increment by the number of points for this document
                    counts.increment(bucket, valueCount);
                    // get the previous GeoPoint if a moving avg was computed
                    if (prevCounts > 0) {
                        final GeoPoint centroid = GeoPoint.fromIndexLong(centroids.get(bucket));
                        pt[0] = centroid.lon();
                        pt[1] = centroid.lat();
                    }
                    // update the moving average
                    for (int i = 0; i < valueCount; ++i) {
                        GeoPoint value = values.valueAt(i);
                        pt[0] = pt[0] + (value.getLon() - pt[0]) / ++prevCounts;
                        pt[1] = pt[1] + (value.getLat() - pt[1]) / prevCounts;
                    }
                    centroids.set(bucket, GeoUtils.mortonHash(pt[0], pt[1]));
                }
            }
        };
    }

    @Override
    public InternalAggregation buildAggregation(long bucket) {
        if (valuesSource == null || bucket >= centroids.size()) {
            return buildEmptyAggregation();
        }
        final long bucketCount = counts.get(bucket);
        final GeoPoint bucketCentroid = (bucketCount > 0) ? GeoPoint.fromIndexLong(centroids.get(bucket)) :
                new GeoPoint(Double.NaN, Double.NaN);
        return new InternalGeoCentroid(name, bucketCentroid , bucketCount, pipelineAggregators(), metaData());
    }

    @Override
    public InternalAggregation buildEmptyAggregation() {
        return new InternalGeoCentroid(name, null, 0l, pipelineAggregators(), metaData());
    }

    @Override
    public void doClose() {
        Releasables.close(centroids, counts);
    }

    public static class Factory extends ValuesSourceAggregatorFactory.LeafOnly<ValuesSource.GeoPoint> {
        protected Factory(String name, ValuesSourceConfig<ValuesSource.GeoPoint> config) {
            super(name, InternalGeoBounds.TYPE.name(), config);
        }

        @Override
        protected Aggregator createUnmapped(AggregationContext aggregationContext, Aggregator parent,
                                            List<PipelineAggregator> pipelineAggregators, Map<String, Object> metaData) throws IOException {
            return new GeoCentroidAggregator(name, aggregationContext, parent, null, pipelineAggregators, metaData);
        }

        @Override
        protected Aggregator doCreateInternal(ValuesSource.GeoPoint valuesSource, AggregationContext aggregationContext, Aggregator parent,
                boolean collectsFromSingleBucket, List<PipelineAggregator> pipelineAggregators, Map<String, Object> metaData)
                throws IOException {
            return new GeoCentroidAggregator(name, aggregationContext, parent, valuesSource, pipelineAggregators, metaData);
        }
    }
}
