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

import org.apache.lucene.search.*;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;

import org.hamcrest.Matchers;

import java.io.IOException;
import java.util.*;

import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;

public class BoolQueryBuilderTests extends AbstractQueryTestCase<BoolQueryBuilder> {
    @Override
    protected BoolQueryBuilder doCreateTestQueryBuilder() {
        BoolQueryBuilder query = new BoolQueryBuilder();
        if (randomBoolean()) {
            query.adjustPureNegative(randomBoolean());
        }
        if (randomBoolean()) {
            query.disableCoord(randomBoolean());
        }
        if (randomBoolean()) {
            query.minimumNumberShouldMatch(randomMinimumShouldMatch());
        }
        int mustClauses = randomIntBetween(0, 3);
        for (int i = 0; i < mustClauses; i++) {
            query.must(RandomQueryBuilder.createQuery(random()));
        }
        int mustNotClauses = randomIntBetween(0, 3);
        for (int i = 0; i < mustNotClauses; i++) {
            query.mustNot(RandomQueryBuilder.createQuery(random()));
        }
        int shouldClauses = randomIntBetween(0, 3);
        for (int i = 0; i < shouldClauses; i++) {
            query.should(RandomQueryBuilder.createQuery(random()));
        }
        int filterClauses = randomIntBetween(0, 3);
        for (int i = 0; i < filterClauses; i++) {
            query.filter(RandomQueryBuilder.createQuery(random()));
        }
        return query;
    }

    @Override
    protected void doAssertLuceneQuery(BoolQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        if (!queryBuilder.hasClauses()) {
            assertThat(query, instanceOf(MatchAllDocsQuery.class));
        } else {
            List<BooleanClause> clauses = new ArrayList<>();
            clauses.addAll(getBooleanClauses(queryBuilder.must(), BooleanClause.Occur.MUST, context));
            clauses.addAll(getBooleanClauses(queryBuilder.mustNot(), BooleanClause.Occur.MUST_NOT, context));
            clauses.addAll(getBooleanClauses(queryBuilder.should(), BooleanClause.Occur.SHOULD, context));
            clauses.addAll(getBooleanClauses(queryBuilder.filter(), BooleanClause.Occur.FILTER, context));

            if (clauses.isEmpty()) {
                assertThat(query, instanceOf(MatchAllDocsQuery.class));
            } else {
                assertThat(query, instanceOf(BooleanQuery.class));
                BooleanQuery booleanQuery = (BooleanQuery) query;
                assertThat(booleanQuery.isCoordDisabled(), equalTo(queryBuilder.disableCoord()));
                if (queryBuilder.adjustPureNegative()) {
                    boolean isNegative = true;
                    for (BooleanClause clause : clauses) {
                        if (clause.isProhibited() == false) {
                            isNegative = false;
                            break;
                        }
                    }
                    if (isNegative) {
                        clauses.add(new BooleanClause(new MatchAllDocsQuery(), BooleanClause.Occur.MUST));
                    }
                }
                assertThat(booleanQuery.clauses().size(), equalTo(clauses.size()));
                Iterator<BooleanClause> clauseIterator = clauses.iterator();
                for (BooleanClause booleanClause : booleanQuery.getClauses()) {
                    assertThat(booleanClause, instanceOf(clauseIterator.next().getClass()));
                }
            }
        }
    }

    private static List<BooleanClause> getBooleanClauses(List<QueryBuilder> queryBuilders, BooleanClause.Occur occur, QueryShardContext context) throws IOException {
        List<BooleanClause> clauses = new ArrayList<>();
        for (QueryBuilder query : queryBuilders) {
            Query innerQuery = query.toQuery(context);
            if (innerQuery != null) {
                clauses.add(new BooleanClause(innerQuery, occur));
            }
        }
        return clauses;
    }

