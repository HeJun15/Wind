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

package org.elasticsearch.index.mapper.string;

import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.IndexableFieldType;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.fielddata.FieldDataType;
import org.elasticsearch.index.mapper.ContentPath;
import org.elasticsearch.index.mapper.DocumentMapper;
import org.elasticsearch.index.mapper.DocumentMapperParser;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.Mapper.BuilderContext;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.index.mapper.MergeResult;
import org.elasticsearch.index.mapper.ParseContext.Document;
import org.elasticsearch.index.mapper.ParsedDocument;
import org.elasticsearch.index.mapper.core.StringFieldMapper;
import org.elasticsearch.index.mapper.core.StringFieldMapper.Builder;
import org.elasticsearch.test.ESSingleNodeTestCase;
import org.elasticsearch.test.VersionUtils;
import org.junit.Before;

import java.util.Arrays;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 */
public class SimpleStringMappingTests extends ESSingleNodeTestCase {
    private static Settings DOC_VALUES_SETTINGS = Settings.builder().put(FieldDataType.FORMAT_KEY, FieldDataType.DOC_VALUES_FORMAT_VALUE).build();

    IndexService indexService;
    DocumentMapperParser parser;

    @Before
    public void before() {
        indexService = createIndex("test");
        parser = indexService.mapperService().documentMapperParser();
    }

    public void testLimit() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string").field("ignore_above", 5).endObject().endObject()
                .endObject().endObject().string();

        DocumentMapper defaultMapper = parser.parse(mapping);

        ParsedDocument doc = defaultMapper.parse("test", "type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "1234")
                .endObject()
                .bytes());

        assertThat(doc.rootDoc().getField("field"), notNullValue());

        doc = defaultMapper.parse("test", "type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "12345")
                .endObject()
                .bytes());

        assertThat(doc.rootDoc().getField("field"), notNullValue());

        doc = defaultMapper.parse("test", "type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "123456")
                .endObject()
                .bytes());

