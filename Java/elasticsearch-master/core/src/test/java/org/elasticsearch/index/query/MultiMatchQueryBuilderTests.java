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
import org.apache.lucene.queries.ExtendedCommonTermsQuery;
import org.apache.lucene.search.*;
import org.elasticsearch.common.lucene.all.AllTermQuery;
import org.elasticsearch.common.lucene.search.MultiPhrasePrefixQuery;
import org.elasticsearch.index.search.MatchQuery;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertBooleanSubQuery;
import static org.hamcrest.CoreMatchers.either;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;

public class MultiMatchQueryBuilderTests extends AbstractQueryTestCase<MultiMatchQueryBuilder> {

    @Override
    protected MultiMatchQueryBuilder doCreateTestQueryBuilder() {
        String fieldName = randomFrom(STRING_FIELD_NAME, INT_FIELD_NAME, DOUBLE_FIELD_NAME, BOOLEAN_FIELD_NAME, DATE_FIELD_NAME);
        if (fieldName.equals(DATE_FIELD_NAME)) {
            assumeTrue("test with date fields runs only when at least a type is registered", getCurrentTypes().length > 0);
        }
        // creates the query with random value and field name
        Object value;
        if (fieldName.equals(STRING_FIELD_NAME)) {
            value = getRandomQueryText();
        } else {
            value = getRandomValueForFieldName(fieldName);
        }
        MultiMatchQueryBuilder query = new MultiMatchQueryBuilder(value, fieldName);
        // field with random boost
        if (randomBoolean()) {
            query.field(fieldName, randomFloat() * 10);
        }
        // sets other parameters of the multi match query
        if (randomBoolean()) {
            query.type(randomFrom(MultiMatchQueryBuilder.Type.values()));
        }
        if (randomBoolean()) {
            query.operator(randomFrom(Operator.values()));
        }
        if (randomBoolean()) {
            query.analyzer(randomAnalyzer());
        }
        if (randomBoolean()) {
            query.slop(randomIntBetween(0, 5));
        }
        if (randomBoolean()) {
            query.fuzziness(randomFuzziness(fieldName));
        }
        if (randomBoolean()) {
            query.prefixLength(randomIntBetween(0, 5));
        }
        if (randomBoolean()) {
            query.maxExpansions(randomIntBetween(1, 5));
        }
        if (randomBoolean()) {
            query.minimumShouldMatch(randomMinimumShouldMatch());
        }
        if (randomBoolean()) {
            query.fuzzyRewrite(getRandomRewriteMethod());
        }
        if (randomBoolean()) {
            query.useDisMax(randomBoolean());
        }
        if (randomBoolean()) {
            query.tieBreaker(randomFloat());
        }
        if (randomBoolean()) {
            query.lenient(randomBoolean());
        }
        if (randomBoolean()) {
            query.cutoffFrequency((float) 10 / randomIntBetween(1, 100));
        }
        if (randomBoolean()) {
            query.zeroTermsQuery(randomFrom(MatchQuery.ZeroTermsQuery.values()));
        }
        // test with fields with boost and patterns delegated to the tests further below
        return query;
    }

    @Override
    protected Map<String, MultiMatchQueryBuilder> getAlternateVersions() {
        Map<String, MultiMatchQueryBuilder> alternateVersions = new HashMap<>();
        String query = "{\n" +
                "    \"multi_match\": {\n" +
                "        \"query\": \"foo bar\",\n" +
                "        \"fields\": \"myField\"\n" +
                "    }\n" +
                "}";
        alternateVersions.put(query, new MultiMatchQueryBuilder("foo bar", "myField"));
        return alternateVersions;
    }

    @Override
    protected void doAssertLuceneQuery(MultiMatchQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        // we rely on integration tests for deeper checks here
        assertThat(query, either(instanceOf(BoostQuery.class)).or(instanceOf(TermQuery.class)).or(instanceOf(AllTermQuery.class))
                .or(instanceOf(BooleanQuery.class)).or(instanceOf(DisjunctionMaxQuery.class))
                .or(instanceOf(FuzzyQuery.class)).or(instanceOf(MultiPhrasePrefixQuery.class))
                .or(instanceOf(MatchAllDocsQuery.class)).or(instanceOf(ExtendedCommonTermsQuery.class))
                .or(instanceOf(MatchNoDocsQuery.class)).or(instanceOf(PhraseQuery.class)));
    }

