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

package org.elasticsearch.index.mapper.merge;

import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.elasticsearch.index.mapper.DocumentFieldMappers;
import org.elasticsearch.index.mapper.DocumentMapper;
import org.elasticsearch.index.mapper.DocumentMapperParser;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.Mapping;
import org.elasticsearch.index.mapper.MergeResult;
import org.elasticsearch.index.mapper.ParsedDocument;
import org.elasticsearch.index.mapper.core.StringFieldMapper;
import org.elasticsearch.index.mapper.object.ObjectMapper;
import org.elasticsearch.test.ESSingleNodeTestCase;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class TestMergeMapperTests extends ESSingleNodeTestCase {

    public void test1Merge() throws Exception {

        String stage1Mapping = XContentFactory.jsonBuilder().startObject().startObject("person").startObject("properties")
                .startObject("name").field("type", "string").endObject()
                .endObject().endObject().endObject().string();
        DocumentMapperParser parser = createIndex("test").mapperService().documentMapperParser();
        DocumentMapper stage1 = parser.parse(stage1Mapping);
        String stage2Mapping = XContentFactory.jsonBuilder().startObject().startObject("person").startObject("properties")
                .startObject("name").field("type", "string").endObject()
                .startObject("age").field("type", "integer").endObject()
                .startObject("obj1").startObject("properties").startObject("prop1").field("type", "integer").endObject().endObject().endObject()
                .endObject().endObject().endObject().string();
        DocumentMapper stage2 = parser.parse(stage2Mapping);

        MergeResult mergeResult = stage1.merge(stage2.mapping(), true, false);
        assertThat(mergeResult.hasConflicts(), equalTo(false));
        // since we are simulating, we should not have the age mapping
        assertThat(stage1.mappers().smartNameFieldMapper("age"), nullValue());
        assertThat(stage1.mappers().smartNameFieldMapper("obj1.prop1"), nullValue());
        // now merge, don't simulate
        mergeResult = stage1.merge(stage2.mapping(), false, false);
        // there is still merge failures
        assertThat(mergeResult.hasConflicts(), equalTo(false));
        // but we have the age in
        assertThat(stage1.mappers().smartNameFieldMapper("age"), notNullValue());
        assertThat(stage1.mappers().smartNameFieldMapper("obj1.prop1"), notNullValue());
    }

    public void testMergeObjectDynamic() throws Exception {
        DocumentMapperParser parser = createIndex("test").mapperService().documentMapperParser();
        String objectMapping = XContentFactory.jsonBuilder().startObject().startObject("type1").endObject().endObject().string();
        DocumentMapper mapper = parser.parse(objectMapping);
        assertNull(mapper.root().dynamic());

        String withDynamicMapping = XContentFactory.jsonBuilder().startObject().startObject("type1").field("dynamic", "false").endObject().endObject().string();
        DocumentMapper withDynamicMapper = parser.parse(withDynamicMapping);
        assertThat(withDynamicMapper.root().dynamic(), equalTo(ObjectMapper.Dynamic.FALSE));

        MergeResult mergeResult = mapper.merge(withDynamicMapper.mapping(), false, false);
        assertThat(mergeResult.hasConflicts(), equalTo(false));
        assertThat(mapper.root().dynamic(), equalTo(ObjectMapper.Dynamic.FALSE));
    }

    public void testMergeObjectAndNested() throws Exception {
        DocumentMapperParser parser = createIndex("test").mapperService().documentMapperParser();
        String objectMapping = XContentFactory.jsonBuilder().startObject().startObject("type1").startObject("properties")
                .startObject("obj").field("type", "object").endObject()
                .endObject().endObject().endObject().string();
        DocumentMapper objectMapper = parser.parse(objectMapping);
        String nestedMapping = XContentFactory.jsonBuilder().startObject().startObject("type1").startObject("properties")
                .startObject("obj").field("type", "nested").endObject()
                .endObject().endObject().endObject().string();
        DocumentMapper nestedMapper = parser.parse(nestedMapping);

        MergeResult mergeResult = objectMapper.merge(nestedMapper.mapping(), true, false);
        assertThat(mergeResult.hasConflicts(), equalTo(true));
        assertThat(mergeResult.buildConflicts().length, equalTo(1));
        assertThat(mergeResult.buildConflicts()[0], equalTo("object mapping [obj] can't be changed from non-nested to nested"));

        mergeResult = nestedMapper.merge(objectMapper.mapping(), true, false);
        assertThat(mergeResult.buildConflicts().length, equalTo(1));
        assertThat(mergeResult.buildConflicts()[0], equalTo("object mapping [obj] can't be changed from nested to non-nested"));
    }

    public void testMergeSearchAnalyzer() throws Exception {
        DocumentMapperParser parser = createIndex("test").mapperService().documentMapperParser();
        String mapping1 = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string").field("analyzer", "standard").field("search_analyzer", "whitespace").endObject().endObject()
                .endObject().endObject().string();
        String mapping2 = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string").field("analyzer", "standard").field("search_analyzer", "keyword").endObject().endObject()
                .endObject().endObject().string();

        DocumentMapper existing = parser.parse(mapping1);
        DocumentMapper changed = parser.parse(mapping2);

        assertThat(((NamedAnalyzer) existing.mappers().getMapper("field").fieldType().searchAnalyzer()).name(), equalTo("whitespace"));
        MergeResult mergeResult = existing.merge(changed.mapping(), false, false);

        assertThat(mergeResult.hasConflicts(), equalTo(false));
        assertThat(((NamedAnalyzer) existing.mappers().getMapper("field").fieldType().searchAnalyzer()).name(), equalTo("keyword"));
    }

    public void testChangeSearchAnalyzerToDefault() throws Exception {
        DocumentMapperParser parser = createIndex("test").mapperService().documentMapperParser();
        String mapping1 = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string").field("analyzer", "standard").field("search_analyzer", "whitespace").endObject().endObject()
                .endObject().endObject().string();
        String mapping2 = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string").field("analyzer", "standard").field("ignore_above", 14).endObject().endObject()
                .endObject().endObject().string();

        DocumentMapper existing = parser.parse(mapping1);
        DocumentMapper changed = parser.parse(mapping2);

        assertThat(((NamedAnalyzer) existing.mappers().getMapper("field").fieldType().searchAnalyzer()).name(), equalTo("whitespace"));
        MergeResult mergeResult = existing.merge(changed.mapping(), false, false);

        assertThat(mergeResult.hasConflicts(), equalTo(false));
        assertThat(((NamedAnalyzer) existing.mappers().getMapper("field").fieldType().searchAnalyzer()).name(), equalTo("standard"));
        assertThat(((StringFieldMapper) (existing.mappers().getMapper("field"))).getIgnoreAbove(), equalTo(14));
    }

    public void testConcurrentMergeTest() throws Throwable {
        final MapperService mapperService = createIndex("test").mapperService();
        mapperService.merge("test", new CompressedXContent("{\"test\":{}}"), true, false);
        final DocumentMapper documentMapper = mapperService.documentMapper("test");

        DocumentFieldMappers dfm = documentMapper.mappers();
        try {
            assertNotNull(dfm.indexAnalyzer().tokenStream("non_existing_field", "foo"));
            fail();
        } catch (IllegalArgumentException e) {
            // ok that's expected
        }

        final AtomicBoolean stopped = new AtomicBoolean(false);
        final CyclicBarrier barrier = new CyclicBarrier(2);
        final AtomicReference<String> lastIntroducedFieldName = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final Thread updater = new Thread() {
            @Override
            public void run() {
                try {
                    barrier.await();
                    for (int i = 0; i < 200 && stopped.get() == false; i++) {
                        final String fieldName = Integer.toString(i);
                        ParsedDocument doc = documentMapper.parse("test", "test", fieldName, new BytesArray("{ \"" + fieldName + "\" : \"test\" }"));
                        Mapping update = doc.dynamicMappingsUpdate();
                        assert update != null;
                        lastIntroducedFieldName.set(fieldName);
                        mapperService.merge("test", new CompressedXContent(update.toString()), false, false);
                    }
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    stopped.set(true);
                }
            }
        };
        updater.start();
        try {
            barrier.await();
            while(stopped.get() == false) {
                final String fieldName = lastIntroducedFieldName.get();
                final BytesReference source = new BytesArray("{ \"" + fieldName + "\" : \"test\" }");
                ParsedDocument parsedDoc = documentMapper.parse("test", "test", "random", source);
                if (parsedDoc.dynamicMappingsUpdate() != null) {
                    // not in the mapping yet, try again
                    continue;
                }
                dfm = documentMapper.mappers();
                assertNotNull(dfm.indexAnalyzer().tokenStream(fieldName, "foo"));
            }
        } finally {
            stopped.set(true);
            updater.join();
        }
        if (error.get() != null) {
            throw error.get();
        }
    }
}
