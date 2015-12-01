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

import org.apache.lucene.queries.ExtendedCommonTermsQuery;
import org.apache.lucene.search.Query;

import java.io.IOException;

import static org.elasticsearch.index.query.QueryBuilders.commonTermsQuery;
import static org.elasticsearch.test.StreamsUtils.copyToStringFromClasspath;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;

public class CommonTermsQueryBuilderTests extends AbstractQueryTestCase<CommonTermsQueryBuilder> {

    @Override
    protected CommonTermsQueryBuilder doCreateTestQueryBuilder() {
        CommonTermsQueryBuilder query;

        // mapped or unmapped field
        String text = randomAsciiOfLengthBetween(1, 10);
        if (randomBoolean()) {
            query = new CommonTermsQueryBuilder(STRING_FIELD_NAME, text);
        } else {
            query = new CommonTermsQueryBuilder(randomAsciiOfLengthBetween(1, 10), text);
        }

        if (randomBoolean()) {
            query.cutoffFrequency(randomIntBetween(1, 10));
        }

        if (randomBoolean()) {
            query.lowFreqOperator(randomFrom(Operator.values()));
        }

        // number of low frequency terms that must match
        if (randomBoolean()) {
            query.lowFreqMinimumShouldMatch("" + randomIntBetween(1, 5));
        }

        if (randomBoolean()) {
            query.highFreqOperator(randomFrom(Operator.values()));
        }

        // number of high frequency terms that must match
        if (randomBoolean()) {
            query.highFreqMinimumShouldMatch("" + randomIntBetween(1, 5));
        }

        if (randomBoolean()) {
            query.analyzer(randomAnalyzer());
        }

        if (randomBoolean()) {
            query.disableCoord(randomBoolean());
        }
        return query;
    }

    @Override
    protected void doAssertLuceneQuery(CommonTermsQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        assertThat(query, instanceOf(ExtendedCommonTermsQuery.class));
        ExtendedCommonTermsQuery extendedCommonTermsQuery = (ExtendedCommonTermsQuery) query;
        assertThat(extendedCommonTermsQuery.getHighFreqMinimumNumberShouldMatchSpec(), equalTo(queryBuilder.highFreqMinimumShouldMatch()));
        assertThat(extendedCommonTermsQuery.getLowFreqMinimumNumberShouldMatchSpec(), equalTo(queryBuilder.lowFreqMinimumShouldMatch()));
    }

    public void testIllegalArguments() {
        try {
            if (randomBoolean()) {
                new CommonTermsQueryBuilder(null, "text");
            } else {
                new CommonTermsQueryBuilder("", "text");
            }
            fail("must be non null");
        } catch (IllegalArgumentException e) {
            // okay
        }

        try {
            new CommonTermsQueryBuilder("fieldName", null);
            fail("must be non null");
        } catch (IllegalArgumentException e) {
            // okay
        }
    }

    public void testFromJson() throws IOException {
        String query =
                "{\n" + 
                "  \"common\" : {\n" + 
                "    \"body\" : {\n" + 
                "      \"query\" : \"nelly the elephant not as a cartoon\",\n" + 
                "      \"disable_coord\" : true,\n" + 
                "      \"high_freq_operator\" : \"AND\",\n" + 
                "      \"low_freq_operator\" : \"OR\",\n" + 
                "      \"cutoff_frequency\" : 0.001,\n" + 
                "      \"minimum_should_match\" : {\n" + 
                "        \"low_freq\" : \"2\",\n" + 
                "        \"high_freq\" : \"3\"\n" + 
                "      },\n" + 
                "      \"boost\" : 42.0\n" + 
                "    }\n" + 
                "  }\n" + 
                "}";

        CommonTermsQueryBuilder queryBuilder = (CommonTermsQueryBuilder) parseQuery(query);
        checkGeneratedJson(query, queryBuilder);

        assertEquals(query, 42, queryBuilder.boost, 0.00001);
        assertEquals(query, 0.001, queryBuilder.cutoffFrequency(), 0.0001);
        assertEquals(query, Operator.OR, queryBuilder.lowFreqOperator());
        assertEquals(query, Operator.AND, queryBuilder.highFreqOperator());
        assertEquals(query, "nelly the elephant not as a cartoon", queryBuilder.value());
    }
    
    public void testNoTermsFromQueryString() throws IOException {
        CommonTermsQueryBuilder builder = new CommonTermsQueryBuilder(STRING_FIELD_NAME, "");
        QueryShardContext context = createShardContext();
        context.setAllowUnmappedFields(true);
        assertNull(builder.toQuery(context));
    }

    public void testCommonTermsQuery1() throws IOException {
        String query = copyToStringFromClasspath("/org/elasticsearch/index/query/commonTerms-query1.json");
        Query parsedQuery = parseQuery(query).toQuery(createShardContext());
        assertThat(parsedQuery, instanceOf(ExtendedCommonTermsQuery.class));
        ExtendedCommonTermsQuery ectQuery = (ExtendedCommonTermsQuery) parsedQuery;
        assertThat(ectQuery.getHighFreqMinimumNumberShouldMatchSpec(), nullValue());
        assertThat(ectQuery.getLowFreqMinimumNumberShouldMatchSpec(), equalTo("2"));
    }

    public void testCommonTermsQuery2() throws IOException {
        String query = copyToStringFromClasspath("/org/elasticsearch/index/query/commonTerms-query2.json");
        Query parsedQuery = parseQuery(query).toQuery(createShardContext());
        assertThat(parsedQuery, instanceOf(ExtendedCommonTermsQuery.class));
        ExtendedCommonTermsQuery ectQuery = (ExtendedCommonTermsQuery) parsedQuery;
        assertThat(ectQuery.getHighFreqMinimumNumberShouldMatchSpec(), equalTo("50%"));
        assertThat(ectQuery.getLowFreqMinimumNumberShouldMatchSpec(), equalTo("5<20%"));
    }

    public void testCommonTermsQuery3() throws IOException {
        String query = copyToStringFromClasspath("/org/elasticsearch/index/query/commonTerms-query3.json");
        Query parsedQuery = parseQuery(query).toQuery(createShardContext());
        assertThat(parsedQuery, instanceOf(ExtendedCommonTermsQuery.class));
        ExtendedCommonTermsQuery ectQuery = (ExtendedCommonTermsQuery) parsedQuery;
        assertThat(ectQuery.getHighFreqMinimumNumberShouldMatchSpec(), nullValue());
        assertThat(ectQuery.getLowFreqMinimumNumberShouldMatchSpec(), equalTo("2"));
    }

    // see #11730
    public void testCommonTermsQuery4() throws IOException {
        boolean disableCoord = randomBoolean();
        Query parsedQuery = parseQuery(commonTermsQuery("field", "text").disableCoord(disableCoord).buildAsBytes()).toQuery(createShardContext());
        assertThat(parsedQuery, instanceOf(ExtendedCommonTermsQuery.class));
        ExtendedCommonTermsQuery ectQuery = (ExtendedCommonTermsQuery) parsedQuery;
        assertThat(ectQuery.isCoordDisabled(), equalTo(disableCoord));
    }
}