        assertThat(doc.rootDoc().getField("field"), nullValue());
    }

    private void assertDefaultAnalyzedFieldType(IndexableFieldType fieldType) {
        assertThat(fieldType.omitNorms(), equalTo(false));
        assertThat(fieldType.indexOptions(), equalTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS));
        assertThat(fieldType.storeTermVectors(), equalTo(false));
        assertThat(fieldType.storeTermVectorOffsets(), equalTo(false));
        assertThat(fieldType.storeTermVectorPositions(), equalTo(false));
        assertThat(fieldType.storeTermVectorPayloads(), equalTo(false));
    }

    private void assertEquals(IndexableFieldType ft1, IndexableFieldType ft2) {
        assertEquals(ft1.tokenized(), ft2.tokenized());
        assertEquals(ft1.omitNorms(), ft2.omitNorms());
        assertEquals(ft1.indexOptions(), ft2.indexOptions());
        assertEquals(ft1.storeTermVectors(), ft2.storeTermVectors());
        assertEquals(ft1.docValuesType(), ft2.docValuesType());
    }

    private void assertParseIdemPotent(IndexableFieldType expected, DocumentMapper mapper) throws Exception {
        String mapping = mapper.toXContent(XContentFactory.jsonBuilder().startObject(), new ToXContent.MapParams(emptyMap())).endObject().string();
        mapper = parser.parse(mapping);
        ParsedDocument doc = mapper.parse("test", "type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "2345")
                .endObject()
                .bytes());
        assertEquals(expected, doc.rootDoc().getField("field").fieldType());
    }

    public void testDefaultsForAnalyzed() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string").endObject().endObject()
                .endObject().endObject().string();

        DocumentMapper defaultMapper = parser.parse(mapping);

        ParsedDocument doc = defaultMapper.parse("test", "type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "1234")
                .endObject()
                .bytes());

        IndexableFieldType fieldType = doc.rootDoc().getField("field").fieldType();
        assertDefaultAnalyzedFieldType(fieldType);
        assertParseIdemPotent(fieldType, defaultMapper);
    }

    public void testDefaultsForNotAnalyzed() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string").field("index", "not_analyzed").endObject().endObject()
                .endObject().endObject().string();

        DocumentMapper defaultMapper = parser.parse(mapping);

        ParsedDocument doc = defaultMapper.parse("test", "type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "1234")
                .endObject()
                .bytes());

        IndexableFieldType fieldType = doc.rootDoc().getField("field").fieldType();
        assertThat(fieldType.omitNorms(), equalTo(true));
        assertThat(fieldType.indexOptions(), equalTo(IndexOptions.DOCS));
        assertThat(fieldType.storeTermVectors(), equalTo(false));
        assertThat(fieldType.storeTermVectorOffsets(), equalTo(false));
        assertThat(fieldType.storeTermVectorPositions(), equalTo(false));
        assertThat(fieldType.storeTermVectorPayloads(), equalTo(false));
        assertParseIdemPotent(fieldType, defaultMapper);

        // now test it explicitly set

        mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string").field("index", "not_analyzed").startObject("norms").field("enabled", true).endObject().field("index_options", "freqs").endObject().endObject()
                .endObject().endObject().string();

        defaultMapper = parser.parse(mapping);

        doc = defaultMapper.parse("test", "type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "1234")
                .endObject()
                .bytes());

        fieldType = doc.rootDoc().getField("field").fieldType();
        assertThat(fieldType.omitNorms(), equalTo(false));
        assertThat(fieldType.indexOptions(), equalTo(IndexOptions.DOCS_AND_FREQS));
        assertThat(fieldType.storeTermVectors(), equalTo(false));
        assertThat(fieldType.storeTermVectorOffsets(), equalTo(false));
        assertThat(fieldType.storeTermVectorPositions(), equalTo(false));
        assertThat(fieldType.storeTermVectorPayloads(), equalTo(false));
        assertParseIdemPotent(fieldType, defaultMapper);

        // also test the deprecated omit_norms

        mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string").field("index", "not_analyzed").field("omit_norms", false).endObject().endObject()
                .endObject().endObject().string();

        defaultMapper = parser.parse(mapping);

        doc = defaultMapper.parse("test", "type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "1234")
                .endObject()
                .bytes());

        fieldType = doc.rootDoc().getField("field").fieldType();
        assertThat(fieldType.omitNorms(), equalTo(false));
        assertParseIdemPotent(fieldType, defaultMapper);
    }

    public void testSearchQuoteAnalyzerSerialization() throws Exception {
        // Cases where search_quote_analyzer should not be added to the mapping.
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties")
                .startObject("field1")
                    .field("type", "string")
                    .field("position_increment_gap", 1000)
                .endObject()
                .startObject("field2")
                    .field("type", "string")
                    .field("position_increment_gap", 1000)
                    .field("analyzer", "standard")
                .endObject()
                .startObject("field3")
                    .field("type", "string")
                    .field("position_increment_gap", 1000)
                    .field("analyzer", "standard")
                    .field("search_analyzer", "simple")
                .endObject()
                .startObject("field4")
                    .field("type", "string")
                    .field("position_increment_gap", 1000)
                    .field("analyzer", "standard")
                    .field("search_analyzer", "simple")
                    .field("search_quote_analyzer", "simple")
                .endObject()
                .endObject()
                .endObject().endObject().string();

        DocumentMapper mapper = parser.parse(mapping);
        for (String fieldName : Arrays.asList("field1", "field2", "field3", "field4")) {
            Map<String, Object> serializedMap = getSerializedMap(fieldName, mapper);
            assertFalse(fieldName, serializedMap.containsKey("search_quote_analyzer"));
        }

        // Cases where search_quote_analyzer should be present.
        mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties")
                .startObject("field1")
                    .field("type", "string")
                    .field("position_increment_gap", 1000)
                    .field("search_quote_analyzer", "simple")
                .endObject()
                .startObject("field2")
                    .field("type", "string")
                    .field("position_increment_gap", 1000)
                    .field("analyzer", "standard")
                    .field("search_analyzer", "standard")
                    .field("search_quote_analyzer", "simple")
                .endObject()
                .endObject()
                .endObject().endObject().string();

        mapper = parser.parse(mapping);
        for (String fieldName : Arrays.asList("field1", "field2")) {
            Map<String, Object> serializedMap = getSerializedMap(fieldName, mapper);
            assertEquals(serializedMap.get("search_quote_analyzer"), "simple");
        }
    }

    private Map<String, Object> getSerializedMap(String fieldName, DocumentMapper mapper) throws Exception {
        FieldMapper fieldMapper = mapper.mappers().smartNameFieldMapper(fieldName);
        XContentBuilder builder = JsonXContent.contentBuilder().startObject();
        fieldMapper.toXContent(builder, ToXContent.EMPTY_PARAMS).endObject();
        builder.close();

        Map<String, Object> fieldMap;
        try (XContentParser parser = JsonXContent.jsonXContent.createParser(builder.bytes())) {
            fieldMap = parser.map();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) fieldMap.get(fieldName);
        return result;
    }

    public void testTermVectors() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties")
                .startObject("field1")
                    .field("type", "string")
                    .field("term_vector", "no")
                .endObject()
                .startObject("field2")
                    .field("type", "string")
                    .field("term_vector", "yes")
                .endObject()
                .startObject("field3")
                    .field("type", "string")
                    .field("term_vector", "with_offsets")
                .endObject()
                .startObject("field4")
                    .field("type", "string")
                    .field("term_vector", "with_positions")
                .endObject()
                .startObject("field5")
                    .field("type", "string")
                    .field("term_vector", "with_positions_offsets")
                .endObject()
                .startObject("field6")
                    .field("type", "string")
                    .field("term_vector", "with_positions_offsets_payloads")
                .endObject()
                .endObject()
                .endObject().endObject().string();

        DocumentMapper defaultMapper = parser.parse(mapping);

        ParsedDocument doc = defaultMapper.parse("test", "type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field1", "1234")
                .field("field2", "1234")
                .field("field3", "1234")
                .field("field4", "1234")
                .field("field5", "1234")
                .field("field6", "1234")
                .endObject()
                .bytes());

        assertThat(doc.rootDoc().getField("field1").fieldType().storeTermVectors(), equalTo(false));
        assertThat(doc.rootDoc().getField("field1").fieldType().storeTermVectorOffsets(), equalTo(false));
        assertThat(doc.rootDoc().getField("field1").fieldType().storeTermVectorPositions(), equalTo(false));
        assertThat(doc.rootDoc().getField("field1").fieldType().storeTermVectorPayloads(), equalTo(false));

        assertThat(doc.rootDoc().getField("field2").fieldType().storeTermVectors(), equalTo(true));
        assertThat(doc.rootDoc().getField("field2").fieldType().storeTermVectorOffsets(), equalTo(false));
        assertThat(doc.rootDoc().getField("field2").fieldType().storeTermVectorPositions(), equalTo(false));
        assertThat(doc.rootDoc().getField("field2").fieldType().storeTermVectorPayloads(), equalTo(false));

        assertThat(doc.rootDoc().getField("field3").fieldType().storeTermVectors(), equalTo(true));
        assertThat(doc.rootDoc().getField("field3").fieldType().storeTermVectorOffsets(), equalTo(true));
        assertThat(doc.rootDoc().getField("field3").fieldType().storeTermVectorPositions(), equalTo(false));
        assertThat(doc.rootDoc().getField("field3").fieldType().storeTermVectorPayloads(), equalTo(false));

        assertThat(doc.rootDoc().getField("field4").fieldType().storeTermVectors(), equalTo(true));
        assertThat(doc.rootDoc().getField("field4").fieldType().storeTermVectorOffsets(), equalTo(false));
        assertThat(doc.rootDoc().getField("field4").fieldType().storeTermVectorPositions(), equalTo(true));
        assertThat(doc.rootDoc().getField("field4").fieldType().storeTermVectorPayloads(), equalTo(false));

        assertThat(doc.rootDoc().getField("field5").fieldType().storeTermVectors(), equalTo(true));
        assertThat(doc.rootDoc().getField("field5").fieldType().storeTermVectorOffsets(), equalTo(true));
        assertThat(doc.rootDoc().getField("field5").fieldType().storeTermVectorPositions(), equalTo(true));
        assertThat(doc.rootDoc().getField("field5").fieldType().storeTermVectorPayloads(), equalTo(false));

        assertThat(doc.rootDoc().getField("field6").fieldType().storeTermVectors(), equalTo(true));
        assertThat(doc.rootDoc().getField("field6").fieldType().storeTermVectorOffsets(), equalTo(true));
        assertThat(doc.rootDoc().getField("field6").fieldType().storeTermVectorPositions(), equalTo(true));
        assertThat(doc.rootDoc().getField("field6").fieldType().storeTermVectorPayloads(), equalTo(true));
    }

    public void testDocValuesFielddata() throws Exception {
        IndexService indexService = createIndex("index");
        DocumentMapperParser parser = indexService.mapperService().documentMapperParser();
        final BuilderContext ctx = new BuilderContext(indexService.getIndexSettings().getSettings(), new ContentPath(1));

        assertFalse(new Builder("anything").index(false).build(ctx).fieldType().hasDocValues());
        assertTrue(new Builder("anything").index(false).fieldDataSettings(DOC_VALUES_SETTINGS).build(ctx).fieldType().hasDocValues());
        assertTrue(new Builder("anything").index(false).docValues(true).build(ctx).fieldType().hasDocValues());

        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("properties")
            .startObject("str1")
                .field("type", "string")
                .startObject("fielddata")
                    .field("format", "paged_bytes")
                .endObject()
            .endObject()
            .startObject("str2")
                .field("type", "string")
                .field("index", "not_analyzed")
                .startObject("fielddata")
                    .field("format", "doc_values")
                .endObject()
            .endObject()
            .endObject()
            .endObject().endObject().string();

        DocumentMapper defaultMapper = parser.parse(mapping);

        ParsedDocument parsedDoc = defaultMapper.parse("test", "type", "1", XContentFactory.jsonBuilder()
            .startObject()
            .field("str1", "1234")
            .field("str2", "1234")
            .endObject()
            .bytes());
        final Document doc = parsedDoc.rootDoc();
        assertEquals(DocValuesType.NONE, docValuesType(doc, "str1"));
        assertEquals(DocValuesType.SORTED_SET, docValuesType(doc, "str2"));
    }

    public void testDocValues() throws Exception {
        // doc values only work on non-analyzed content
        final BuilderContext ctx = new BuilderContext(indexService.getIndexSettings().getSettings(), new ContentPath(1));
        try {
            new StringFieldMapper.Builder("anything").docValues(true).build(ctx);
            fail();
        } catch (Exception e) { /* OK */ }

        assertFalse(new Builder("anything").index(false).build(ctx).fieldType().hasDocValues());
        assertTrue(new Builder("anything").index(true).tokenized(false).build(ctx).fieldType().hasDocValues());
        assertFalse(new Builder("anything").index(true).tokenized(true).build(ctx).fieldType().hasDocValues());
        assertFalse(new Builder("anything").index(false).tokenized(false).docValues(false).build(ctx).fieldType().hasDocValues());
        assertTrue(new Builder("anything").index(false).docValues(true).build(ctx).fieldType().hasDocValues());

        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties")
                .startObject("str1")
                    .field("type", "string")
                    .field("index", "no")
                .endObject()
                .startObject("str2")
                    .field("type", "string")
                    .field("index", "not_analyzed")
                .endObject()
                .startObject("str3")
                    .field("type", "string")
                    .field("index", "analyzed")
                .endObject()
                .startObject("str4")
                    .field("type", "string")
                    .field("index", "not_analyzed")
                    .field("doc_values", false)
                .endObject()
                .startObject("str5")
                    .field("type", "string")
                    .field("index", "no")
                    .field("doc_values", true)
                .endObject()
                .endObject()
                .endObject().endObject().string();

        DocumentMapper defaultMapper = parser.parse(mapping);

        ParsedDocument parsedDoc = defaultMapper.parse("test", "type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("str1", "1234")
                .field("str2", "1234")
                .field("str3", "1234")
                .field("str4", "1234")
                .field("str5", "1234")
                .endObject()
                .bytes());
        final Document doc = parsedDoc.rootDoc();
        assertEquals(DocValuesType.NONE, docValuesType(doc, "str1"));
        assertEquals(DocValuesType.SORTED_SET, docValuesType(doc, "str2"));
        assertEquals(DocValuesType.NONE, docValuesType(doc, "str3"));
        assertEquals(DocValuesType.NONE, docValuesType(doc, "str4"));
        assertEquals(DocValuesType.SORTED_SET, docValuesType(doc, "str5"));

    }

    // TODO: this function shouldn't be necessary.  parsing should just add a single field that is indexed and dv
    public static DocValuesType docValuesType(Document document, String fieldName) {
        for (IndexableField field : document.getFields(fieldName)) {
            if (field.fieldType().docValuesType() != DocValuesType.NONE) {
                return field.fieldType().docValuesType();
            }
        }
        return DocValuesType.NONE;
    }

    public void testDisableNorms() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string").endObject().endObject()
                .endObject().endObject().string();

        DocumentMapper defaultMapper = parser.parse(mapping);

        ParsedDocument doc = defaultMapper.parse("test", "type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "1234")
                .endObject()
                .bytes());

        IndexableFieldType fieldType = doc.rootDoc().getField("field").fieldType();
        assertEquals(false, fieldType.omitNorms());

        String updatedMapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string").startObject("norms").field("enabled", false).endObject()
                .endObject().endObject().endObject().endObject().string();
        MergeResult mergeResult = defaultMapper.merge(parser.parse(updatedMapping).mapping(), false, false);
        assertFalse(Arrays.toString(mergeResult.buildConflicts()), mergeResult.hasConflicts());

        doc = defaultMapper.parse("test", "type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "1234")
                .endObject()
                .bytes());

        fieldType = doc.rootDoc().getField("field").fieldType();
        assertEquals(true, fieldType.omitNorms());

        updatedMapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties").startObject("field").field("type", "string").startObject("norms").field("enabled", true).endObject()
                .endObject().endObject().endObject().endObject().string();
        mergeResult = defaultMapper.merge(parser.parse(updatedMapping).mapping(), true, false);
        assertTrue(mergeResult.hasConflicts());
        assertEquals(1, mergeResult.buildConflicts().length);
        assertTrue(mergeResult.buildConflicts()[0].contains("different [omit_norms]"));
    }

    /**
     * Test that expected exceptions are thrown when creating a new index with position_offset_gap
     */
    public void testPositionOffsetGapDeprecation() throws Exception {
        // test deprecation exceptions on newly created indexes
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties")
                .startObject("field1")
                .field("type", "string")
                .field("position_increment_gap", 10)
                .endObject()
                .startObject("field2")
                .field("type", "string")
                .field("position_offset_gap", 50)
                .field("analyzer", "standard")
                .endObject().endObject().endObject().endObject().string();
        try {
            parser.parse(mapping);
            fail("Mapping definition should fail with the position_offset_gap setting");
        }catch (MapperParsingException e) {
            assertEquals(e.getMessage(), "Mapping definition for [field2] has unsupported parameters:  [position_offset_gap : 50]");
        }
    }

    /**
     * Test backward compatibility
     */
    public void testBackwardCompatible() throws Exception {

        Settings settings = Settings.settingsBuilder().put(IndexMetaData.SETTING_VERSION_CREATED, VersionUtils.randomVersionBetween(random(), Version.V_1_0_0,
                                         Version.V_1_7_1)).build();

        DocumentMapperParser parser = createIndex("backward_compatible_index", settings).mapperService().documentMapperParser();

        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("properties")
                .startObject("field1")
                .field("type", "string")
                .field("position_offset_gap", 10)
                .endObject().endObject().endObject().endObject().string();
        parser.parse(mapping);

        assertThat(parser.parse(mapping).mapping().toString(), containsString("\"position_increment_gap\":10"));
    }
}
