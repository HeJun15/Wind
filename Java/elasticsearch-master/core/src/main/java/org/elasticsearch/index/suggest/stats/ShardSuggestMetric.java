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

package org.elasticsearch.index.suggest.stats;

import org.elasticsearch.common.metrics.CounterMetric;
import org.elasticsearch.common.metrics.MeanMetric;

import java.util.concurrent.TimeUnit;

/**
 *
 */
public final class ShardSuggestMetric {
    private final MeanMetric suggestMetric = new MeanMetric();
    private final CounterMetric currentMetric = new CounterMetric();

    /**
     * Called before suggest
     */
    public void preSuggest() {
        currentMetric.inc();
    }

    /**
     * Called after suggest
     * @param tookInNanos time of suggest used in nanos
     */
    public void postSuggest(long tookInNanos) {
        currentMetric.dec();
        suggestMetric.inc(tookInNanos);
    }

    /**
     * @return The current stats
     */
    public SuggestStats stats() {
        return new SuggestStats(suggestMetric.count(), TimeUnit.NANOSECONDS.toMillis(suggestMetric.sum()), currentMetric.count());
    }
}
