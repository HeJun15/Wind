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
package org.elasticsearch.search.aggregations.support;

import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.aggregations.support.format.ValueFormat;
import org.elasticsearch.search.aggregations.support.format.ValueFormatter;
import org.elasticsearch.search.aggregations.support.format.ValueParser;

/**
 *
 */
public class ValuesSourceConfig<VS extends ValuesSource> {

    final Class<VS> valueSourceType;
    FieldContext fieldContext;
    SearchScript script;
    ValueType scriptValueType;
    boolean unmapped = false;
    String formatPattern;
    ValueFormat format = ValueFormat.RAW;
    Object missing;

    public ValuesSourceConfig(Class<VS> valueSourceType) {
        this.valueSourceType = valueSourceType;
    }

    public Class<VS> valueSourceType() {
        return valueSourceType;
    }

    public FieldContext fieldContext() {
        return fieldContext;
    }

    public SearchScript script() {
        return script;
    }

    public boolean unmapped() {
        return unmapped;
    }

    public boolean valid() {
        return fieldContext != null || script != null || unmapped;
    }

    public ValuesSourceConfig<VS> fieldContext(FieldContext fieldContext) {
        this.fieldContext = fieldContext;
        return this;
    }

    public ValuesSourceConfig<VS> script(SearchScript script) {
        this.script = script;
        return this;
    }

    public ValuesSourceConfig<VS> unmapped(boolean unmapped) {
        this.unmapped = unmapped;
        return this;
    }

    public ValueFormat format() {
        return format;
    }

    public ValueFormatter formatter() {
        return format.formatter();
    }

    public ValueParser parser() {
        return format.parser();
    }
}