    @Override
    protected Map<String, BoolQueryBuilder> getAlternateVersions() {
        Map<String, BoolQueryBuilder> alternateVersions = new HashMap<>();
        BoolQueryBuilder tempQueryBuilder = createTestQueryBuilder();
        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();
        String contentString = "{\n" +
                "    \"bool\" : {\n";
        if (tempQueryBuilder.must().size() > 0) {
            QueryBuilder must = tempQueryBuilder.must().get(0);
            contentString += "must: " + must.toString() + ",";
            expectedQuery.must(must);
        }
        if (tempQueryBuilder.mustNot().size() > 0) {
            QueryBuilder mustNot = tempQueryBuilder.mustNot().get(0);
            contentString += (randomBoolean() ? "must_not: " : "mustNot: ") + mustNot.toString() + ",";
            expectedQuery.mustNot(mustNot);
        }
        if (tempQueryBuilder.should().size() > 0) {
            QueryBuilder should = tempQueryBuilder.should().get(0);
            contentString += "should: " + should.toString() + ",";
            expectedQuery.should(should);
        }
        if (tempQueryBuilder.filter().size() > 0) {
            QueryBuilder filter = tempQueryBuilder.filter().get(0);
            contentString += "filter: " + filter.toString() + ",";
            expectedQuery.filter(filter);
        }
        contentString = contentString.substring(0, contentString.length() - 1);
        contentString += "    }    \n" + "}";
        alternateVersions.put(contentString, expectedQuery);
        return alternateVersions;
    }

    public void testIllegalArguments() {
        BoolQueryBuilder booleanQuery = new BoolQueryBuilder();

        try {
            booleanQuery.must(null);
            fail("cannot be null");
        } catch (IllegalArgumentException e) {
        }

        try {
            booleanQuery.mustNot(null);
            fail("cannot be null");
        } catch (IllegalArgumentException e) {
        }

        try {
            booleanQuery.filter(null);
            fail("cannot be null");
        } catch (IllegalArgumentException e) {
        }

        try {
            booleanQuery.should(null);
            fail("cannot be null");
        } catch (IllegalArgumentException e) {
        }
    }

    // https://github.com/elasticsearch/elasticsearch/issues/7240
    public void testEmptyBooleanQuery() throws Exception {
        XContentBuilder contentBuilder = XContentFactory.contentBuilder(randomFrom(XContentType.values()));
        BytesReference query = contentBuilder.startObject().startObject("bool").endObject().endObject().bytes();
        Query parsedQuery = parseQuery(query).toQuery(createShardContext());
        assertThat(parsedQuery, Matchers.instanceOf(MatchAllDocsQuery.class));
    }

    public void testDefaultMinShouldMatch() throws Exception {
        // Queries have a minShouldMatch of 0
        BooleanQuery bq = (BooleanQuery) parseQuery(boolQuery().must(termQuery("foo", "bar")).buildAsBytes()).toQuery(createShardContext());
        assertEquals(0, bq.getMinimumNumberShouldMatch());

        bq = (BooleanQuery) parseQuery(boolQuery().should(termQuery("foo", "bar")).buildAsBytes()).toQuery(createShardContext());
        assertEquals(0, bq.getMinimumNumberShouldMatch());

        // Filters have a minShouldMatch of 0/1
        ConstantScoreQuery csq = (ConstantScoreQuery) parseQuery(constantScoreQuery(boolQuery().must(termQuery("foo", "bar"))).buildAsBytes()).toQuery(createShardContext());
        bq = (BooleanQuery) csq.getQuery();
        assertEquals(0, bq.getMinimumNumberShouldMatch());

        csq = (ConstantScoreQuery) parseQuery(constantScoreQuery(boolQuery().should(termQuery("foo", "bar"))).buildAsBytes()).toQuery(createShardContext());
        bq = (BooleanQuery) csq.getQuery();
        assertEquals(1, bq.getMinimumNumberShouldMatch());
    }

    public void testMinShouldMatchFilterWithoutShouldClauses() throws Exception {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.filter(new BoolQueryBuilder().must(new MatchAllQueryBuilder()));
        Query query = boolQueryBuilder.toQuery(createShardContext());
        assertThat(query, instanceOf(BooleanQuery.class));
        BooleanQuery booleanQuery = (BooleanQuery) query;
        assertThat(booleanQuery.getMinimumNumberShouldMatch(), equalTo(0));
        assertThat(booleanQuery.clauses().size(), equalTo(1));
        BooleanClause booleanClause = booleanQuery.clauses().get(0);
        assertThat(booleanClause.getOccur(), equalTo(BooleanClause.Occur.FILTER));
        assertThat(booleanClause.getQuery(), instanceOf(BooleanQuery.class));
        BooleanQuery innerBooleanQuery = (BooleanQuery) booleanClause.getQuery();
        //we didn't set minimum should match initially, there are no should clauses so it should be 0
        assertThat(innerBooleanQuery.getMinimumNumberShouldMatch(), equalTo(0));
        assertThat(innerBooleanQuery.clauses().size(), equalTo(1));
        BooleanClause innerBooleanClause = innerBooleanQuery.clauses().get(0);
        assertThat(innerBooleanClause.getOccur(), equalTo(BooleanClause.Occur.MUST));
        assertThat(innerBooleanClause.getQuery(), instanceOf(MatchAllDocsQuery.class));
    }

