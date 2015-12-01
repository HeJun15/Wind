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
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.unit.Fuzziness;
import org.hamcrest.Matchers;

import java.io.IOException;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

public class FuzzyQueryBuilderTests extends AbstractQueryTestCase<FuzzyQueryBuilder> {

    @Override
    protected FuzzyQueryBuilder doCreateTestQueryBuilder() {
        Tuple<String, Object> fieldAndValue = getRandomFieldNameAndValue();
        FuzzyQueryBuilder query = new FuzzyQueryBuilder(fieldAndValue.v1(), fieldAndValue.v2());
        if (randomBoolean()) {
            query.fuzziness(randomFuzziness(query.fieldName()));
        }
        if (randomBoolean()) {
            query.prefixLength(randomIntBetween(0, 10));
        }
        if (randomBoolean()) {
            query.maxExpansions(randomIntBetween(1, 10));
        }
        if (randomBoolean()) {
            query.transpositions(randomBoolean());
        }
        if (randomBoolean()) {
            query.rewrite(getRandomRewriteMethod());
        }
        return query;
    }

    @Override
    protected void doAssertLuceneQuery(FuzzyQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        if (isNumericFieldName(queryBuilder.fieldName()) || queryBuilder.fieldName().equals(DATE_FIELD_NAME)) {
            assertThat(query, instanceOf(NumericRangeQuery.class));
        } else {
            assertThat(query, instanceOf(FuzzyQuery.class));
        }
    }

    public void testIllegalArguments() {
        try {
            new FuzzyQueryBuilder(null, "text");
            fail("must not be null");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            new FuzzyQueryBuilder("", "text");
            fail("must not be empty");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            new FuzzyQueryBuilder("field", null);
            fail("must not be null");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testUnsupportedFuzzinessForStringType() throws IOException {
        QueryShardContext context = createShardContext();
        context.setAllowUnmappedFields(true);

        FuzzyQueryBuilder fuzzyQueryBuilder = new FuzzyQueryBuilder(STRING_FIELD_NAME, "text");
        fuzzyQueryBuilder.fuzziness(Fuzziness.build(randomFrom("a string which is not auto", "3h", "200s")));

        try {
            fuzzyQueryBuilder.toQuery(context);
            fail("should have failed with NumberFormatException");
        } catch (NumberFormatException e) {
            assertThat(e.getMessage(), Matchers.containsString("For input string"));
        }
    }

    public void testToQueryWithStringField() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"fuzzy\":{\n" +
                "        \"" + STRING_FIELD_NAME + "\":{\n" +
                "            \"value\":\"sh\",\n" +
                "            \"fuzziness\": \"AUTO\",\n" +
                "            \"prefix_length\":1,\n" +
                "            \"boost\":2.0\n" +
                "        }\n" +
                "    }\n" +
                "}";
        Query parsedQuery = parseQuery(query).toQuery(createShardContext());
        assertThat(parsedQuery, instanceOf(BoostQuery.class));
        BoostQuery boostQuery = (BoostQuery) parsedQuery;
        assertThat(boostQuery.getBoost(), equalTo(2.0f));
        assertThat(boostQuery.getQuery(), instanceOf(FuzzyQuery.class));
        FuzzyQuery fuzzyQuery = (FuzzyQuery) boostQuery.getQuery();
        assertThat(fuzzyQuery.getTerm(), equalTo(new Term(STRING_FIELD_NAME, "sh")));
        assertThat(fuzzyQuery.getMaxEdits(), equalTo(Fuzziness.AUTO.asDistance("sh")));
        assertThat(fuzzyQuery.getPrefixLength(), equalTo(1));

    }

    public void testToQueryWithNumericField() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"fuzzy\":{\n" +
                "        \"" + INT_FIELD_NAME + "\":{\n" +
                "            \"value\":12,\n" +
                "            \"fuzziness\":5\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        Query parsedQuery = parseQuery(query).toQuery(createShardContext());
        assertThat(parsedQuery, instanceOf(NumericRangeQuery.class));
        NumericRangeQuery fuzzyQuery = (NumericRangeQuery) parsedQuery;
        assertThat(fuzzyQuery.getMin().longValue(), equalTo(7l));
        assertThat(fuzzyQuery.getMax().longValue(), equalTo(17l));
    }

    public void testFromJson() throws IOException {
        String json =
                "{\n" + 
                "  \"fuzzy\" : {\n" + 
                "    \"user\" : {\n" + 
                "      \"value\" : \"ki\",\n" + 
                "      \"fuzziness\" : \"2\",\n" + 
                "      \"prefix_length\" : 0,\n" + 
                "      \"max_expansions\" : 100,\n" + 
                "      \"transpositions\" : false,\n" + 
                "      \"boost\" : 42.0\n" + 
                "    }\n" + 
                "  }\n" + 
                "}";
        FuzzyQueryBuilder parsed = (FuzzyQueryBuilder) parseQuery(json);
        checkGeneratedJson(json, parsed);
        assertEquals(json, 42.0, parsed.boost(), 0.00001);
        assertEquals(json, 2, parsed.fuzziness().asInt());
    }
}
