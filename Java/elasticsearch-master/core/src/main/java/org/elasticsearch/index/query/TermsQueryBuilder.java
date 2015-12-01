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

package org.elasticsearch.index.query;

import org.apache.lucene.index.Term;
import org.apache.lucene.queries.TermsQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.lucene.BytesRefs;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.indices.cache.query.terms.TermsLookup;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A filter for a field based on several terms matching on any of them.
 */
public class TermsQueryBuilder extends AbstractQueryBuilder<TermsQueryBuilder> {

    public static final String NAME = "terms";

    static final TermsQueryBuilder PROTOTYPE = new TermsQueryBuilder("field", "value");

    private final String fieldName;
    private final List<Object> values;
    private final TermsLookup termsLookup;

    public TermsQueryBuilder(String fieldName, TermsLookup termsLookup) {
        this(fieldName, null, termsLookup);
    }

    /**
     * constructor used internally for serialization of both value / termslookup variants
     */
    TermsQueryBuilder(String fieldName, List<Object> values, TermsLookup termsLookup) {
        if (Strings.isEmpty(fieldName)) {
            throw new IllegalArgumentException("field name cannot be null.");
        }
        if (values == null && termsLookup == null) {
            throw new IllegalArgumentException("No value or termsLookup specified for terms query");
        }
        if (values != null && termsLookup != null) {
            throw new IllegalArgumentException("Both values and termsLookup specified for terms query");
        }
        this.fieldName = fieldName;
        this.values = values;
        this.termsLookup = termsLookup;
    }

    /**
     * A filter for a field based on several terms matching on any of them.
     *
     * @param fieldName The field name
     * @param values The terms
     */
    public TermsQueryBuilder(String fieldName, String... values) {
        this(fieldName, values != null ? Arrays.asList(values) : null);
    }

    /**
     * A filter for a field based on several terms matching on any of them.
     *
     * @param fieldName The field name
     * @param values The terms
     */
    public TermsQueryBuilder(String fieldName, int... values) {
        this(fieldName, values != null ? Arrays.stream(values).mapToObj(s -> s).collect(Collectors.toList()) : (Iterable<?>) null);
    }

    /**
     * A filter for a field based on several terms matching on any of them.
     *
     * @param fieldName The field name
     * @param values The terms
     */
    public TermsQueryBuilder(String fieldName, long... values) {
        this(fieldName, values != null ? Arrays.stream(values).mapToObj(s -> s).collect(Collectors.toList()) : (Iterable<?>) null);
    }

    /**
     * A filter for a field based on several terms matching on any of them.
     *
     * @param fieldName The field name
     * @param values The terms
     */
    public TermsQueryBuilder(String fieldName, float... values) {
        this(fieldName, values != null ? IntStream.range(0, values.length)
                           .mapToObj(i -> values[i]).collect(Collectors.toList()) : (Iterable<?>) null);
    }

    /**
     * A filter for a field based on several terms matching on any of them.
     *
     * @param fieldName The field name
     * @param values The terms
     */
    public TermsQueryBuilder(String fieldName, double... values) {
        this(fieldName, values != null ? Arrays.stream(values).mapToObj(s -> s).collect(Collectors.toList()) : (Iterable<?>) null);
    }

    /**
     * A filter for a field based on several terms matching on any of them.
     *
     * @param fieldName The field name
     * @param values The terms
     */
    public TermsQueryBuilder(String fieldName, Object... values) {
        this(fieldName, values != null ? Arrays.asList(values) : (Iterable<?>) null);
    }

    /**
     * A filter for a field based on several terms matching on any of them.
     *
     * @param fieldName The field name
     * @param values The terms
     */
    public TermsQueryBuilder(String fieldName, Iterable<?> values) {
        if (Strings.isEmpty(fieldName)) {
            throw new IllegalArgumentException("field name cannot be null.");
        }
        if (values == null) {
            throw new IllegalArgumentException("No value specified for terms query");
        }
        this.fieldName = fieldName;
        this.values = convertToBytesRefListIfStringList(values);
        this.termsLookup = null;
    }

    public String fieldName() {
        return this.fieldName;
    }

    public List<Object> values() {
        return convertToStringListIfBytesRefList(this.values);
    }

    public TermsLookup termsLookup() {
        return this.termsLookup;
    }

