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

package org.elasticsearch.index.mapper.object;

import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.mapper.DocumentMapper;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.test.ESSingleNodeTestCase;

import static org.hamcrest.Matchers.containsString;

/**
 */
public class SimpleObjectMappingTests extends ESSingleNodeTestCase {
    public void testDifferentInnerObjectTokenFailure() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .endObject().endObject().string();

        DocumentMapper defaultMapper = createIndex("test").mapperService().documentMapperParser().parse(mapping);
        try {
            defaultMapper.parse("test", "type", "1", new BytesArray(" {\n" +
                    "      \"object\": {\n" +
                    "        \"array\":[\n" +
                    "        {\n" +
                    "          \"object\": { \"value\": \"value\" }\n" +
                    "        },\n" +
                    "        {\n" +
                    "          \"object\":\"value\"\n" +
                    "        }\n" +
                    "        ]\n" +
                    "      },\n" +
                    "      \"value\":\"value\"\n" +
                    "    }"));
            fail();
        } catch (MapperParsingException e) {
            // all is well
        }
    }

    public void testEmptyArrayProperties() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startArray("properties").endArray()
                .endObject().endObject().string();
        createIndex("test").mapperService().documentMapperParser().parse(mapping);
    }

    public void testEmptyFieldsArrayMultiFields() throws Exception {
        String mapping = XContentFactory.jsonBuilder()
                                        .startObject()
                                            .startObject("tweet")
                                                .startObject("properties")
                                                    .startObject("name")
                                                        .field("type", "string")
                                                        .field("index", "analyzed")
                                                        .startArray("fields")
                                                        .endArray()
                                                    .endObject()
                                                .endObject()
                                            .endObject()
                                        .endObject()
                                        .string();
        createIndex("test").mapperService().documentMapperParser().parse(mapping);
    }

    public void testFieldsArrayMultiFieldsShouldThrowException() throws Exception {
        String mapping = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("tweet")
                        .startObject("properties")
                            .startObject("name")
                                .field("type", "string")
                                .field("index", "analyzed")
                                .startArray("fields")
                                    .startObject().field("test", "string").endObject()
                                    .startObject().field("test2", "string").endObject()
                                .endArray()
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject()
                .string();
        try {
            createIndex("test").mapperService().documentMapperParser().parse(mapping);
            fail("Expected MapperParsingException");
        } catch(MapperParsingException e) {
            assertThat(e.getMessage(), containsString("expected map for property [fields]"));
            assertThat(e.getMessage(), containsString("but got a class java.util.ArrayList"));
        }
    }

    public void testEmptyFieldsArray() throws Exception {
        String mapping = XContentFactory.jsonBuilder()
                                        .startObject()
                                            .startObject("tweet")
                                                .startObject("properties")
                                                    .startArray("fields")
                                                    .endArray()
                                                .endObject()
                                            .endObject()
                                        .endObject()
                                        .string();
        createIndex("test").mapperService().documentMapperParser().parse(mapping);
    }

    public void testFieldsWithFilledArrayShouldThrowException() throws Exception {
        String mapping = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("tweet")
                        .startObject("properties")
                            .startArray("fields")
                                .startObject().field("test", "string").endObject()
                                .startObject().field("test2", "string").endObject()
                            .endArray()
                        .endObject()
                    .endObject()
                .endObject()
                .string();
        try {
            createIndex("test").mapperService().documentMapperParser().parse(mapping);
            fail("Expected MapperParsingException");
        } catch (MapperParsingException e) {
            assertThat(e.getMessage(), containsString("Expected map for property [fields]"));
        }
    }

    public void testFieldPropertiesArray() throws Exception {
        String mapping = XContentFactory.jsonBuilder()
                                        .startObject()
                                            .startObject("tweet")
                                                .startObject("properties")
                                                    .startObject("name")
                                                        .field("type", "string")
                                                        .field("index", "analyzed")
                                                        .startObject("fields")
                                                            .startObject("raw")
                                                                .field("type", "string")
                                                                .field("index","not_analyzed")
                                                            .endObject()
                                                        .endObject()
                                                    .endObject()
                                                .endObject()
                                            .endObject()
                                        .endObject()
                                        .string();
        createIndex("test").mapperService().documentMapperParser().parse(mapping);
    }
}