    public void testIllegaArguments() {
        try {
            new MultiMatchQueryBuilder(null, "field");
            fail("value must not be null");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            new MultiMatchQueryBuilder("value", (String[]) null);
            fail("initial fields must be supplied at construction time must not be null");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            new MultiMatchQueryBuilder("value", new String[]{""});
            fail("field names cannot be empty");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            new MultiMatchQueryBuilder("value", "field").type(null);
            fail("type must not be null");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testToQueryBoost() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        QueryShardContext shardContext = createShardContext();
        MultiMatchQueryBuilder multiMatchQueryBuilder = new MultiMatchQueryBuilder("test");
        multiMatchQueryBuilder.field(STRING_FIELD_NAME, 5f);
        Query query = multiMatchQueryBuilder.toQuery(shardContext);
        assertTermOrBoostQuery(query, STRING_FIELD_NAME, "test", 5f);

        multiMatchQueryBuilder = new MultiMatchQueryBuilder("test");
        multiMatchQueryBuilder.field(STRING_FIELD_NAME, 5f);
        multiMatchQueryBuilder.boost(2f);
        query = multiMatchQueryBuilder.toQuery(shardContext);
        assertThat(query, instanceOf(BoostQuery.class));
        BoostQuery boostQuery = (BoostQuery) query;
        assertThat(boostQuery.getBoost(), equalTo(2f));
        assertTermOrBoostQuery(boostQuery.getQuery(), STRING_FIELD_NAME, "test", 5f);
    }

    public void testToQueryMultipleTermsBooleanQuery() throws Exception {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        Query query = multiMatchQuery("test1 test2").field(STRING_FIELD_NAME).useDisMax(false).toQuery(createShardContext());
        assertThat(query, instanceOf(BooleanQuery.class));
        BooleanQuery bQuery = (BooleanQuery) query;
        assertThat(bQuery.clauses().size(), equalTo(2));
        assertThat(assertBooleanSubQuery(query, TermQuery.class, 0).getTerm(), equalTo(new Term(STRING_FIELD_NAME, "test1")));
        assertThat(assertBooleanSubQuery(query, TermQuery.class, 1).getTerm(), equalTo(new Term(STRING_FIELD_NAME, "test2")));
    }

    public void testToQueryMultipleFieldsBooleanQuery() throws Exception {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        Query query = multiMatchQuery("test").field(STRING_FIELD_NAME).field(STRING_FIELD_NAME_2).useDisMax(false).toQuery(createShardContext());
        assertThat(query, instanceOf(BooleanQuery.class));
        BooleanQuery bQuery = (BooleanQuery) query;
        assertThat(bQuery.clauses().size(), equalTo(2));
        assertThat(assertBooleanSubQuery(query, TermQuery.class, 0).getTerm(), equalTo(new Term(STRING_FIELD_NAME, "test")));
        assertThat(assertBooleanSubQuery(query, TermQuery.class, 1).getTerm(), equalTo(new Term(STRING_FIELD_NAME_2, "test")));
    }

    public void testToQueryMultipleFieldsDisMaxQuery() throws Exception {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        Query query = multiMatchQuery("test").field(STRING_FIELD_NAME).field(STRING_FIELD_NAME_2).useDisMax(true).toQuery(createShardContext());
        assertThat(query, instanceOf(DisjunctionMaxQuery.class));
        DisjunctionMaxQuery disMaxQuery = (DisjunctionMaxQuery) query;
        List<Query> disjuncts = disMaxQuery.getDisjuncts();
        assertThat(disjuncts.get(0), instanceOf(TermQuery.class));
        assertThat(((TermQuery) disjuncts.get(0)).getTerm(), equalTo(new Term(STRING_FIELD_NAME, "test")));
        assertThat(disjuncts.get(1), instanceOf(TermQuery.class));
        assertThat(((TermQuery) disjuncts.get(1)).getTerm(), equalTo(new Term(STRING_FIELD_NAME_2, "test")));
    }

    public void testToQueryFieldsWildcard() throws Exception {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        Query query = multiMatchQuery("test").field("mapped_str*").useDisMax(false).toQuery(createShardContext());
        assertThat(query, instanceOf(BooleanQuery.class));
        BooleanQuery bQuery = (BooleanQuery) query;
        assertThat(bQuery.clauses().size(), equalTo(2));
        assertThat(assertBooleanSubQuery(query, TermQuery.class, 0).getTerm(), equalTo(new Term(STRING_FIELD_NAME, "test")));
        assertThat(assertBooleanSubQuery(query, TermQuery.class, 1).getTerm(), equalTo(new Term(STRING_FIELD_NAME_2, "test")));
    }

    public void testFromJson() throws IOException {
        String json =
                "{\n" + 
                "  \"multi_match\" : {\n" + 
                "    \"query\" : \"quick brown fox\",\n" + 
                "    \"fields\" : [ \"title^1.0\", \"title.original^1.0\", \"title.shingles^1.0\" ],\n" + 
                "    \"type\" : \"most_fields\",\n" + 
                "    \"operator\" : \"OR\",\n" + 
                "    \"slop\" : 0,\n" + 
                "    \"prefix_length\" : 0,\n" + 
                "    \"max_expansions\" : 50,\n" + 
                "    \"lenient\" : false,\n" + 
                "    \"zero_terms_query\" : \"NONE\",\n" + 
                "    \"boost\" : 1.0\n" + 
                "  }\n" + 
                "}";

        MultiMatchQueryBuilder parsed = (MultiMatchQueryBuilder) parseQuery(json);
        checkGeneratedJson(json, parsed);

        assertEquals(json, "quick brown fox", parsed.value());
        assertEquals(json, 3, parsed.fields().size());
        assertEquals(json, MultiMatchQueryBuilder.Type.MOST_FIELDS, parsed.type());
        assertEquals(json, Operator.OR, parsed.operator());
    }
}
