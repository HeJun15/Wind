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

import com.spatial4j.core.shape.Point;

import org.apache.lucene.search.GeoPointDistanceQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.GeoUtils;
import org.elasticsearch.Version;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.search.geo.GeoDistanceRangeQuery;
import org.elasticsearch.test.geo.RandomShapeGenerator;

import java.io.IOException;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

public class GeoDistanceQueryBuilderTests extends AbstractQueryTestCase<GeoDistanceQueryBuilder> {

    @Override
    protected GeoDistanceQueryBuilder doCreateTestQueryBuilder() {
        GeoDistanceQueryBuilder qb = new GeoDistanceQueryBuilder(GEO_POINT_FIELD_NAME);
        String distance = "" + randomDouble();
        if (randomBoolean()) {
            DistanceUnit unit = randomFrom(DistanceUnit.values());
            distance = distance + unit.toString();
        }
        int selector = randomIntBetween(0, 2);
        switch (selector) {
            case 0:
                qb.distance(randomDouble(), randomFrom(DistanceUnit.values()));
                break;
            case 1:
                qb.distance(distance, randomFrom(DistanceUnit.values()));
                break;
            case 2:
                qb.distance(distance);
                break;
        }

        Point p = RandomShapeGenerator.xRandomPoint(random());
        qb.point(new GeoPoint(p.getY(), p.getX()));

        if (randomBoolean()) {
            qb.setValidationMethod(randomFrom(GeoValidationMethod.values()));
        }

        if (randomBoolean()) {
            qb.optimizeBbox(randomFrom("none", "memory", "indexed"));
        }

        if (randomBoolean()) {
            qb.geoDistance(randomFrom(GeoDistance.values()));
        }
        return qb;
    }

