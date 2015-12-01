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

package org.elasticsearch.index.mapper.timestamp;

import org.apache.lucene.index.IndexOptions;
import org.elasticsearch.Version;
import org.elasticsearch.action.TimestampParsingException;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.joda.Joda;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.mapper.DocumentMapper;
import org.elasticsearch.index.mapper.DocumentMapperParser;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.index.mapper.MergeResult;
import org.elasticsearch.index.mapper.ParsedDocument;
import org.elasticsearch.index.mapper.SourceToParse;
import org.elasticsearch.index.mapper.internal.TimestampFieldMapper;
import org.elasticsearch.test.ESSingleNodeTestCase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.Version.V_1_5_0;
import static org.elasticsearch.Version.V_2_0_0_beta1;
import static org.elasticsearch.common.settings.Settings.settingsBuilder;
import static org.elasticsearch.test.VersionUtils.randomVersion;
import static org.elasticsearch.test.VersionUtils.randomVersionBetween;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 */
public class TimestampMappingTests extends ESSingleNodeTestCase {
    Settings BWC_SETTINGS = Settings.builder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.V_1_4_2.id).build();

    public void testSimpleDisabled() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type").endObject().string();
        DocumentMapper docMapper = createIndex("test").mapperService().documentMapperParser().parse(mapping);
        BytesReference source = XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "value")
                .endObject()
                .bytes();
        ParsedDocument doc = docMapper.parse(SourceToParse.source(source).type("type").id("1").timestamp(1));

        assertThat(doc.rootDoc().getField("_timestamp"), equalTo(null));
    }

    public void testEnabled() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp").field("enabled", "yes").endObject()
                .endObject().endObject().string();
        DocumentMapper docMapper = createIndex("test").mapperService().documentMapperParser().parse(mapping);
        BytesReference source = XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "value")
                .endObject()
                .bytes();
        ParsedDocument doc = docMapper.parse(SourceToParse.source(source).type("type").id("1").timestamp(1));

        assertThat(doc.rootDoc().getField("_timestamp").fieldType().stored(), equalTo(true));
        assertNotSame(IndexOptions.NONE, doc.rootDoc().getField("_timestamp").fieldType().indexOptions());
        assertThat(doc.rootDoc().getField("_timestamp").tokenStream(docMapper.mappers().indexAnalyzer(), null), notNullValue());
    }

    public void testDefaultValues() throws Exception {
        for (Version version : Arrays.asList(V_1_5_0, V_2_0_0_beta1, randomVersion(random()))) {
            for (String mapping : Arrays.asList(
                    XContentFactory.jsonBuilder().startObject().startObject("type").endObject().string(),
                    XContentFactory.jsonBuilder().startObject().startObject("type").startObject("_timestamp").endObject().endObject().string())) {
                DocumentMapper docMapper = createIndex("test", Settings.builder().put(IndexMetaData.SETTING_VERSION_CREATED, version).build()).mapperService().documentMapperParser().parse(mapping);
                assertThat(docMapper.timestampFieldMapper().enabled(), equalTo(TimestampFieldMapper.Defaults.ENABLED.enabled));
                assertThat(docMapper.timestampFieldMapper().fieldType().stored(), equalTo(version.onOrAfter(Version.V_2_0_0_beta1)));
                assertThat(docMapper.timestampFieldMapper().fieldType().indexOptions(), equalTo(TimestampFieldMapper.Defaults.FIELD_TYPE.indexOptions()));
                assertThat(docMapper.timestampFieldMapper().path(), equalTo(TimestampFieldMapper.Defaults.PATH));
                assertThat(docMapper.timestampFieldMapper().fieldType().hasDocValues(), equalTo(version.onOrAfter(Version.V_2_0_0_beta1)));
                String expectedFormat = version.onOrAfter(Version.V_2_0_0_beta1) ? TimestampFieldMapper.DEFAULT_DATE_TIME_FORMAT :
                        TimestampFieldMapper.Defaults.DATE_TIME_FORMATTER_BEFORE_2_0.format();
                assertThat(docMapper.timestampFieldMapper().fieldType().dateTimeFormatter().format(), equalTo(expectedFormat));
                assertAcked(client().admin().indices().prepareDelete("test").execute().get());
            }
        }
    }

    public void testBackcompatSetValues() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp")
                .field("enabled", "yes").field("store", "no").field("index", "no")
                .field("path", "timestamp").field("format", "year")
                .field("doc_values", true)
                .endObject()
                .endObject().endObject().string();
        DocumentMapper docMapper = createIndex("test", BWC_SETTINGS).mapperService().documentMapperParser().parse(mapping);
        assertThat(docMapper.timestampFieldMapper().enabled(), equalTo(true));
        assertThat(docMapper.timestampFieldMapper().fieldType().stored(), equalTo(false));
        assertEquals(IndexOptions.NONE, docMapper.timestampFieldMapper().fieldType().indexOptions());
        assertThat(docMapper.timestampFieldMapper().path(), equalTo("timestamp"));
        assertThat(docMapper.timestampFieldMapper().fieldType().dateTimeFormatter().format(), equalTo("year"));
        assertThat(docMapper.timestampFieldMapper().fieldType().hasDocValues(), equalTo(true));
    }

    public void testThatDisablingDuringMergeIsWorking() throws Exception {
        String enabledMapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp").field("enabled", true).endObject()
                .endObject().endObject().string();
        DocumentMapperParser parser = createIndex("test").mapperService().documentMapperParser();
        DocumentMapper enabledMapper = parser.parse(enabledMapping);

        String disabledMapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp").field("enabled", false).endObject()
                .endObject().endObject().string();
        DocumentMapper disabledMapper = parser.parse(disabledMapping);

        enabledMapper.merge(disabledMapper.mapping(), false, false);

        assertThat(enabledMapper.timestampFieldMapper().enabled(), is(false));
    }

    // issue 3174
    public void testThatSerializationWorksCorrectlyForIndexField() throws Exception {
        String enabledMapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp").field("enabled", true).field("store", "yes").field("index", "no").endObject()
                .endObject().endObject().string();
        DocumentMapper enabledMapper = createIndex("test", BWC_SETTINGS).mapperService().documentMapperParser().parse(enabledMapping);

        XContentBuilder builder = JsonXContent.contentBuilder().startObject();
        enabledMapper.timestampFieldMapper().toXContent(builder, ToXContent.EMPTY_PARAMS).endObject();
        builder.close();
        Map<String, Object> serializedMap;
        try (XContentParser parser = JsonXContent.jsonXContent.createParser(builder.bytes())) {
            serializedMap = parser.map();
        }
        assertThat(serializedMap, hasKey("_timestamp"));
        assertThat(serializedMap.get("_timestamp"), instanceOf(Map.class));
        Map<String, Object> timestampConfiguration = (Map<String, Object>) serializedMap.get("_timestamp");
        assertThat(timestampConfiguration, hasKey("index"));
        assertThat(timestampConfiguration.get("index").toString(), is("no"));
    }

    // Issue 4718: was throwing a TimestampParsingException: failed to parse timestamp [null]
    public void testBackcompatPathMissingDefaultValue() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp")
                    .field("enabled", "yes")
                    .field("path", "timestamp")
                    .field("ignore_missing", false)
                .endObject()
                .endObject().endObject();
        XContentBuilder doc = XContentFactory.jsonBuilder()
                .startObject()
                    .field("foo", "bar")
                .endObject();

        MetaData metaData = MetaData.builder().build();
        DocumentMapper docMapper = createIndex("test", BWC_SETTINGS).mapperService().documentMapperParser().parse(mapping.string());

        MappingMetaData mappingMetaData = new MappingMetaData(docMapper);

        IndexRequest request = new IndexRequest("test", "type", "1").source(doc);
        try {
            request.process(metaData, mappingMetaData, true, "test");
            fail();
        } catch (TimestampParsingException e) {
            assertThat(e.getDetailedMessage(), containsString("timestamp is required by mapping"));
        }
    }

    // Issue 4718: was throwing a TimestampParsingException: failed to parse timestamp [null]
    public void testTimestampDefaultValue() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp")
                    .field("enabled", "yes")
                .endObject()
                .endObject().endObject();
        XContentBuilder doc = XContentFactory.jsonBuilder()
                .startObject()
                    .field("foo", "bar")
                .endObject();

        MetaData metaData = MetaData.builder().build();
        DocumentMapper docMapper = createIndex("test").mapperService().documentMapperParser().parse(mapping.string());

        MappingMetaData mappingMetaData = new MappingMetaData(docMapper);

        IndexRequest request = new IndexRequest("test", "type", "1").source(doc);
        request.process(metaData, mappingMetaData, true, "test");
        assertThat(request.timestamp(), notNullValue());

        // We should have less than one minute (probably some ms)
        long delay = System.currentTimeMillis() - Long.parseLong(request.timestamp());
        assertThat(delay, lessThanOrEqualTo(60000L));
    }

    // Issue 4718: was throwing a TimestampParsingException: failed to parse timestamp [null]
    public void testBackcompatPathMissingDefaultToEpochValue() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp")
                    .field("enabled", "yes")
                    .field("path", "timestamp")
                    .field("default", "1970-01-01")
                    .field("format", "YYYY-MM-dd")
                .endObject()
                .endObject().endObject();
        XContentBuilder doc = XContentFactory.jsonBuilder()
                .startObject()
                    .field("foo", "bar")
                .endObject();

        DocumentMapper docMapper = createIndex("test", BWC_SETTINGS).mapperService().documentMapperParser().parse(mapping.string());
        MetaData metaData = client().admin().cluster().prepareState().get().getState().getMetaData();

        MappingMetaData mappingMetaData = new MappingMetaData(docMapper);

        IndexRequest request = new IndexRequest("test", "type", "1").source(doc);
        request.process(metaData, mappingMetaData, true, "test");
        assertThat(request.timestamp(), notNullValue());
        assertThat(request.timestamp(), is(MappingMetaData.Timestamp.parseStringTimestamp("1970-01-01", Joda.forPattern("YYYY-MM-dd"), Version.CURRENT)));
    }

    // Issue 4718: was throwing a TimestampParsingException: failed to parse timestamp [null]
    public void testTimestampMissingDefaultToEpochValue() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp")
                    .field("enabled", "yes")
                    .field("default", "1970-01-01")
                    .field("format", "YYYY-MM-dd")
                .endObject()
                .endObject().endObject();
        XContentBuilder doc = XContentFactory.jsonBuilder()
                .startObject()
                    .field("foo", "bar")
                .endObject();

        DocumentMapper docMapper = createIndex("test").mapperService().documentMapperParser().parse(mapping.string());
        MetaData metaData = client().admin().cluster().prepareState().get().getState().getMetaData();

        MappingMetaData mappingMetaData = new MappingMetaData(docMapper);

        IndexRequest request = new IndexRequest("test", "type", "1").source(doc);
        request.process(metaData, mappingMetaData, true, "test");
        assertThat(request.timestamp(), notNullValue());
        assertThat(request.timestamp(), is(MappingMetaData.Timestamp.parseStringTimestamp("1970-01-01", Joda.forPattern("YYYY-MM-dd"), Version.CURRENT)));
    }

    // Issue 4718: was throwing a TimestampParsingException: failed to parse timestamp [null]
    public void testBackcompatPathMissingNowDefaultValue() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp")
                    .field("enabled", "yes")
                    .field("path", "timestamp")
                    .field("default", "now")
                    .field("format", "YYYY-MM-dd")
                .endObject()
                .endObject().endObject();
        XContentBuilder doc = XContentFactory.jsonBuilder()
                .startObject()
                    .field("foo", "bar")
                .endObject();

        MetaData metaData = MetaData.builder().build();
        DocumentMapper docMapper = createIndex("test", BWC_SETTINGS).mapperService().documentMapperParser().parse(mapping.string());

        MappingMetaData mappingMetaData = new MappingMetaData(docMapper);

        IndexRequest request = new IndexRequest("test", "type", "1").source(doc);
        request.process(metaData, mappingMetaData, true, "test");
        assertThat(request.timestamp(), notNullValue());

        // We should have less than one minute (probably some ms)
        long delay = System.currentTimeMillis() - Long.parseLong(request.timestamp());
        assertThat(delay, lessThanOrEqualTo(60000L));
    }

    // Issue 4718: was throwing a TimestampParsingException: failed to parse timestamp [null]
    public void testTimestampMissingNowDefaultValue() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp")
                    .field("enabled", "yes")
                    .field("default", "now")
                    .field("format", "YYYY-MM-dd")
                .endObject()
                .endObject().endObject();
        XContentBuilder doc = XContentFactory.jsonBuilder()
                .startObject()
                    .field("foo", "bar")
                .endObject();

        MetaData metaData = MetaData.builder().build();
        DocumentMapper docMapper = createIndex("test").mapperService().documentMapperParser().parse(mapping.string());

        MappingMetaData mappingMetaData = new MappingMetaData(docMapper);

        IndexRequest request = new IndexRequest("test", "type", "1").source(doc);
        request.process(metaData, mappingMetaData, true, "test");
        assertThat(request.timestamp(), notNullValue());

        // We should have less than one minute (probably some ms)
        long delay = System.currentTimeMillis() - Long.parseLong(request.timestamp());
        assertThat(delay, lessThanOrEqualTo(60000L));
    }

    // Issue 4718: was throwing a TimestampParsingException: failed to parse timestamp [null]
    public void testPathMissingWithForcedNullDefaultShouldFail() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp")
                    .field("enabled", "yes")
                    .field("path", "timestamp")
                    .field("default", (String) null)
                .endObject()
                .endObject().endObject();
        try {
            createIndex("test").mapperService().documentMapperParser().parse(mapping.string());
            fail("we should reject the mapping with a TimestampParsingException: default timestamp can not be set to null");
        } catch (TimestampParsingException e) {
            assertThat(e.getDetailedMessage(), containsString("default timestamp can not be set to null"));
        }
    }

    // Issue 4718: was throwing a TimestampParsingException: failed to parse timestamp [null]
    public void testBackcompatPathMissingShouldFail() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp")
                    .field("enabled", "yes")
                    .field("path", "timestamp")
                    .field("ignore_missing", false)
                .endObject()
                .endObject().endObject();
        XContentBuilder doc = XContentFactory.jsonBuilder()
                .startObject()
                    .field("foo", "bar")
                .endObject();

        MetaData metaData = MetaData.builder().build();
        DocumentMapper docMapper = createIndex("test", BWC_SETTINGS).mapperService().documentMapperParser().parse(mapping.string());

        MappingMetaData mappingMetaData = new MappingMetaData(docMapper);

        IndexRequest request = new IndexRequest("test", "type", "1").source(doc);
        try {
            request.process(metaData, mappingMetaData, true, "test");
            fail("we should reject the mapping with a TimestampParsingException: timestamp is required by mapping");
        } catch (TimestampParsingException e) {
            assertThat(e.getDetailedMessage(), containsString("timestamp is required by mapping"));
        }
    }

    // Issue 4718: was throwing a TimestampParsingException: failed to parse timestamp [null]
    public void testTimestampMissingWithForcedNullDefaultShouldFail() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp")
                    .field("enabled", "yes")
                    .field("default", (String) null)
                .endObject()
                .endObject().endObject();

        try {
            createIndex("test").mapperService().documentMapperParser().parse(mapping.string());
            fail("we should reject the mapping with a TimestampParsingException: default timestamp can not be set to null");
        } catch (TimestampParsingException e) {
            assertThat(e.getDetailedMessage(), containsString("default timestamp can not be set to null"));
        }
    }

    // Issue 4718: was throwing a TimestampParsingException: failed to parse timestamp [null]
    public void testTimestampDefaultAndIgnore() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp")
                    .field("enabled", "yes")
                    .field("default", "1971-12-26")
                    .field("ignore_missing", false)
                .endObject()
                .endObject().endObject();

        try {
            createIndex("test").mapperService().documentMapperParser().parse(mapping.string());
            fail("we should reject the mapping with a TimestampParsingException: default timestamp can not be set with ignore_missing set to false");
        } catch (TimestampParsingException e) {
            assertThat(e.getDetailedMessage(), containsString("default timestamp can not be set with ignore_missing set to false"));
        }
    }

    // Issue 4718: was throwing a TimestampParsingException: failed to parse timestamp [null]
    public void testTimestampMissingShouldNotFail() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp")
                    .field("enabled", "yes")
                .endObject()
                .endObject().endObject();
        XContentBuilder doc = XContentFactory.jsonBuilder()
                .startObject()
                    .field("foo", "bar")
                .endObject();

        MetaData metaData = MetaData.builder().build();
        DocumentMapper docMapper = createIndex("test").mapperService().documentMapperParser().parse(mapping.string());

        MappingMetaData mappingMetaData = new MappingMetaData(docMapper);

        IndexRequest request = new IndexRequest("test", "type", "1").source(doc);
        request.process(metaData, mappingMetaData, true, "test");

        assertThat(request.timestamp(), notNullValue());

        // We should have less than one minute (probably some ms)
        long delay = System.currentTimeMillis() - Long.parseLong(request.timestamp());
        assertThat(delay, lessThanOrEqualTo(60000L));
    }

    public void testDefaultTimestampStream() throws IOException {
        // Testing null value for default timestamp
        {
            MappingMetaData.Timestamp timestamp = new MappingMetaData.Timestamp(true, null,
                    TimestampFieldMapper.DEFAULT_DATE_TIME_FORMAT, null, null);
            MappingMetaData expected = new MappingMetaData("type", new CompressedXContent("{}".getBytes(StandardCharsets.UTF_8)),
                    new MappingMetaData.Id(null), new MappingMetaData.Routing(false, null), timestamp, false);

            BytesStreamOutput out = new BytesStreamOutput();
            expected.writeTo(out);
            out.close();
            BytesReference bytes = out.bytes();

            MappingMetaData metaData = MappingMetaData.PROTO.readFrom(StreamInput.wrap(bytes));

            assertThat(metaData, is(expected));
        }

        // Testing "now" value for default timestamp
        {
            MappingMetaData.Timestamp timestamp = new MappingMetaData.Timestamp(true, null,
                    TimestampFieldMapper.DEFAULT_DATE_TIME_FORMAT, "now", null);
            MappingMetaData expected = new MappingMetaData("type", new CompressedXContent("{}".getBytes(StandardCharsets.UTF_8)),
                    new MappingMetaData.Id(null), new MappingMetaData.Routing(false, null), timestamp, false);

            BytesStreamOutput out = new BytesStreamOutput();
            expected.writeTo(out);
            out.close();
            BytesReference bytes = out.bytes();

            MappingMetaData metaData = MappingMetaData.PROTO.readFrom(StreamInput.wrap(bytes));

            assertThat(metaData, is(expected));
        }

        // Testing "ignore_missing" value for default timestamp
        {
            MappingMetaData.Timestamp timestamp = new MappingMetaData.Timestamp(true, null,
                    TimestampFieldMapper.DEFAULT_DATE_TIME_FORMAT, "now", false);
            MappingMetaData expected = new MappingMetaData("type", new CompressedXContent("{}".getBytes(StandardCharsets.UTF_8)),
                    new MappingMetaData.Id(null), new MappingMetaData.Routing(false, null), timestamp, false);

            BytesStreamOutput out = new BytesStreamOutput();
            expected.writeTo(out);
            out.close();
            BytesReference bytes = out.bytes();

            MappingMetaData metaData = MappingMetaData.PROTO.readFrom(StreamInput.wrap(bytes));

            assertThat(metaData, is(expected));
        }
    }

    public void testMergingFielddataLoadingWorks() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp").field("enabled", randomBoolean()).startObject("fielddata").field("loading", "lazy").field("format", "doc_values").endObject().field("store", "yes").endObject()
                .endObject().endObject().string();
        Settings indexSettings = Settings.builder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.V_1_4_2.id).build();
        DocumentMapperParser parser = createIndex("test", indexSettings).mapperService().documentMapperParser();

        DocumentMapper docMapper = parser.parse(mapping);
        assertThat(docMapper.timestampFieldMapper().fieldType().fieldDataType().getLoading(), equalTo(MappedFieldType.Loading.LAZY));
        assertThat(docMapper.timestampFieldMapper().fieldType().fieldDataType().getFormat(indexSettings), equalTo("doc_values"));
        mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp").field("enabled", randomBoolean()).startObject("fielddata").field("loading", "eager").field("format", "array").endObject().field("store", "yes").endObject()
                .endObject().endObject().string();

        MergeResult mergeResult = docMapper.merge(parser.parse(mapping).mapping(), false, false);
        assertThat(mergeResult.buildConflicts().length, equalTo(0));
        assertThat(docMapper.timestampFieldMapper().fieldType().fieldDataType().getLoading(), equalTo(MappedFieldType.Loading.EAGER));
        assertThat(docMapper.timestampFieldMapper().fieldType().fieldDataType().getFormat(indexSettings), equalTo("array"));
    }

    public void testParsingNotDefaultTwiceDoesNotChangeMapping() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp")
                    .field("enabled", true)
                    .field("default", "1970-01-01")
                .endObject().endObject().endObject().string();
        DocumentMapperParser parser = createIndex("test").mapperService().documentMapperParser();

        DocumentMapper docMapper = parser.parse(mapping);
        docMapper = parser.parse(docMapper.mappingSource().string());
        assertThat(docMapper.mappingSource().string(), equalTo(mapping));
    }

    public void testBackcompatParsingTwiceDoesNotChangeTokenizeValue() throws Exception {
        String[] index_options = {"no", "analyzed", "not_analyzed"};
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp").field("enabled", true)
                .field("index", index_options[randomInt(2)])
                .field("store", true)
                .field("path", "foo")
                .field("default", "1970-01-01")
                .startObject("fielddata").field("format", "doc_values").endObject()
                .endObject()
                .startObject("properties")
                .endObject()
                .endObject().endObject().string();
        DocumentMapperParser parser = createIndex("test", BWC_SETTINGS).mapperService().documentMapperParser();

        DocumentMapper docMapper = parser.parse(mapping);
        boolean tokenized = docMapper.timestampFieldMapper().fieldType().tokenized();
        docMapper = parser.parse(docMapper.mappingSource().string());
        assertThat(tokenized, equalTo(docMapper.timestampFieldMapper().fieldType().tokenized()));
    }

    public void testMergingConflicts() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp").field("enabled", true)
                .startObject("fielddata").field("format", "doc_values").endObject()
                .field("store", "yes")
                .field("index", "analyzed")
                .field("path", "foo")
                .field("default", "1970-01-01")
                .endObject()
                .endObject().endObject().string();
        Settings indexSettings = Settings.builder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.V_1_4_2.id).build();
        DocumentMapperParser parser = createIndex("test", indexSettings).mapperService().documentMapperParser();

        DocumentMapper docMapper = parser.parse(mapping);
        assertThat(docMapper.timestampFieldMapper().fieldType().fieldDataType().getLoading(), equalTo(MappedFieldType.Loading.LAZY));
        mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp").field("enabled", false)
                .startObject("fielddata").field("format", "array").endObject()
                .field("store", "no")
                .field("index", "no")
                .field("path", "bar")
                .field("default", "1970-01-02")
                .endObject()
                .endObject().endObject().string();

        MergeResult mergeResult = docMapper.merge(parser.parse(mapping).mapping(), true, false);
        List<String> expectedConflicts = new ArrayList<>(Arrays.asList(
            "mapper [_timestamp] has different [index] values",
            "mapper [_timestamp] has different [store] values",
            "Cannot update default in _timestamp value. Value is 1970-01-01 now encountering 1970-01-02",
            "Cannot update path in _timestamp value. Value is foo path in merged mapping is bar"));

        for (String conflict : mergeResult.buildConflicts()) {
            assertTrue("found unexpected conflict [" + conflict + "]", expectedConflicts.remove(conflict));
        }
        assertTrue("missing conflicts: " + Arrays.toString(expectedConflicts.toArray()), expectedConflicts.isEmpty());
        assertThat(docMapper.timestampFieldMapper().fieldType().fieldDataType().getLoading(), equalTo(MappedFieldType.Loading.LAZY));
        assertTrue(docMapper.timestampFieldMapper().enabled());
        assertThat(docMapper.timestampFieldMapper().fieldType().fieldDataType().getFormat(indexSettings), equalTo("doc_values"));
    }

    public void testBackcompatMergingConflictsForIndexValues() throws Exception {
        List<String> indexValues = new ArrayList<>();
        indexValues.add("analyzed");
        indexValues.add("no");
        indexValues.add("not_analyzed");
        String mapping = XContentFactory.jsonBuilder().startObject()
                .startObject("type")
                .startObject("_timestamp")
                .field("index", indexValues.remove(randomInt(2)))
                .endObject()
                .endObject().endObject().string();
        DocumentMapperParser parser = createIndex("test", BWC_SETTINGS).mapperService().documentMapperParser();

        DocumentMapper docMapper = parser.parse(mapping);
        mapping = XContentFactory.jsonBuilder().startObject()
                .startObject("type")
                .startObject("_timestamp")
                .field("index", indexValues.remove(randomInt(1)))
                .endObject()
                .endObject().endObject().string();

        MergeResult mergeResult = docMapper.merge(parser.parse(mapping).mapping(), true, false);
        List<String> expectedConflicts = new ArrayList<>();
        expectedConflicts.add("mapper [_timestamp] has different [index] values");
        expectedConflicts.add("mapper [_timestamp] has different [tokenize] values");
        if (indexValues.get(0).equals("not_analyzed") == false) {
            // if the only index value left is not_analyzed, then the doc values setting will be the same, but in the
            // other two cases, it will change
            expectedConflicts.add("mapper [_timestamp] has different [doc_values] values");
        }

        for (String conflict : mergeResult.buildConflicts()) {
            assertThat(conflict, isIn(expectedConflicts));
        }
    }

    /**
     * Test for issue #9223
     */
    public void testInitMappers() throws IOException {
        String mapping = XContentFactory.jsonBuilder().startObject()
                .startObject("type")
                    .startObject("_timestamp")
                        .field("enabled", true)
                        .field("default", (String) null)
                    .endObject()
                .endObject().endObject().string();
        // This was causing a NPE
        new MappingMetaData(new CompressedXContent(mapping));
    }

    public void testBackcompatMergePaths() throws Exception {
        String[] possiblePathValues = {"some_path", "anotherPath", null};
        DocumentMapperParser parser = createIndex("test", BWC_SETTINGS).mapperService().documentMapperParser();
        XContentBuilder mapping1 = XContentFactory.jsonBuilder().startObject()
                .startObject("type")
                .startObject("_timestamp");
        String path1 = possiblePathValues[randomInt(2)];
        if (path1!=null) {
            mapping1.field("path", path1);
        }
        mapping1.endObject()
                .endObject().endObject();
        XContentBuilder mapping2 = XContentFactory.jsonBuilder().startObject()
                .startObject("type")
                .startObject("_timestamp");
        String path2 = possiblePathValues[randomInt(2)];
        if (path2!=null) {
            mapping2.field("path", path2);
        }
        mapping2.endObject()
                .endObject().endObject();

        assertConflict(mapping1.string(), mapping2.string(), parser, (path1 == path2 ? null : "Cannot update path in _timestamp value"));
    }

    void assertConflict(String mapping1, String mapping2, DocumentMapperParser parser, String conflict) throws IOException {
        DocumentMapper docMapper = parser.parse(mapping1);
        docMapper = parser.parse(docMapper.mappingSource().string());
        MergeResult mergeResult = docMapper.merge(parser.parse(mapping2).mapping(), true, false);
        assertThat(mergeResult.buildConflicts().length, equalTo(conflict == null ? 0 : 1));
        if (conflict != null) {
            assertThat(mergeResult.buildConflicts()[0], containsString(conflict));
        }
    }

    public void testBackcompatDocValuesSerialization() throws Exception {
        // default
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("_timestamp")
            .endObject().endObject().endObject().string();
        assertDocValuesSerialization(mapping);

        // just format specified
        mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("_timestamp")
            .startObject("fielddata").field("format", "doc_values").endObject()
            .endObject().endObject().endObject().string();
        assertDocValuesSerialization(mapping);

        // explicitly enabled
        mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("_timestamp")
            .field("doc_values", true)
            .endObject().endObject().endObject().string();
        assertDocValuesSerialization(mapping);

        // explicitly disabled
        mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("_timestamp")
            .field("doc_values", false)
            .endObject().endObject().endObject().string();
        assertDocValuesSerialization(mapping);

        // explicitly enabled, with format
        mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("_timestamp")
            .field("doc_values", true)
            .startObject("fielddata").field("format", "doc_values").endObject()
            .endObject().endObject().endObject().string();
        assertDocValuesSerialization(mapping);

        // explicitly disabled, with format
        mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("_timestamp")
            .field("doc_values", false)
            .startObject("fielddata").field("format", "doc_values").endObject()
            .endObject().endObject().endObject().string();
        assertDocValuesSerialization(mapping);
    }

    void assertDocValuesSerialization(String mapping) throws Exception {
        DocumentMapperParser parser = createIndex("test_doc_values", BWC_SETTINGS).mapperService().documentMapperParser();
        DocumentMapper docMapper = parser.parse(mapping);
        boolean docValues = docMapper.timestampFieldMapper().fieldType().hasDocValues();
        docMapper = parser.parse(docMapper.mappingSource().string());
        assertThat(docMapper.timestampFieldMapper().fieldType().hasDocValues(), equalTo(docValues));
        assertAcked(client().admin().indices().prepareDelete("test_doc_values"));
    }

    public void testBackcompatPath() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("_timestamp").field("enabled", true).field("path", "custom_timestamp").endObject()
            .endObject().endObject().string();
        DocumentMapper docMapper = createIndex("test", BWC_SETTINGS).mapperService().documentMapperParser().parse(mapping);
        MetaData metaData = client().admin().cluster().prepareState().get().getState().getMetaData();

        XContentBuilder doc = XContentFactory.jsonBuilder().startObject().field("custom_timestamp", 1).endObject();
        MappingMetaData mappingMetaData = new MappingMetaData(docMapper);
        IndexRequest request = new IndexRequest("test", "type", "1").source(doc);
        request.process(metaData, mappingMetaData, true, "test");

        assertThat(request.timestamp(), is("1"));
    }

    public void testIncludeInObjectBackcompat() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("_timestamp").field("enabled", true).field("default", "1970").field("format", "YYYY").endObject()
            .endObject().endObject().string();
        Settings settings = Settings.builder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.V_1_4_2.id).build();
        DocumentMapper docMapper = createIndex("test", settings).mapperService().documentMapperParser().parse(mapping);
        MetaData metaData = client().admin().cluster().prepareState().get().getState().getMetaData();

        XContentBuilder doc = XContentFactory.jsonBuilder().startObject().field("_timestamp", 2000000).endObject();
        MappingMetaData mappingMetaData = new MappingMetaData(docMapper);
        IndexRequest request = new IndexRequest("test", "type", "1").source(doc);
        request.process(metaData, mappingMetaData, true, "test");

        // _timestamp in a document never worked, so backcompat is ignoring the field
        assertEquals(MappingMetaData.Timestamp.parseStringTimestamp("1970", Joda.forPattern("YYYY"), Version.V_1_4_2), request.timestamp());
        assertNull(docMapper.parse("test", "type", "1", doc.bytes()).rootDoc().get("_timestamp"));
    }

    public void testIncludeInObjectNotAllowed() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
            .startObject("_timestamp").field("enabled", true).field("default", "1970").field("format", "YYYY").endObject()
            .endObject().endObject().string();
        DocumentMapper docMapper = createIndex("test").mapperService().documentMapperParser().parse(mapping);

        try {
            docMapper.parse("test", "type", "1", XContentFactory.jsonBuilder()
                .startObject().field("_timestamp", 2000000).endObject().bytes());
            fail("Expected failure to parse metadata field");
        } catch (MapperParsingException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Field [_timestamp] is a metadata field and cannot be added inside a document"));
        }
    }

    public void testThatEpochCanBeIgnoredWithCustomFormat() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp").field("enabled", true).field("format", "yyyyMMddHH").endObject()
            .endObject().endObject().string();
        DocumentMapper docMapper = createIndex("test").mapperService().documentMapperParser().parse(mapping);
        MetaData metaData = client().admin().cluster().prepareState().get().getState().getMetaData();

        XContentBuilder doc = XContentFactory.jsonBuilder().startObject().endObject();
        IndexRequest request = new IndexRequest("test", "type", "1").source(doc).timestamp("2015060210");
        MappingMetaData mappingMetaData = new MappingMetaData(docMapper);
        request.process(metaData, mappingMetaData, true, "test");

        assertThat(request.timestamp(), is("1433239200000"));
    }

    public void testThatIndicesBefore2xMustSupportUnixTimestampsInAnyDateFormat() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type")
                .startObject("_timestamp").field("enabled", true).field("format", "dateOptionalTime").endObject()
                .endObject().endObject().string();

        BytesReference source = XContentFactory.jsonBuilder().startObject().field("field", "value").endObject().bytes();

        //
        // test with older versions
        Settings oldSettings = settingsBuilder().put(IndexMetaData.SETTING_VERSION_CREATED, randomVersionBetween(random(), Version.V_0_90_0, Version.V_1_6_0)).build();
        DocumentMapper docMapper = createIndex("old-index", oldSettings).mapperService().documentMapperParser().parse(mapping);

        MetaData metaData = client().admin().cluster().prepareState().get().getState().getMetaData();

        // both index request are successfully processed
        IndexRequest oldIndexDateIndexRequest = new IndexRequest("old-index", "type", "1").source(source).timestamp("1970-01-01");
        oldIndexDateIndexRequest.process(metaData, new MappingMetaData(docMapper), true, "old-index");
        IndexRequest oldIndexTimestampIndexRequest = new IndexRequest("old-index", "type", "1").source(source).timestamp("1234567890");
        oldIndexTimestampIndexRequest.process(metaData, new MappingMetaData(docMapper), true, "old-index");

        //
        // test with 2.x
        DocumentMapper currentMapper = createIndex("new-index").mapperService().documentMapperParser().parse(mapping);
        MetaData newMetaData = client().admin().cluster().prepareState().get().getState().getMetaData();

        // this works with 2.x
        IndexRequest request = new IndexRequest("new-index", "type", "1").source(source).timestamp("1970-01-01");
        request.process(newMetaData, new MappingMetaData(currentMapper), true, "new-index");

        // this fails with 2.x
        request = new IndexRequest("new-index", "type", "1").source(source).timestamp("1234567890");
        try {
            request.process(newMetaData, new MappingMetaData(currentMapper), true, "new-index");
        } catch (Exception e) {
            assertThat(e.getCause(), instanceOf(IllegalArgumentException.class));
            assertThat(e.getMessage(), containsString("failed to parse timestamp [1234567890]"));
        }
    }
}
