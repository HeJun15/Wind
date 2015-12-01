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

package org.elasticsearch.fieldstats;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.action.fieldstats.FieldStats;
import org.elasticsearch.action.fieldstats.FieldStatsResponse;
import org.elasticsearch.action.fieldstats.IndexConstraint;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.mapper.core.DateFieldMapper;
import org.elasticsearch.test.ESSingleNodeTestCase;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.elasticsearch.action.fieldstats.IndexConstraint.Comparison.*;
import static org.elasticsearch.action.fieldstats.IndexConstraint.Property.MAX;
import static org.elasticsearch.action.fieldstats.IndexConstraint.Property.MIN;
import static org.hamcrest.Matchers.*;

/**
 */
public class FieldStatsTests extends ESSingleNodeTestCase {

    public void testByte() {
        testNumberRange("field1", "byte", 12, 18);
        testNumberRange("field1", "byte", -5, 5);
        testNumberRange("field1", "byte", -18, -12);
    }

    public void testShort() {
        testNumberRange("field1", "short", 256, 266);
        testNumberRange("field1", "short", -5, 5);
        testNumberRange("field1", "short", -266, -256);
    }

    public void testInteger() {
        testNumberRange("field1", "integer", 56880, 56890);
        testNumberRange("field1", "integer", -5, 5);
        testNumberRange("field1", "integer", -56890, -56880);
    }

    public void testLong() {
        testNumberRange("field1", "long", 312321312312412l, 312321312312422l);
        testNumberRange("field1", "long", -5, 5);
        testNumberRange("field1", "long", -312321312312422l, -312321312312412l);
    }

    public void testString() {
        createIndex("test", Settings.EMPTY, "field", "value", "type=string");
        for (int value = 0; value <= 10; value++) {
            client().prepareIndex("test", "test").setSource("field", String.format(Locale.ENGLISH, "%03d", value)).get();
        }
        client().admin().indices().prepareRefresh().get();

        FieldStatsResponse result = client().prepareFieldStats().setFields("field").get();
        assertThat(result.getAllFieldStats().get("field").getMaxDoc(), equalTo(11l));
        assertThat(result.getAllFieldStats().get("field").getDocCount(), equalTo(11l));
        assertThat(result.getAllFieldStats().get("field").getDensity(), equalTo(100));
        assertThat(result.getAllFieldStats().get("field").getMinValue(), equalTo(new BytesRef(String.format(Locale.ENGLISH, "%03d", 0))));
        assertThat(result.getAllFieldStats().get("field").getMaxValue(), equalTo(new BytesRef(String.format(Locale.ENGLISH, "%03d", 10))));
        assertThat(result.getAllFieldStats().get("field").getMinValueAsString(), equalTo(String.format(Locale.ENGLISH, "%03d", 0)));
        assertThat(result.getAllFieldStats().get("field").getMaxValueAsString(), equalTo(String.format(Locale.ENGLISH, "%03d", 10)));
    }

    public void testDouble() {
        String fieldName = "field";
        createIndex("test", Settings.EMPTY, fieldName, "value", "type=double");
        for (double value = -1; value <= 9; value++) {
            client().prepareIndex("test", "test").setSource(fieldName, value).get();
        }
        client().admin().indices().prepareRefresh().get();

        FieldStatsResponse result = client().prepareFieldStats().setFields(fieldName).get();
        assertThat(result.getAllFieldStats().get(fieldName).getMaxDoc(), equalTo(11l));
        assertThat(result.getAllFieldStats().get(fieldName).getDocCount(), equalTo(11l));
        assertThat(result.getAllFieldStats().get(fieldName).getDensity(), equalTo(100));
        assertThat(result.getAllFieldStats().get(fieldName).getMinValue(), equalTo(-1d));
        assertThat(result.getAllFieldStats().get(fieldName).getMaxValue(), equalTo(9d));
        assertThat(result.getAllFieldStats().get(fieldName).getMinValueAsString(), equalTo(Double.toString(-1)));
    }

    public void testFloat() {
        String fieldName = "field";
        createIndex("test", Settings.EMPTY, fieldName, "value", "type=float");
        for (float value = -1; value <= 9; value++) {
            client().prepareIndex("test", "test").setSource(fieldName, value).get();
        }
        client().admin().indices().prepareRefresh().get();

        FieldStatsResponse result = client().prepareFieldStats().setFields(fieldName).get();
        assertThat(result.getAllFieldStats().get(fieldName).getMaxDoc(), equalTo(11l));
        assertThat(result.getAllFieldStats().get(fieldName).getDocCount(), equalTo(11l));
        assertThat(result.getAllFieldStats().get(fieldName).getDensity(), equalTo(100));
        assertThat(result.getAllFieldStats().get(fieldName).getMinValue(), equalTo(-1.0));
        assertThat(result.getAllFieldStats().get(fieldName).getMaxValue(), equalTo(9.0));
        assertThat(result.getAllFieldStats().get(fieldName).getMinValueAsString(), equalTo(Float.toString(-1)));
        assertThat(result.getAllFieldStats().get(fieldName).getMaxValueAsString(), equalTo(Float.toString(9)));
    }

