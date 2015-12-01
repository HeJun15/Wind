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

package org.elasticsearch.common.xcontent.support;

import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.test.ESTestCase;
import org.hamcrest.Matchers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 */
public class XContentMapValuesTests extends ESTestCase {
    public void testFilter() throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
                .field("test1", "value1")
                .field("test2", "value2")
                .field("something_else", "value3")
                .endObject();

        Map<String, Object> source;
        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            source = parser.map();
        }
        Map<String, Object> filter = XContentMapValues.filter(source, new String[]{"test1"}, Strings.EMPTY_ARRAY);
        assertThat(filter.size(), equalTo(1));
        assertThat(filter.get("test1").toString(), equalTo("value1"));

        filter = XContentMapValues.filter(source, new String[]{"test*"}, Strings.EMPTY_ARRAY);
        assertThat(filter.size(), equalTo(2));
        assertThat(filter.get("test1").toString(), equalTo("value1"));
        assertThat(filter.get("test2").toString(), equalTo("value2"));

        filter = XContentMapValues.filter(source, Strings.EMPTY_ARRAY, new String[]{"test1"});
        assertThat(filter.size(), equalTo(2));
        assertThat(filter.get("test2").toString(), equalTo("value2"));
        assertThat(filter.get("something_else").toString(), equalTo("value3"));

        // more complex object...
        builder = XContentFactory.jsonBuilder().startObject()
                .startObject("path1")
                .startArray("path2")
                .startObject().field("test", "value1").endObject()
                .startObject().field("test", "value2").endObject()
                .endArray()
                .endObject()
                .field("test1", "value1")
                .endObject();

        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            source = parser.map();
        }
        filter = XContentMapValues.filter(source, new String[]{"path1"}, Strings.EMPTY_ARRAY);
        assertThat(filter.size(), equalTo(1));

        filter = XContentMapValues.filter(source, new String[]{"path1*"}, Strings.EMPTY_ARRAY);
        assertThat(filter.get("path1"), equalTo(source.get("path1")));
        assertThat(filter.containsKey("test1"), equalTo(false));

        filter = XContentMapValues.filter(source, new String[]{"test1*"}, Strings.EMPTY_ARRAY);
        assertThat(filter.get("test1"), equalTo(source.get("test1")));
        assertThat(filter.containsKey("path1"), equalTo(false));

        filter = XContentMapValues.filter(source, new String[]{"path1.path2.*"}, Strings.EMPTY_ARRAY);
        assertThat(filter.get("path1"), equalTo(source.get("path1")));
        assertThat(filter.containsKey("test1"), equalTo(false));
    }

    @SuppressWarnings({"unchecked"})
    public void testExtractValue() throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
                .field("test", "value")
                .endObject();

        Map<String, Object> map;
        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            map = parser.map();
        }
        assertThat(XContentMapValues.extractValue("test", map).toString(), equalTo("value"));
        assertThat(XContentMapValues.extractValue("test.me", map), nullValue());
        assertThat(XContentMapValues.extractValue("something.else.2", map), nullValue());

        builder = XContentFactory.jsonBuilder().startObject()
                .startObject("path1").startObject("path2").field("test", "value").endObject().endObject()
                .endObject();

        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            map = parser.map();
        }
        assertThat(XContentMapValues.extractValue("path1.path2.test", map).toString(), equalTo("value"));
        assertThat(XContentMapValues.extractValue("path1.path2.test_me", map), nullValue());
        assertThat(XContentMapValues.extractValue("path1.non_path2.test", map), nullValue());

        Object extValue = XContentMapValues.extractValue("path1.path2", map);
        assertThat(extValue, instanceOf(Map.class));
        Map<String, Object> extMapValue = (Map<String, Object>) extValue;
        assertThat(extMapValue, hasEntry("test", (Object) "value"));

        extValue = XContentMapValues.extractValue("path1", map);
        assertThat(extValue, instanceOf(Map.class));
        extMapValue = (Map<String, Object>) extValue;
        assertThat(extMapValue.containsKey("path2"), equalTo(true));

        // lists
        builder = XContentFactory.jsonBuilder().startObject()
                .startObject("path1").field("test", "value1", "value2").endObject()
                .endObject();

        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            map = parser.map();
        }

        extValue = XContentMapValues.extractValue("path1.test", map);
        assertThat(extValue, instanceOf(List.class));

        List extListValue = (List) extValue;
        assertThat(extListValue.size(), equalTo(2));

        builder = XContentFactory.jsonBuilder().startObject()
                .startObject("path1")
                .startArray("path2")
                .startObject().field("test", "value1").endObject()
                .startObject().field("test", "value2").endObject()
                .endArray()
                .endObject()
                .endObject();

        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            map = parser.map();
        }

        extValue = XContentMapValues.extractValue("path1.path2.test", map);
        assertThat(extValue, instanceOf(List.class));

        extListValue = (List) extValue;
        assertThat(extListValue.size(), equalTo(2));
        assertThat(extListValue.get(0).toString(), equalTo("value1"));
        assertThat(extListValue.get(1).toString(), equalTo("value2"));

        // fields with . in them
        builder = XContentFactory.jsonBuilder().startObject()
                .field("xxx.yyy", "value")
                .endObject();
        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            map = parser.map();
        }
        assertThat(XContentMapValues.extractValue("xxx.yyy", map).toString(), equalTo("value"));

        builder = XContentFactory.jsonBuilder().startObject()
                .startObject("path1.xxx").startObject("path2.yyy").field("test", "value").endObject().endObject()
                .endObject();

        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            map = parser.map();
        }
        assertThat(XContentMapValues.extractValue("path1.xxx.path2.yyy.test", map).toString(), equalTo("value"));
    }

    public void testExtractRawValue() throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
                .field("test", "value")
                .endObject();

        Map<String, Object> map;
        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            map = parser.map();
        }
        assertThat(XContentMapValues.extractRawValues("test", map).get(0).toString(), equalTo("value"));

        builder = XContentFactory.jsonBuilder().startObject()
                .field("test.me", "value")
                .endObject();

        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            map = parser.map();
        }
        assertThat(XContentMapValues.extractRawValues("test.me", map).get(0).toString(), equalTo("value"));

        builder = XContentFactory.jsonBuilder().startObject()
                .startObject("path1").startObject("path2").field("test", "value").endObject().endObject()
                .endObject();

        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            map = parser.map();
        }
        assertThat(XContentMapValues.extractRawValues("path1.path2.test", map).get(0).toString(), equalTo("value"));

        builder = XContentFactory.jsonBuilder().startObject()
                .startObject("path1.xxx").startObject("path2.yyy").field("test", "value").endObject().endObject()
                .endObject();

        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            map = parser.map();
        }
        assertThat(XContentMapValues.extractRawValues("path1.xxx.path2.yyy.test", map).get(0).toString(), equalTo("value"));
    }

    public void testPrefixedNamesFilteringTest() {
        Map<String, Object> map = new HashMap<>();
        map.put("obj", "value");
        map.put("obj_name", "value_name");
        Map<String, Object> filterdMap = XContentMapValues.filter(map, new String[]{"obj_name"}, Strings.EMPTY_ARRAY);
        assertThat(filterdMap.size(), equalTo(1));
        assertThat((String) filterdMap.get("obj_name"), equalTo("value_name"));
    }


    @SuppressWarnings("unchecked")
    public void testNestedFiltering() {
        Map<String, Object> map = new HashMap<>();
        map.put("field", "value");
        map.put("array",
                Arrays.asList(
                        1,
                        new HashMap<String, Object>() {{
                            put("nested", 2);
                            put("nested_2", 3);
                        }}));
        Map<String, Object> falteredMap = XContentMapValues.filter(map, new String[]{"array.nested"}, Strings.EMPTY_ARRAY);
        assertThat(falteredMap.size(), equalTo(1));

        // Selecting members of objects within arrays (ex. [ 1, { nested: "value"} ])  always returns all values in the array (1 in the ex)
        // this is expected behavior as this types of objects are not supported in ES
        assertThat((Integer) ((List) falteredMap.get("array")).get(0), equalTo(1));
        assertThat(((Map<String, Object>) ((List) falteredMap.get("array")).get(1)).size(), equalTo(1));
        assertThat((Integer) ((Map<String, Object>) ((List) falteredMap.get("array")).get(1)).get("nested"), equalTo(2));

        falteredMap = XContentMapValues.filter(map, new String[]{"array.*"}, Strings.EMPTY_ARRAY);
        assertThat(falteredMap.size(), equalTo(1));
        assertThat((Integer) ((List) falteredMap.get("array")).get(0), equalTo(1));
        assertThat(((Map<String, Object>) ((List) falteredMap.get("array")).get(1)).size(), equalTo(2));

        map.clear();
        map.put("field", "value");
        map.put("obj",
                new HashMap<String, Object>() {{
                    put("field", "value");
                    put("field2", "value2");
                }});
        falteredMap = XContentMapValues.filter(map, new String[]{"obj.field"}, Strings.EMPTY_ARRAY);
        assertThat(falteredMap.size(), equalTo(1));
        assertThat(((Map<String, Object>) falteredMap.get("obj")).size(), equalTo(1));
        assertThat((String) ((Map<String, Object>) falteredMap.get("obj")).get("field"), equalTo("value"));

        falteredMap = XContentMapValues.filter(map, new String[]{"obj.*"}, Strings.EMPTY_ARRAY);
        assertThat(falteredMap.size(), equalTo(1));
        assertThat(((Map<String, Object>) falteredMap.get("obj")).size(), equalTo(2));
        assertThat((String) ((Map<String, Object>) falteredMap.get("obj")).get("field"), equalTo("value"));
        assertThat((String) ((Map<String, Object>) falteredMap.get("obj")).get("field2"), equalTo("value2"));

    }

    @SuppressWarnings("unchecked")
    public void testCompleteObjectFiltering() {
        Map<String, Object> map = new HashMap<>();
        map.put("field", "value");
        map.put("obj",
                new HashMap<String, Object>() {{
                    put("field", "value");
                    put("field2", "value2");
                }});
        map.put("array",
                Arrays.asList(
                        1,
                        new HashMap<String, Object>() {{
                            put("field", "value");
                            put("field2", "value2");
                        }}));

        Map<String, Object> filteredMap = XContentMapValues.filter(map, new String[]{"obj"}, Strings.EMPTY_ARRAY);
        assertThat(filteredMap.size(), equalTo(1));
        assertThat(((Map<String, Object>) filteredMap.get("obj")).size(), equalTo(2));
        assertThat(((Map<String, Object>) filteredMap.get("obj")).get("field").toString(), equalTo("value"));
        assertThat(((Map<String, Object>) filteredMap.get("obj")).get("field2").toString(), equalTo("value2"));


        filteredMap = XContentMapValues.filter(map, new String[]{"obj"}, new String[]{"*.field2"});
        assertThat(filteredMap.size(), equalTo(1));
        assertThat(((Map<String, Object>) filteredMap.get("obj")).size(), equalTo(1));
        assertThat(((Map<String, Object>) filteredMap.get("obj")).get("field").toString(), equalTo("value"));


        filteredMap = XContentMapValues.filter(map, new String[]{"array"}, new String[]{});
        assertThat(filteredMap.size(), equalTo(1));
        assertThat(((List) filteredMap.get("array")).size(), equalTo(2));
        assertThat((Integer) ((List) filteredMap.get("array")).get(0), equalTo(1));
        assertThat(((Map<String, Object>) ((List) filteredMap.get("array")).get(1)).size(), equalTo(2));

        filteredMap = XContentMapValues.filter(map, new String[]{"array"}, new String[]{"*.field2"});
        assertThat(filteredMap.size(), equalTo(1));
        assertThat(((List) filteredMap.get("array")).size(), equalTo(2));
        assertThat((Integer) ((List) filteredMap.get("array")).get(0), equalTo(1));
        assertThat(((Map<String, Object>) ((List) filteredMap.get("array")).get(1)).size(), equalTo(1));
        assertThat(((Map<String, Object>) ((List) filteredMap.get("array")).get(1)).get("field").toString(), equalTo("value"));
    }

    @SuppressWarnings("unchecked")
    public void testFilterIncludesUsingStarPrefix() {
        Map<String, Object> map = new HashMap<>();
        map.put("field", "value");
        map.put("obj",
                new HashMap<String, Object>() {{
                    put("field", "value");
                    put("field2", "value2");
                }});
        map.put("n_obj",
                new HashMap<String, Object>() {{
                    put("n_field", "value");
                    put("n_field2", "value2");
                }});

        Map<String, Object> filteredMap = XContentMapValues.filter(map, new String[]{"*.field2"}, Strings.EMPTY_ARRAY);
        assertThat(filteredMap.size(), equalTo(1));
        assertThat(filteredMap, hasKey("obj"));
        assertThat(((Map<String, Object>) filteredMap.get("obj")).size(), equalTo(1));
        assertThat(((Map<String, Object>) filteredMap.get("obj")), hasKey("field2"));

        // only objects
        filteredMap = XContentMapValues.filter(map, new String[]{"*.*"}, Strings.EMPTY_ARRAY);
        assertThat(filteredMap.size(), equalTo(2));
        assertThat(filteredMap, hasKey("obj"));
        assertThat(((Map<String, Object>) filteredMap.get("obj")).size(), equalTo(2));
        assertThat(filteredMap, hasKey("n_obj"));
        assertThat(((Map<String, Object>) filteredMap.get("n_obj")).size(), equalTo(2));


        filteredMap = XContentMapValues.filter(map, new String[]{"*"}, new String[]{"*.*2"});
        assertThat(filteredMap.size(), equalTo(3));
        assertThat(filteredMap, hasKey("field"));
        assertThat(filteredMap, hasKey("obj"));
        assertThat(((Map) filteredMap.get("obj")).size(), equalTo(1));
        assertThat(((Map<String, Object>) filteredMap.get("obj")), hasKey("field"));
        assertThat(filteredMap, hasKey("n_obj"));
        assertThat(((Map<String, Object>) filteredMap.get("n_obj")).size(), equalTo(1));
        assertThat(((Map<String, Object>) filteredMap.get("n_obj")), hasKey("n_field"));

    }

    public void testFilterWithEmptyIncludesExcludes() {
        Map<String, Object> map = new HashMap<>();
        map.put("field", "value");
        Map<String, Object> filteredMap = XContentMapValues.filter(map, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        assertThat(filteredMap.size(), equalTo(1));
        assertThat(filteredMap.get("field").toString(), equalTo("value"));

    }

    public void testThatFilterIncludesEmptyObjectWhenUsingIncludes() throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
                .startObject("obj")
                .endObject()
                .endObject();

        Tuple<XContentType, Map<String, Object>> mapTuple = XContentHelper.convertToMap(builder.bytes(), true);
        Map<String, Object> filteredSource = XContentMapValues.filter(mapTuple.v2(), new String[]{"obj"}, Strings.EMPTY_ARRAY);

        assertThat(mapTuple.v2(), equalTo(filteredSource));
    }

    public void testThatFilterIncludesEmptyObjectWhenUsingExcludes() throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
                .startObject("obj")
                .endObject()
                .endObject();

        Tuple<XContentType, Map<String, Object>> mapTuple = XContentHelper.convertToMap(builder.bytes(), true);
        Map<String, Object> filteredSource = XContentMapValues.filter(mapTuple.v2(), Strings.EMPTY_ARRAY, new String[]{"nonExistingField"});

        assertThat(mapTuple.v2(), equalTo(filteredSource));
    }

    public void testNotOmittingObjectsWithExcludedProperties() throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
                .startObject("obj")
                .field("f1", "v1")
                .endObject()
                .endObject();

        Tuple<XContentType, Map<String, Object>> mapTuple = XContentHelper.convertToMap(builder.bytes(), true);
        Map<String, Object> filteredSource = XContentMapValues.filter(mapTuple.v2(), Strings.EMPTY_ARRAY, new String[]{"obj.f1"});

        assertThat(filteredSource.size(), equalTo(1));
        assertThat(filteredSource, hasKey("obj"));
        assertThat(((Map) filteredSource.get("obj")).size(), equalTo(0));
    }

    @SuppressWarnings({"unchecked"})
    public void testNotOmittingObjectWithNestedExcludedObject() throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
                .startObject("obj1")
                .startObject("obj2")
                .startObject("obj3")
                .endObject()
                .endObject()
                .endObject()
                .endObject();

        // implicit include
        Tuple<XContentType, Map<String, Object>> mapTuple = XContentHelper.convertToMap(builder.bytes(), true);
        Map<String, Object> filteredSource = XContentMapValues.filter(mapTuple.v2(), Strings.EMPTY_ARRAY, new String[]{"*.obj2"});

        assertThat(filteredSource.size(), equalTo(1));
        assertThat(filteredSource, hasKey("obj1"));
        assertThat(((Map) filteredSource.get("obj1")).size(), Matchers.equalTo(0));

        // explicit include
        filteredSource = XContentMapValues.filter(mapTuple.v2(), new String[]{"obj1"}, new String[]{"*.obj2"});
        assertThat(filteredSource.size(), equalTo(1));
        assertThat(filteredSource, hasKey("obj1"));
        assertThat(((Map) filteredSource.get("obj1")).size(), Matchers.equalTo(0));

        // wild card include
        filteredSource = XContentMapValues.filter(mapTuple.v2(), new String[]{"*.obj2"}, new String[]{"*.obj3"});
        assertThat(filteredSource.size(), equalTo(1));
        assertThat(filteredSource, hasKey("obj1"));
        assertThat(((Map<String, Object>) filteredSource.get("obj1")), hasKey("obj2"));
        assertThat(((Map) ((Map) filteredSource.get("obj1")).get("obj2")).size(), Matchers.equalTo(0));
    }

    @SuppressWarnings({"unchecked"})
    public void testIncludingObjectWithNestedIncludedObject() throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
                .startObject("obj1")
                .startObject("obj2")
                .endObject()
                .endObject()
                .endObject();

        Tuple<XContentType, Map<String, Object>> mapTuple = XContentHelper.convertToMap(builder.bytes(), true);
        Map<String, Object> filteredSource = XContentMapValues.filter(mapTuple.v2(), new String[]{"*.obj2"}, Strings.EMPTY_ARRAY);

        assertThat(filteredSource.size(), equalTo(1));
        assertThat(filteredSource, hasKey("obj1"));
        assertThat(((Map) filteredSource.get("obj1")).size(), equalTo(1));
        assertThat(((Map<String, Object>) filteredSource.get("obj1")), hasKey("obj2"));
        assertThat(((Map) ((Map) filteredSource.get("obj1")).get("obj2")).size(), equalTo(0));
    }

    public void testEmptyList() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
                .startArray("some_array")
                .endArray().endObject();

        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            assertEquals(XContentParser.Token.START_OBJECT, parser.nextToken());
            assertEquals(XContentParser.Token.FIELD_NAME, parser.nextToken());
            assertEquals("some_array", parser.currentName());
            if (random().nextBoolean()) {
                // sometimes read the start array token, sometimes not
                assertEquals(XContentParser.Token.START_ARRAY, parser.nextToken());
            }
            assertEquals(Collections.emptyList(), parser.list());
        }
    }

    public void testSimpleList() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
                .startArray("some_array")
                    .value(1)
                    .value(3)
                    .value(0)
                .endArray().endObject();

        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            assertEquals(XContentParser.Token.START_OBJECT, parser.nextToken());
            assertEquals(XContentParser.Token.FIELD_NAME, parser.nextToken());
            assertEquals("some_array", parser.currentName());
            if (random().nextBoolean()) {
                // sometimes read the start array token, sometimes not
                assertEquals(XContentParser.Token.START_ARRAY, parser.nextToken());
            }
            assertEquals(Arrays.asList(1, 3, 0), parser.list());
        }
    }

    public void testNestedList() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
                .startArray("some_array")
                    .startArray().endArray()
                    .startArray().value(1).value(3).endArray()
                    .startArray().value(2).endArray()
                .endArray().endObject();

        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            assertEquals(XContentParser.Token.START_OBJECT, parser.nextToken());
            assertEquals(XContentParser.Token.FIELD_NAME, parser.nextToken());
            assertEquals("some_array", parser.currentName());
            if (random().nextBoolean()) {
                // sometimes read the start array token, sometimes not
                assertEquals(XContentParser.Token.START_ARRAY, parser.nextToken());
            }
            assertEquals(
                    Arrays.asList(Collections.<Integer>emptyList(), Arrays.asList(1, 3), Arrays.asList(2)),
                    parser.list());
        }
    }

    public void testNestedMapInList() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
                .startArray("some_array")
                    .startObject().field("foo", "bar").endObject()
                    .startObject().endObject()
                .endArray().endObject();

        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(builder.string())) {
            assertEquals(XContentParser.Token.START_OBJECT, parser.nextToken());
            assertEquals(XContentParser.Token.FIELD_NAME, parser.nextToken());
            assertEquals("some_array", parser.currentName());
            if (random().nextBoolean()) {
                // sometimes read the start array token, sometimes not
                assertEquals(XContentParser.Token.START_ARRAY, parser.nextToken());
            }
            assertEquals(
                    Arrays.asList(singletonMap("foo", "bar"), emptyMap()),
                    parser.list());
        }
    }
}