    public void testMinShouldMatchFilterWithShouldClauses() throws Exception {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.filter(new BoolQueryBuilder().must(new MatchAllQueryBuilder()).should(new MatchAllQueryBuilder()));
        Query query = boolQueryBuilder.toQuery(createShardContext());
        assertThat(query, instanceOf(BooleanQuery.class));
        BooleanQuery booleanQuery = (BooleanQuery) query;
        assertThat(booleanQuery.getMinimumNumberShouldMatch(), equalTo(0));
        assertThat(booleanQuery.clauses().size(), equalTo(1));
        BooleanClause booleanClause = booleanQuery.clauses().get(0);
        assertThat(booleanClause.getOccur(), equalTo(BooleanClause.Occur.FILTER));
        assertThat(booleanClause.getQuery(), instanceOf(BooleanQuery.class));
        BooleanQuery innerBooleanQuery = (BooleanQuery) booleanClause.getQuery();
        //we didn't set minimum should match initially, but there are should clauses so it should be 1
        assertThat(innerBooleanQuery.getMinimumNumberShouldMatch(), equalTo(1));
        assertThat(innerBooleanQuery.clauses().size(), equalTo(2));
        BooleanClause innerBooleanClause1 = innerBooleanQuery.clauses().get(0);
        assertThat(innerBooleanClause1.getOccur(), equalTo(BooleanClause.Occur.MUST));
        assertThat(innerBooleanClause1.getQuery(), instanceOf(MatchAllDocsQuery.class));
        BooleanClause innerBooleanClause2 = innerBooleanQuery.clauses().get(1);
        assertThat(innerBooleanClause2.getOccur(), equalTo(BooleanClause.Occur.SHOULD));
        assertThat(innerBooleanClause2.getQuery(), instanceOf(MatchAllDocsQuery.class));
    }

    public void testFromJson() throws IOException {
        String query =
                "{" +
                "\"bool\" : {" +
                "  \"must\" : [ {" +
                "    \"term\" : {" +
                "      \"user\" : {" +
                "        \"value\" : \"kimchy\"," +
                "        \"boost\" : 1.0" +
                "      }" +
                "    }" +
                "  } ]," +
                "  \"filter\" : [ {" +
                "    \"term\" : {" +
                "      \"tag\" : {" +
                "        \"value\" : \"tech\"," +
                "        \"boost\" : 1.0" +
                "      }" +
                "    }" +
                "  } ]," +
                "  \"must_not\" : [ {" +
                "    \"range\" : {" +
                "      \"age\" : {" +
                "        \"from\" : 10," +
                "        \"to\" : 20," +
                "        \"include_lower\" : true," +
                "        \"include_upper\" : true," +
                "        \"boost\" : 1.0" +
                "      }" +
                "    }" +
                "  } ]," +
                "  \"should\" : [ {" +
                "    \"term\" : {" +
                "      \"tag\" : {" +
                "        \"value\" : \"wow\"," +
                "        \"boost\" : 1.0" +
                "      }" +
                "    }" +
                "  }, {" +
                "    \"term\" : {" +
                "      \"tag\" : {" +
                "        \"value\" : \"elasticsearch\"," +
                "        \"boost\" : 1.0" +
                "      }" +
                "    }" +
                "  } ]," +
                "  \"disable_coord\" : false," +
                "  \"adjust_pure_negative\" : true," +
                "  \"minimum_should_match\" : \"23\"," +
                "  \"boost\" : 42.0" +
                "}" +
              "}";

        BoolQueryBuilder queryBuilder = (BoolQueryBuilder) parseQuery(query);
        checkGeneratedJson(query, queryBuilder);

        assertEquals(query, 42, queryBuilder.boost, 0.00001);
        assertEquals(query, "23", queryBuilder.minimumShouldMatch());
        assertEquals(query, "kimchy", ((TermQueryBuilder)queryBuilder.must().get(0)).value());
    }
}
