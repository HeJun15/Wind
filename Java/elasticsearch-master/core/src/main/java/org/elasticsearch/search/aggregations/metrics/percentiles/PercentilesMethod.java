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

package org.elasticsearch.search.aggregations.metrics.percentiles;


/**
 * An enum representing the methods for calculating percentiles
 */
public enum PercentilesMethod {
    /**
     * The TDigest method for calculating percentiles
     */
    TDIGEST("tdigest"),
    /**
     * The HDRHistogram method of calculating percentiles
     */
    HDR("hdr");

    private String name;

    private PercentilesMethod(String name) {
        this.name = name;
    }

    /**
     * @return the name of the method
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the {@link PercentilesMethod} for this method name. returns
     * <code>null</code> if no {@link PercentilesMethod} exists for the name.
     */
    public static PercentilesMethod resolveFromName(String name) {
        for (PercentilesMethod method : values()) {
            if (method.name.equalsIgnoreCase(name)) {
                return method;
            }
        }
        return null;
    }
}