    /**
     * Same as {@link #convertToBytesRefIfString} but on Iterable.
     * @param objs the Iterable of input object
     * @return the same input or a list of {@link BytesRef} representation if input was a list of type string
     */
    private static List<Object> convertToBytesRefListIfStringList(Iterable<?> objs) {
        if (objs == null) {
            return null;
        }
        List<Object> newObjs = new ArrayList<>();
        for (Object obj : objs) {
            newObjs.add(convertToBytesRefIfString(obj));
        }
        return newObjs;
    }

    /**
     * Same as {@link #convertToStringIfBytesRef} but on Iterable.
     * @param objs the Iterable of input object
     * @return the same input or a list of utf8 string if input was a list of type {@link BytesRef}
     */
    private static List<Object> convertToStringListIfBytesRefList(Iterable<?> objs) {
        if (objs == null) {
            return null;
        }
        List<Object> newObjs = new ArrayList<>();
        for (Object obj : objs) {
            newObjs.add(convertToStringIfBytesRef(obj));
        }
        return newObjs;
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        if (this.termsLookup != null) {
            builder.startObject(fieldName);
            termsLookup.toXContent(builder, params);
            builder.endObject();
        } else {
            builder.field(fieldName, convertToStringListIfBytesRefList(values));
        }
        printBoostAndQueryName(builder);
        builder.endObject();
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        List<Object> terms;
        TermsLookup termsLookup = null;
        if (this.termsLookup != null) {
            termsLookup = new TermsLookup(this.termsLookup);
            if (termsLookup.index() == null) {
                termsLookup.index(context.index().name());
            }
            Client client = context.getClient();
            terms = fetch(termsLookup, client);
        } else {
            terms = values;
        }
        if (terms == null || terms.isEmpty()) {
            return Queries.newMatchNoDocsQuery();
        }
        return handleTermsQuery(terms, fieldName, context);
    }

    private List<Object> fetch(TermsLookup termsLookup, Client client) {
        List<Object> terms = new ArrayList<>();
        GetRequest getRequest = new GetRequest(termsLookup.index(), termsLookup.type(), termsLookup.id())
                .preference("_local").routing(termsLookup.routing());
        getRequest.copyContextAndHeadersFrom(SearchContext.current());
        final GetResponse getResponse = client.get(getRequest).actionGet();
        if (getResponse.isExists()) {
            List<Object> extractedValues = XContentMapValues.extractRawValues(termsLookup.path(), getResponse.getSourceAsMap());
            terms.addAll(extractedValues);
        }
        return terms;
    }

    private static Query handleTermsQuery(List<Object> terms, String fieldName, QueryShardContext context) {
        MappedFieldType fieldType = context.fieldMapper(fieldName);
        String indexFieldName;
        if (fieldType != null) {
            indexFieldName = fieldType.names().indexName();
        } else {
            indexFieldName = fieldName;
        }

        Query query;
        if (context.isFilter()) {
            if (fieldType != null) {
                query = fieldType.termsQuery(terms, context);
            } else {
                BytesRef[] filterValues = new BytesRef[terms.size()];
                for (int i = 0; i < filterValues.length; i++) {
                    filterValues[i] = BytesRefs.toBytesRef(terms.get(i));
                }
                query = new TermsQuery(indexFieldName, filterValues);
            }
        } else {
            BooleanQuery.Builder bq = new BooleanQuery.Builder();
            for (Object term : terms) {
                if (fieldType != null) {
                    bq.add(fieldType.termQuery(term, context), BooleanClause.Occur.SHOULD);
                } else {
                    bq.add(new TermQuery(new Term(indexFieldName, BytesRefs.toBytesRef(term))), BooleanClause.Occur.SHOULD);
                }
            }
            query = bq.build();
        }
        return query;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected TermsQueryBuilder doReadFrom(StreamInput in) throws IOException {
        String field = in.readString();
        TermsLookup lookup = null;
        if (in.readBoolean()) {
            lookup = TermsLookup.readTermsLookupFrom(in);
        }
        List<Object> values = (List<Object>) in.readGenericValue();
        return new TermsQueryBuilder(field, values, lookup);
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(fieldName);
        out.writeBoolean(termsLookup != null);
        if (termsLookup != null) {
            termsLookup.writeTo(out);
        }
        out.writeGenericValue(values);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(fieldName, values, termsLookup);
    }

    @Override
    protected boolean doEquals(TermsQueryBuilder other) {
        return Objects.equals(fieldName, other.fieldName) &&
                Objects.equals(values, other.values) &&
                Objects.equals(termsLookup, other.termsLookup);
    }
}