    public void testIllegalValues() {
        try {
            if (randomBoolean()) {
                new GeoDistanceQueryBuilder("");
            } else {
                new GeoDistanceQueryBuilder(null);
            }
            fail("must not be null or empty");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        GeoDistanceQueryBuilder query = new GeoDistanceQueryBuilder("fieldName");
        try {
            if (randomBoolean()) {
                query.distance("");
            } else {
                query.distance(null);
            }
            fail("must not be null or empty");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        try {
            if (randomBoolean()) {
                query.distance("", DistanceUnit.DEFAULT);
            } else {
                query.distance(null, DistanceUnit.DEFAULT);
            }
            fail("must not be null or empty");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        try {
            query.distance("1", null);
            fail("unit must not be null");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        try {
            query.distance(1, null);
            fail("unit must not be null");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        try {
            query.geohash(null);
            fail("geohash must not be null");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        try {
            query.geoDistance(null);
            fail("geodistance must not be null");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        try {
            query.optimizeBbox(null);
            fail("optimizeBbox must not be null");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    /**
     * Overridden here to ensure the test is only run if at least one type is
     * present in the mappings. Geo queries do not execute if the field is not
     * explicitly mapped
     */
    @Override
    public void testToQuery() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        super.testToQuery();
    }

    @Override
    protected void doAssertLuceneQuery(GeoDistanceQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        Version version = context.indexVersionCreated();
        if (version.before(Version.V_2_2_0)) {
            assertLegacyQuery(queryBuilder, query);
        } else {
            assertGeoPointQuery(queryBuilder, query);
        }
    }

    private void assertLegacyQuery(GeoDistanceQueryBuilder queryBuilder, Query query) throws IOException {
        assertThat(query, instanceOf(GeoDistanceRangeQuery.class));
        GeoDistanceRangeQuery geoQuery = (GeoDistanceRangeQuery) query;
        assertThat(geoQuery.fieldName(), equalTo(queryBuilder.fieldName()));
        if (queryBuilder.point() != null) {
            assertThat(geoQuery.lat(), equalTo(queryBuilder.point().lat()));
            assertThat(geoQuery.lon(), equalTo(queryBuilder.point().lon()));
        }
        assertThat(geoQuery.geoDistance(), equalTo(queryBuilder.geoDistance()));
        assertThat(geoQuery.minInclusiveDistance(), equalTo(Double.NEGATIVE_INFINITY));
        double distance = queryBuilder.distance();
        if (queryBuilder.geoDistance() != null) {
            distance = queryBuilder.geoDistance().normalize(distance, DistanceUnit.DEFAULT);
        }
        assertThat(geoQuery.maxInclusiveDistance(), closeTo(distance, Math.abs(distance) / 1000));
    }

    private void assertGeoPointQuery(GeoDistanceQueryBuilder queryBuilder, Query query) throws IOException {
        assertThat(query, instanceOf(GeoPointDistanceQuery.class));
        GeoPointDistanceQuery geoQuery = (GeoPointDistanceQuery) query;
        assertThat(geoQuery.getField(), equalTo(queryBuilder.fieldName()));
        if (queryBuilder.point() != null) {
            assertThat(geoQuery.getCenterLat(), equalTo(queryBuilder.point().lat()));
            assertThat(geoQuery.getCenterLon(), equalTo(queryBuilder.point().lon()));
        }
        double distance = queryBuilder.distance();
        if (queryBuilder.geoDistance() != null) {
            distance = queryBuilder.geoDistance().normalize(distance, DistanceUnit.DEFAULT);
            assertThat(geoQuery.getRadiusMeters(), closeTo(distance, GeoUtils.TOLERANCE));
        }
    }

    public void testParsingAndToQuery1() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"geo_distance\":{\n" +
                "        \"distance\":\"12mi\",\n" +
                "        \"" + GEO_POINT_FIELD_NAME + "\":{\n" +
                "            \"lat\":40,\n" +
                "            \"lon\":-70\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        assertGeoDistanceRangeQuery(query, 40, -70, 12, DistanceUnit.DEFAULT);
    }

    public void testParsingAndToQuery2() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"geo_distance\":{\n" +
                "        \"distance\":\"12mi\",\n" +
                "        \"" + GEO_POINT_FIELD_NAME + "\":[-70, 40]\n" +
                "    }\n" +
                "}\n";
        assertGeoDistanceRangeQuery(query, 40, -70, 12, DistanceUnit.DEFAULT);
    }

    public void testParsingAndToQuery3() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"geo_distance\":{\n" +
                "        \"distance\":\"12mi\",\n" +
                "        \"" + GEO_POINT_FIELD_NAME + "\":\"40, -70\"\n" +
                "    }\n" +
                "}\n";
        assertGeoDistanceRangeQuery(query, 40, -70, 12, DistanceUnit.DEFAULT);
    }

    public void testParsingAndToQuery4() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"geo_distance\":{\n" +
                "        \"distance\":\"12mi\",\n" +
                "        \"" + GEO_POINT_FIELD_NAME + "\":\"drn5x1g8cu2y\"\n" +
                "    }\n" +
                "}\n";
        assertGeoDistanceRangeQuery(query, 40, -70, 12, DistanceUnit.DEFAULT);
    }

    public void testParsingAndToQuery5() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"geo_distance\":{\n" +
                "        \"distance\":12,\n" +
                "        \"unit\":\"mi\",\n" +
                "        \"" + GEO_POINT_FIELD_NAME + "\":{\n" +
                "            \"lat\":40,\n" +
                "            \"lon\":-70\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        assertGeoDistanceRangeQuery(query, 40, -70, 12, DistanceUnit.DEFAULT);
    }

    public void testParsingAndToQuery6() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"geo_distance\":{\n" +
                "        \"distance\":\"12\",\n" +
                "        \"unit\":\"mi\",\n" +
                "        \"" + GEO_POINT_FIELD_NAME + "\":{\n" +
                "            \"lat\":40,\n" +
                "            \"lon\":-70\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        assertGeoDistanceRangeQuery(query, 40, -70, 12, DistanceUnit.DEFAULT);
    }

    public void testParsingAndToQuery7() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "  \"geo_distance\":{\n" +
                "      \"distance\":\"19.312128\",\n" +
                "      \"" + GEO_POINT_FIELD_NAME + "\":{\n" +
                "          \"lat\":40,\n" +
                "          \"lon\":-70\n" +
                "      }\n" +
                "  }\n" +
                "}\n";
        assertGeoDistanceRangeQuery(query, 40, -70, 0.012, DistanceUnit.DEFAULT);
    }

    public void testParsingAndToQuery8() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"geo_distance\":{\n" +
                "        \"distance\":19.312128,\n" +
                "        \"" + GEO_POINT_FIELD_NAME + "\":{\n" +
                "            \"lat\":40,\n" +
                "            \"lon\":-70\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        assertGeoDistanceRangeQuery(query, 40, -70, 12, DistanceUnit.KILOMETERS);
    }

