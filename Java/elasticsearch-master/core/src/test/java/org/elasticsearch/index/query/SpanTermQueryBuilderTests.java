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

import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.lucene.BytesRefs;
import org.elasticsearch.index.mapper.MappedFieldType;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;

public class SpanTermQueryBuilderTests extends AbstractTermQueryTestCase<SpanTermQueryBuilder> {

    @Override
    protected SpanTermQueryBuilder createQueryBuilder(String fieldName, Object value) {
        return new SpanTermQueryBuilder(fieldName, value);
    }

    @Override
    protected void doAssertLuceneQuery(SpanTermQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        assertThat(query, instanceOf(SpanTermQuery.class));
        SpanTermQuery spanTermQuery = (SpanTermQuery) query;
        assertThat(spanTermQuery.getTerm().field(), equalTo(queryBuilder.fieldName()));
        MappedFieldType mapper = context.fieldMapper(queryBuilder.fieldName());
        if (mapper != null) {
            BytesRef bytesRef = mapper.indexedValueForSearch(queryBuilder.value());
            assertThat(spanTermQuery.getTerm().bytes(), equalTo(bytesRef));
        } else {
            assertThat(spanTermQuery.getTerm().bytes(), equalTo(BytesRefs.toBytesRef(queryBuilder.value())));
        }
    }

    /**
     * @param amount the number of clauses that will be returned
     * @return an array of random {@link SpanTermQueryBuilder} with same field name
     */
    public SpanTermQueryBuilder[] createSpanTermQueryBuilders(int amount) {
        SpanTermQueryBuilder[] clauses = new SpanTermQueryBuilder[amount];
        SpanTermQueryBuilder first = createTestQueryBuilder();
        clauses[0] = first;
        for (int i = 1; i < amount; i++) {
            // we need same field name in all clauses, so we only randomize value
            SpanTermQueryBuilder spanTermQuery = new SpanTermQueryBuilder(first.fieldName(), getRandomValueForFieldName(first.fieldName()));
            if (randomBoolean()) {
                spanTermQuery.boost(2.0f / randomIntBetween(1, 20));
            }
            if (randomBoolean()) {
                spanTermQuery.queryName(randomAsciiOfLengthBetween(1, 10));
            }
            clauses[i] = spanTermQuery;
        }
        return clauses;
    }

    public void testFromJson() throws IOException {
        String json =
                "{    \"span_term\" : { \"user\" : { \"value\" : \"kimchy\", \"boost\" : 2.0 } }}    ";

        SpanTermQueryBuilder parsed = (SpanTermQueryBuilder) parseQuery(json);
        checkGeneratedJson(json, parsed);

        assertEquals(json, "kimchy", parsed.value());
        assertEquals(json, 2.0, parsed.boost(), 0.0001);
    }
}
