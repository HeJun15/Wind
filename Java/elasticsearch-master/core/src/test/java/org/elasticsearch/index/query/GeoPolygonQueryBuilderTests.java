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

import com.spatial4j.core.shape.jts.JtsGeometry;
import com.vividsolutions.jts.geom.Coordinate;

import org.apache.lucene.search.GeoPointInPolygonQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.Version;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.geo.GeoUtils;
import org.elasticsearch.common.geo.builders.ShapeBuilder;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.search.geo.GeoPolygonQuery;
import org.elasticsearch.test.geo.RandomShapeGenerator;
import org.elasticsearch.test.geo.RandomShapeGenerator.ShapeType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.elasticsearch.test.StreamsUtils.copyToStringFromClasspath;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class GeoPolygonQueryBuilderTests extends AbstractQueryTestCase<GeoPolygonQueryBuilder> {
    @Override
    protected GeoPolygonQueryBuilder doCreateTestQueryBuilder() {
        List<GeoPoint> polygon = randomPolygon(randomIntBetween(4, 50));
        GeoPolygonQueryBuilder builder = new GeoPolygonQueryBuilder(GEO_POINT_FIELD_NAME, polygon);
        if (randomBoolean()) {
            builder.setValidationMethod(randomFrom(GeoValidationMethod.values()));
        }
        return builder;
    }

    @Override
    protected void doAssertLuceneQuery(GeoPolygonQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        Version version = context.indexVersionCreated();
        if (version.before(Version.V_2_2_0)) {
            assertLegacyQuery(queryBuilder, query);
        } else {
            assertGeoPointQuery(queryBuilder, query);
        }
    }

    private void assertLegacyQuery(GeoPolygonQueryBuilder queryBuilder, Query query) {
        assertThat(query, instanceOf(GeoPolygonQuery.class));
        GeoPolygonQuery geoQuery = (GeoPolygonQuery) query;
        assertThat(geoQuery.fieldName(), equalTo(queryBuilder.fieldName()));
        List<GeoPoint> queryBuilderPoints = queryBuilder.points();
        GeoPoint[] queryPoints = geoQuery.points();
        assertThat(queryPoints.length, equalTo(queryBuilderPoints.size()));
        if (GeoValidationMethod.isCoerce(queryBuilder.getValidationMethod())) {
            for (int i = 0; i < queryBuilderPoints.size(); i++) {
                GeoPoint queryBuilderPoint = queryBuilderPoints.get(i);
                GeoPoint pointCopy = new GeoPoint(queryBuilderPoint);
                GeoUtils.normalizePoint(pointCopy, true, true);
                assertThat(queryPoints[i], equalTo(pointCopy));
            }
        } else {
            for (int i = 0; i < queryBuilderPoints.size(); i++) {
                assertThat(queryPoints[i], equalTo(queryBuilderPoints.get(i)));
            }
        }
    }

    private void assertGeoPointQuery(GeoPolygonQueryBuilder queryBuilder, Query query) {
        assertThat(query, instanceOf(GeoPointInPolygonQuery.class));
        GeoPointInPolygonQuery geoQuery = (GeoPointInPolygonQuery) query;
        assertThat(geoQuery.getField(), equalTo(queryBuilder.fieldName()));
        List<GeoPoint> queryBuilderPoints = queryBuilder.points();
        double[] lats = geoQuery.getLats();
        double[] lons = geoQuery.getLons();
        assertThat(lats.length, equalTo(queryBuilderPoints.size()));
        assertThat(lons.length, equalTo(queryBuilderPoints.size()));
        for (int i=0; i < queryBuilderPoints.size(); ++i) {
            final GeoPoint queryBuilderPoint = queryBuilderPoints.get(i);
            final GeoPoint pointCopy = new GeoPoint(queryBuilderPoint);
            GeoUtils.normalizePoint(pointCopy);
            assertThat(lats[i], closeTo(pointCopy.getLat(), 1E-5D));
            assertThat(lons[i], closeTo(pointCopy.getLon(), 1E-5D));
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

    public List<GeoPoint> randomPolygon(int numPoints) {
        ShapeBuilder shapeBuilder = null;
        // This is a temporary fix because sometimes the RandomShapeGenerator
        // returns null. This is if there is an error generating the polygon. So
        // in this case keep trying until we successfully generate one
        while (shapeBuilder == null) {
            shapeBuilder = RandomShapeGenerator.createShapeWithin(getRandom(), null, ShapeType.POLYGON);
        }
        JtsGeometry shape = (JtsGeometry) shapeBuilder.build();
        Coordinate[] coordinates = shape.getGeom().getCoordinates();
        ArrayList<GeoPoint> polygonPoints = new ArrayList<>();
        for (Coordinate coord : coordinates) {
            polygonPoints.add(new GeoPoint(coord.y, coord.x));
        }
        return polygonPoints;
    }

    public void testNullFieldName() {
        try {
            new GeoPolygonQueryBuilder(null, randomPolygon(5));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("fieldName must not be null"));
        }
    }

    public void testEmptyPolygon() {
        try {
            if (randomBoolean()) {
                new GeoPolygonQueryBuilder(GEO_POINT_FIELD_NAME, new ArrayList<GeoPoint>());
            } else {
                new GeoPolygonQueryBuilder(GEO_POINT_FIELD_NAME, null);
            }
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("polygon must not be null or empty"));
        }
    }

    public void testInvalidClosedPolygon() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(0, 90));
        points.add(new GeoPoint(90, 90));
        points.add(new GeoPoint(0, 90));
        try {
            new GeoPolygonQueryBuilder(GEO_POINT_FIELD_NAME, points);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("too few points defined for geo_polygon query"));
        }
    }

    public void testInvalidOpenPolygon() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(0, 90));
        points.add(new GeoPoint(90, 90));
        try {
            new GeoPolygonQueryBuilder(GEO_POINT_FIELD_NAME, points);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("too few points defined for geo_polygon query"));
        }
    }

    public void testDeprecatedXContent() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder().prettyPrint();
        builder.startObject();
        builder.startObject("geo_polygon");
        builder.startObject(GEO_POINT_FIELD_NAME);
        builder.startArray("points");
        builder.value("0,0");
        builder.value("0,90");
        builder.value("90,90");
        builder.value("90,0");
        builder.endArray();
        builder.endObject();
        builder.field("normalize", true); // deprecated
        builder.endObject();
        builder.endObject();
        try {
            parseQuery(builder.string());
            fail("normalize is deprecated");
        } catch (IllegalArgumentException ex) {
            assertEquals("Deprecated field [normalize] used, expected [coerce] instead", ex.getMessage());
        }
    }

    public void testParsingAndToQueryParsingExceptions() throws IOException {
        String[] brokenFiles = new String[]{
                "/org/elasticsearch/index/query/geo_polygon_exception_1.json",
                "/org/elasticsearch/index/query/geo_polygon_exception_2.json",
                "/org/elasticsearch/index/query/geo_polygon_exception_3.json",
                "/org/elasticsearch/index/query/geo_polygon_exception_4.json",
                "/org/elasticsearch/index/query/geo_polygon_exception_5.json"
        };
        for (String brokenFile : brokenFiles) {
            String query = copyToStringFromClasspath(brokenFile);
            try {
                parseQuery(query);
                fail("parsing a broken geo_polygon filter didn't fail as expected while parsing: " + brokenFile);
            } catch (ParsingException e) {
                // success!
            }
        }
    }

    public void testParsingAndToQuery1() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"geo_polygon\":{\n" +
                "        \"" + GEO_POINT_FIELD_NAME + "\":{\n" +
                "            \"points\":[\n" +
                "                [-70, 40],\n" +
                "                [-80, 30],\n" +
                "                [-90, 20]\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        assertGeoPolygonQuery(query);
    }

    public void testParsingAndToQuery2() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"geo_polygon\":{\n" +
                "        \"" + GEO_POINT_FIELD_NAME + "\":{\n" +
                "            \"points\":[\n" +
                "                {\n" +
                "                    \"lat\":40,\n" +
                "                    \"lon\":-70\n" +
                "                },\n" +
                "                {\n" +
                "                    \"lat\":30,\n" +
                "                    \"lon\":-80\n" +
                "                },\n" +
                "                {\n" +
                "                    \"lat\":20,\n" +
                "                    \"lon\":-90\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        assertGeoPolygonQuery(query);
    }

    public void testParsingAndToQuery3() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"geo_polygon\":{\n" +
                "        \"" + GEO_POINT_FIELD_NAME + "\":{\n" +
                "            \"points\":[\n" +
                "                \"40, -70\",\n" +
                "                \"30, -80\",\n" +
                "                \"20, -90\"\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        assertGeoPolygonQuery(query);
    }

    public void testParsingAndToQuery4() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String query = "{\n" +
                "    \"geo_polygon\":{\n" +
                "        \"" + GEO_POINT_FIELD_NAME + "\":{\n" +
                "            \"points\":[\n" +
                "                \"drn5x1g8cu2y\",\n" +
                "                \"30, -80\",\n" +
                "                \"20, -90\"\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        assertGeoPolygonQuery(query);
    }

    private void assertGeoPolygonQuery(String query) throws IOException {
        QueryShardContext context = createShardContext();
        Version version = context.indexVersionCreated();
        Query parsedQuery = parseQuery(query).toQuery(context);
        if (version.before(Version.V_2_2_0)) {
            GeoPolygonQuery filter = (GeoPolygonQuery) parsedQuery;
            assertThat(filter.fieldName(), equalTo(GEO_POINT_FIELD_NAME));
            assertThat(filter.points().length, equalTo(4));
            assertThat(filter.points()[0].lat(), closeTo(40, 0.00001));
            assertThat(filter.points()[0].lon(), closeTo(-70, 0.00001));
            assertThat(filter.points()[1].lat(), closeTo(30, 0.00001));
            assertThat(filter.points()[1].lon(), closeTo(-80, 0.00001));
            assertThat(filter.points()[2].lat(), closeTo(20, 0.00001));
            assertThat(filter.points()[2].lon(), closeTo(-90, 0.00001));
        } else {
            GeoPointInPolygonQuery q = (GeoPointInPolygonQuery) parsedQuery;
            assertThat(q.getField(), equalTo(GEO_POINT_FIELD_NAME));
            final double[] lats = q.getLats();
            final double[] lons = q.getLons();
            assertThat(lats.length, equalTo(4));
            assertThat(lons.length, equalTo(4));
            assertThat(lats[0], closeTo(40, 1E-5));
            assertThat(lons[0], closeTo(-70, 1E-5));
            assertThat(lats[1], closeTo(30, 1E-5));
            assertThat(lons[1], closeTo(-80, 1E-5));
            assertThat(lats[2], closeTo(20, 1E-5));
            assertThat(lons[2], closeTo(-90, 1E-5));
            assertThat(lats[3], equalTo(lats[0]));
            assertThat(lons[3], equalTo(lons[0]));
        }
    }

    public void testFromJson() throws IOException {
        String json =
                "{\n" + 
                "  \"geo_polygon\" : {\n" + 
                "    \"person.location\" : {\n" + 
                "      \"points\" : [ [ -70.0, 40.0 ], [ -80.0, 30.0 ], [ -90.0, 20.0 ], [ -70.0, 40.0 ] ]\n" + 
                "    },\n" + 
                "    \"coerce\" : false,\n" + 
                "    \"ignore_malformed\" : false,\n" + 
                "    \"boost\" : 1.0\n" + 
                "  }\n" + 
                "}";
        GeoPolygonQueryBuilder parsed = (GeoPolygonQueryBuilder) parseQuery(json);
        checkGeneratedJson(json, parsed);
        assertEquals(json, 4, parsed.points().size());
    }
}