    public void testParsingAndToQuery9() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"geo_distance\":{\n" +
                "        \"distance\":\"19.312128\",\n" +
                "        \"unit\":\"km\",\n" +
                "        \"" + GEO_POINT_FIELD_NAME + "\":{\n" +
                "            \"lat\":40,\n" +
                "            \"lon\":-70\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        assertGeoDistanceRangeQuery(query, 40, -70, 12, DistanceUnit.DEFAULT);
    }

    public void testParsingAndToQuery10() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"geo_distance\":{\n" +
                "        \"distance\":19.312128,\n" +
                "        \"unit\":\"km\",\n" +
                "        \"" + GEO_POINT_FIELD_NAME + "\":{\n" +
                "            \"lat\":40,\n" +
                "            \"lon\":-70\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        assertGeoDistanceRangeQuery(query, 40, -70, 12, DistanceUnit.DEFAULT);
    }

    public void testParsingAndToQuery11() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"geo_distance\":{\n" +
                "        \"distance\":\"19.312128km\",\n" +
                "        \"" + GEO_POINT_FIELD_NAME + "\":{\n" +
                "            \"lat\":40,\n" +
                "            \"lon\":-70\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        assertGeoDistanceRangeQuery(query, 40, -70, 12, DistanceUnit.DEFAULT);
    }

    public void testParsingAndToQuery12() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"geo_distance\":{\n" +
                "        \"distance\":\"12mi\",\n" +
                "        \"unit\":\"km\",\n" +
                "        \"" + GEO_POINT_FIELD_NAME + "\":{\n" +
                "            \"lat\":40,\n" +
                "            \"lon\":-70\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        assertGeoDistanceRangeQuery(query, 40, -70, 12, DistanceUnit.DEFAULT);
    }

    private void assertGeoDistanceRangeQuery(String query, double lat, double lon, double distance, DistanceUnit distanceUnit) throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        Query parsedQuery = parseQuery(query).toQuery(createShardContext());
        Version version = queryShardContext().indexVersionCreated();
        if (version.before(Version.V_2_2_0)) {
            GeoDistanceRangeQuery q = (GeoDistanceRangeQuery) parsedQuery;
            assertThat(q.fieldName(), equalTo(GEO_POINT_FIELD_NAME));
            assertThat(q.lat(), closeTo(lat, 1E-5D));
            assertThat(q.lon(), closeTo(lon, 1E-5D));
            assertThat(q.minInclusiveDistance(), equalTo(Double.NEGATIVE_INFINITY));
            assertThat(q.maxInclusiveDistance(), closeTo(distanceUnit.convert(distance, DistanceUnit.MILES), 1E-5D));
        } else {
            GeoPointDistanceQuery q = (GeoPointDistanceQuery) parsedQuery;
            assertThat(q.getField(), equalTo(GEO_POINT_FIELD_NAME));
            assertThat(q.getCenterLat(), closeTo(lat, 1E-5D));
            assertThat(q.getCenterLon(), closeTo(lon, 1E-5D));
            assertThat(q.getRadiusMeters(), closeTo(distanceUnit.convert(distance, DistanceUnit.MILES), 1E-5D));
        }
    }

    public void testFromJson() throws IOException {
        String json =
                "{\n" + 
                "  \"geo_distance\" : {\n" + 
                "    \"pin.location\" : [ -70.0, 40.0 ],\n" + 
                "    \"distance\" : 12000.0,\n" + 
                "    \"distance_type\" : \"sloppy_arc\",\n" + 
                "    \"optimize_bbox\" : \"memory\",\n" + 
                "    \"validation_method\" : \"STRICT\",\n" + 
                "    \"boost\" : 1.0\n" + 
                "  }\n" + 
                "}";
        GeoDistanceQueryBuilder parsed = (GeoDistanceQueryBuilder) parseQuery(json);
        checkGeneratedJson(json, parsed);
        assertEquals(json, -70.0, parsed.point().getLon(), 0.0001);
        assertEquals(json, 40.0, parsed.point().getLat(), 0.0001);
        assertEquals(json, 12000.0, parsed.distance(), 0.0001);
    }
}