    private void testNumberRange(String fieldName, String fieldType, long min, long max) {
        createIndex("test", Settings.EMPTY, fieldName, "value", "type=" + fieldType);
        for (long value = min; value <= max; value++) {
            client().prepareIndex("test", "test").setSource(fieldName, value).get();
        }
        client().admin().indices().prepareRefresh().get();

        FieldStatsResponse result = client().prepareFieldStats().setFields(fieldName).get();
        long numDocs = max - min + 1;
        assertThat(result.getAllFieldStats().get(fieldName).getMaxDoc(), equalTo(numDocs));
        assertThat(result.getAllFieldStats().get(fieldName).getDocCount(), equalTo(numDocs));
        assertThat(result.getAllFieldStats().get(fieldName).getDensity(), equalTo(100));
        assertThat(result.getAllFieldStats().get(fieldName).getMinValue(), equalTo(min));
        assertThat(result.getAllFieldStats().get(fieldName).getMaxValue(), equalTo(max));
        assertThat(result.getAllFieldStats().get(fieldName).getMinValueAsString(), equalTo(java.lang.Long.toString(min)));
        assertThat(result.getAllFieldStats().get(fieldName).getMaxValueAsString(), equalTo(java.lang.Long.toString(max)));
        client().admin().indices().prepareDelete("test").get();
    }

    public void testMerge() {
        List<FieldStats> stats = new ArrayList<>();
        stats.add(new FieldStats.Long(1, 1l, 1l, 1l, 1l, 1l));
        stats.add(new FieldStats.Long(1, 1l, 1l, 1l, 1l, 1l));
        stats.add(new FieldStats.Long(1, 1l, 1l, 1l, 1l, 1l));

        FieldStats stat = new FieldStats.Long(1, 1l, 1l, 1l, 1l, 1l);
        for (FieldStats otherStat : stats) {
            stat.append(otherStat);
        }
        assertThat(stat.getMaxDoc(), equalTo(4l));
        assertThat(stat.getDocCount(), equalTo(4l));
        assertThat(stat.getSumDocFreq(), equalTo(4l));
        assertThat(stat.getSumTotalTermFreq(), equalTo(4l));
    }

    public void testMerge_notAvailable() {
        List<FieldStats> stats = new ArrayList<>();
        stats.add(new FieldStats.Long(1, 1l, 1l, 1l, 1l, 1l));
        stats.add(new FieldStats.Long(1, 1l, 1l, 1l, 1l, 1l));
        stats.add(new FieldStats.Long(1, 1l, 1l, 1l, 1l, 1l));

        FieldStats stat = new FieldStats.Long(1, -1l, -1l, -1l, 1l, 1l);
        for (FieldStats otherStat : stats) {
            stat.append(otherStat);
        }
        assertThat(stat.getMaxDoc(), equalTo(4l));
        assertThat(stat.getDocCount(), equalTo(-1l));
        assertThat(stat.getSumDocFreq(), equalTo(-1l));
        assertThat(stat.getSumTotalTermFreq(), equalTo(-1l));

        stats.add(new FieldStats.Long(1, -1l, -1l, -1l, 1l, 1l));
        stat = stats.remove(0);
        for (FieldStats otherStat : stats) {
            stat.append(otherStat);
        }
        assertThat(stat.getMaxDoc(), equalTo(4l));
        assertThat(stat.getDocCount(), equalTo(-1l));
        assertThat(stat.getSumDocFreq(), equalTo(-1l));
        assertThat(stat.getSumTotalTermFreq(), equalTo(-1l));
    }

    public void testInvalidField() {
        createIndex("test1", Settings.EMPTY, "field1", "value", "type=string");
        client().prepareIndex("test1", "test").setSource("field1", "a").get();
        client().prepareIndex("test1", "test").setSource("field1", "b").get();

        createIndex("test2", Settings.EMPTY, "field2", "value", "type=string");
        client().prepareIndex("test2", "test").setSource("field2", "a").get();
        client().prepareIndex("test2", "test").setSource("field2", "b").get();
        client().admin().indices().prepareRefresh().get();

        FieldStatsResponse result = client().prepareFieldStats().setFields("field1", "field2").get();
        assertThat(result.getFailedShards(), equalTo(2));
        assertThat(result.getTotalShards(), equalTo(2));
        assertThat(result.getSuccessfulShards(), equalTo(0));
        assertThat(result.getShardFailures()[0].reason(), either(containsString("field [field1] doesn't exist")).or(containsString("field [field2] doesn't exist")));
        assertThat(result.getIndicesMergedFieldStats().size(), equalTo(0));

        // will only succeed on the 'test2' shard, because there the field does exist
        result = client().prepareFieldStats().setFields("field1").get();
        assertThat(result.getFailedShards(), equalTo(1));
        assertThat(result.getTotalShards(), equalTo(2));
        assertThat(result.getSuccessfulShards(), equalTo(1));
        assertThat(result.getShardFailures()[0].reason(), either(containsString("field [field1] doesn't exist")).or(containsString("field [field2] doesn't exist")));
        assertThat(result.getIndicesMergedFieldStats().get("_all").get("field1").getMinValueAsString(), equalTo("a"));
        assertThat(result.getIndicesMergedFieldStats().get("_all").get("field1").getMaxValueAsString(), equalTo("b"));
    }

