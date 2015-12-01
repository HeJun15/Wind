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

package org.elasticsearch.search.suggest.completion.context;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.mapper.ParseContext;
import org.elasticsearch.index.mapper.core.CompletionFieldMapper;

import java.io.IOException;
import java.util.*;

/**
 * A {@link ContextMapping} defines criteria that can be used to
 * filter and/or boost suggestions at query time for {@link CompletionFieldMapper}.
 *
 * Implementations have to define how contexts are parsed at query/index time
 */
public abstract class ContextMapping implements ToXContent {

    public static final String FIELD_TYPE = "type";
    public static final String FIELD_NAME = "name";
    protected final Type type;
    protected final String name;

    public enum Type {
        CATEGORY, GEO;

        public static Type fromString(String type) {
            if (type.equalsIgnoreCase("category")) {
                return CATEGORY;
            } else if (type.equalsIgnoreCase("geo")) {
                return GEO;
            } else {
                throw new IllegalArgumentException("No context type for [" + type + "]");
            }
        }
    }

    /**
     * Define a new context mapping of a specific type
     *
     * @param type type of context mapping, either {@link Type#CATEGORY} or {@link Type#GEO}
     * @param name name of context mapping
     */
    protected ContextMapping(Type type, String name) {
        this.type = type;
        this.name = name;
    }

    /**
     * @return the type name of the context
     */
    public Type type() {
        return type;
    }

    /**
     * @return the name/id of the context
     */
    public String name() {
        return name;
    }

    /**
     * Parses a set of index-time contexts.
     */
    public abstract Set<CharSequence> parseContext(ParseContext parseContext, XContentParser parser) throws IOException, ElasticsearchParseException;

    /**
     * Retrieves a set of context from a <code>document</code> at index-time.
     */
    protected abstract Set<CharSequence> parseContext(ParseContext.Document document);

    /**
     * Parses query contexts for this mapper
     */
    public abstract List<QueryContext> parseQueryContext(XContentParser parser) throws IOException, ElasticsearchParseException;

    /**
     * Implementations should add specific configurations
     * that need to be persisted
     */
    protected abstract XContentBuilder toInnerXContent(XContentBuilder builder, Params params) throws IOException;

    @Override
    public final XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.field(FIELD_NAME, name);
        builder.field(FIELD_TYPE, type.name());
        toInnerXContent(builder, params);
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContextMapping that = (ContextMapping) o;
        if (type != that.type) return false;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }

    @Override
    public String toString() {
        try {
            return toXContent(JsonXContent.contentBuilder(), ToXContent.EMPTY_PARAMS).string();
        } catch (IOException e) {
            return super.toString();
        }
    }

    public static class QueryContext {
        public final String context;
        public final int boost;
        public final boolean isPrefix;

        public QueryContext(String context, int boost, boolean isPrefix) {
            this.context = context;
            this.boost = boost;
            this.isPrefix = isPrefix;
        }

        @Override
        public String toString() {
            return "QueryContext{" +
                    "context='" + context + '\'' +
                    ", boost=" + boost +
                    ", isPrefix=" + isPrefix +
                    '}';
        }
    }
}