    public void testNumberFiltering() {
        createIndex("test1", Settings.EMPTY, "type", "value", "type=long");
        client().prepareIndex("test1", "test").setSource("value", 1l).get();
        createIndex("test2", Settings.EMPTY, "type", "value", "type=long");
        client().prepareIndex("test2", "test").setSource("value", 3l).get();
        client().admin().indices().prepareRefresh().get();

        FieldStatsResponse response = client().prepareFieldStats()
                .setFields("value")
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(2));
        assertThat(response.getIndicesMergedFieldStats().get("test1").get("value").getMinValue(), equalTo(1l));
        assertThat(response.getIndicesMergedFieldStats().get("test2").get("value").getMinValue(), equalTo(3l));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GTE, "-1"), new IndexConstraint("value", MAX, LTE, "0"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(0));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GTE, "0"), new IndexConstraint("value", MAX, LT, "1"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(0));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GTE, "0"), new IndexConstraint("value", MAX, LTE, "1"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(1));
        assertThat(response.getIndicesMergedFieldStats().get("test1").get("value").getMinValue(), equalTo(1l));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GTE, "1"), new IndexConstraint("value", MAX, LTE,  "2"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(1));
        assertThat(response.getIndicesMergedFieldStats().get("test1").get("value").getMinValue(), equalTo(1l));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GT, "1"), new IndexConstraint("value", MAX, LTE, "2"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(0));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GT, "2"), new IndexConstraint("value", MAX, LTE, "3"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(1));
        assertThat(response.getIndicesMergedFieldStats().get("test2").get("value").getMinValue(), equalTo(3l));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GTE, "3"), new IndexConstraint("value", MAX, LTE, "4"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(1));
        assertThat(response.getIndicesMergedFieldStats().get("test2").get("value").getMinValue(), equalTo(3l));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GT, "3"), new IndexConstraint("value", MAX, LTE, "4"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(0));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GTE,  "1"), new IndexConstraint("value", MAX, LTE, "3"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(2));
        assertThat(response.getIndicesMergedFieldStats().get("test1").get("value").getMinValue(), equalTo(1l));
        assertThat(response.getIndicesMergedFieldStats().get("test2").get("value").getMinValue(), equalTo(3l));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GT, "1"), new IndexConstraint("value", MAX, LT, "3"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(0));
    }

    public void testDateFiltering() {
        DateTime dateTime1 = new DateTime(2014, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        String dateTime1Str = DateFieldMapper.Defaults.DATE_TIME_FORMATTER.parser().print(dateTime1);
        DateTime dateTime2 = new DateTime(2014, 1, 2, 0, 0, 0, 0, DateTimeZone.UTC);
        String dateTime2Str = DateFieldMapper.Defaults.DATE_TIME_FORMATTER.parser().print(dateTime2);

        createIndex("test1", Settings.EMPTY, "type", "value", "type=date");
        client().prepareIndex("test1", "test").setSource("value", dateTime1Str).get();
        createIndex("test2", Settings.EMPTY, "type", "value", "type=date");
        client().prepareIndex("test2", "test").setSource("value", dateTime2Str).get();
        client().admin().indices().prepareRefresh().get();

        FieldStatsResponse response = client().prepareFieldStats()
                .setFields("value")
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(2));
        assertThat(response.getIndicesMergedFieldStats().get("test1").get("value").getMinValue(), equalTo(dateTime1.getMillis()));
        assertThat(response.getIndicesMergedFieldStats().get("test2").get("value").getMinValue(), equalTo(dateTime2.getMillis()));
        assertThat(response.getIndicesMergedFieldStats().get("test1").get("value").getMinValueAsString(), equalTo(dateTime1Str));
        assertThat(response.getIndicesMergedFieldStats().get("test2").get("value").getMinValueAsString(), equalTo(dateTime2Str));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GTE, "2013-12-30T00:00:00.000Z"), new IndexConstraint("value", MAX, LTE, "2013-12-31T00:00:00.000Z"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(0));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GTE, "2013-12-31T00:00:00.000Z"), new IndexConstraint("value", MAX, LTE, "2014-01-01T00:00:00.000Z"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(1));
        assertThat(response.getIndicesMergedFieldStats().get("test1").get("value").getMinValue(), equalTo(dateTime1.getMillis()));
        assertThat(response.getIndicesMergedFieldStats().get("test1").get("value").getMinValueAsString(), equalTo(dateTime1Str));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GT, "2014-01-01T00:00:00.000Z"), new IndexConstraint("value", MAX, LTE, "2014-01-02T00:00:00.000Z"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(1));
        assertThat(response.getIndicesMergedFieldStats().get("test2").get("value").getMinValue(), equalTo(dateTime2.getMillis()));
        assertThat(response.getIndicesMergedFieldStats().get("test2").get("value").getMinValueAsString(), equalTo(dateTime2Str));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GT, "2014-01-02T00:00:00.000Z"), new IndexConstraint("value", MAX, LTE, "2014-01-03T00:00:00.000Z"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(0));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GTE, "2014-01-01T23:00:00.000Z"), new IndexConstraint("value", MAX, LTE, "2014-01-02T01:00:00.000Z"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(1));
        assertThat(response.getIndicesMergedFieldStats().get("test2").get("value").getMinValue(), equalTo(dateTime2.getMillis()));
        assertThat(response.getIndicesMergedFieldStats().get("test2").get("value").getMinValueAsString(), equalTo(dateTime2Str));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GTE, "2014-01-01T00:00:00.000Z"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(2));
        assertThat(response.getIndicesMergedFieldStats().get("test1").get("value").getMinValue(), equalTo(dateTime1.getMillis()));
        assertThat(response.getIndicesMergedFieldStats().get("test2").get("value").getMinValue(), equalTo(dateTime2.getMillis()));
        assertThat(response.getIndicesMergedFieldStats().get("test1").get("value").getMinValueAsString(), equalTo(dateTime1Str));
        assertThat(response.getIndicesMergedFieldStats().get("test2").get("value").getMinValueAsString(), equalTo(dateTime2Str));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MAX, LTE, "2014-01-02T00:00:00.000Z"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(2));
        assertThat(response.getIndicesMergedFieldStats().get("test1").get("value").getMinValue(), equalTo(dateTime1.getMillis()));
        assertThat(response.getIndicesMergedFieldStats().get("test2").get("value").getMinValue(), equalTo(dateTime2.getMillis()));
        assertThat(response.getIndicesMergedFieldStats().get("test1").get("value").getMinValueAsString(), equalTo(dateTime1Str));
        assertThat(response.getIndicesMergedFieldStats().get("test2").get("value").getMinValueAsString(), equalTo(dateTime2Str));
    }

    public void testDateFiltering_optionalFormat() {
        createIndex("test1", Settings.EMPTY, "type", "value", "type=date,format=strict_date_optional_time");
        client().prepareIndex("test1", "type").setSource("value", "2014-01-01T00:00:00.000Z").get();
        createIndex("test2", Settings.EMPTY, "type", "value", "type=date,format=strict_date_optional_time");
        client().prepareIndex("test2", "type").setSource("value", "2014-01-02T00:00:00.000Z").get();
        client().admin().indices().prepareRefresh().get();

        DateTime dateTime1 = new DateTime(2014, 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
        DateTime dateTime2 = new DateTime(2014, 1, 2, 0, 0, 0, 0, DateTimeZone.UTC);
        FieldStatsResponse response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GT, String.valueOf(dateTime1.getMillis()), "epoch_millis"), new IndexConstraint("value", MAX, LTE, String.valueOf(dateTime2.getMillis()), "epoch_millis"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(1));
        assertThat(response.getIndicesMergedFieldStats().get("test2").get("value").getMinValueAsString(), equalTo("2014-01-02T00:00:00.000Z"));

        try {
            client().prepareFieldStats()
                    .setFields("value")
                    .setIndexContraints(new IndexConstraint("value", MIN, GT, String.valueOf(dateTime1.getMillis()), "xyz"))
                    .setLevel("indices")
                    .get();
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("Invalid format"));
        }
    }

    public void testEmptyIndex() {
        createIndex("test1", Settings.EMPTY, "type", "value", "type=date");
        FieldStatsResponse response = client().prepareFieldStats()
                .setFields("value")
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(1));
        assertThat(response.getIndicesMergedFieldStats().get("test1").size(), equalTo(0));

        response = client().prepareFieldStats()
                .setFields("value")
                .setIndexContraints(new IndexConstraint("value", MIN, GTE, "1998-01-01T00:00:00.000Z"))
                .setLevel("indices")
                .get();
        assertThat(response.getIndicesMergedFieldStats().size(), equalTo(0));
    }

}