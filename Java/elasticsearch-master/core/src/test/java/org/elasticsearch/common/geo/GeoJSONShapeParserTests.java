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

package org.elasticsearch.common.geo;

import com.spatial4j.core.exception.InvalidShapeException;
import com.spatial4j.core.shape.Circle;
import com.spatial4j.core.shape.Rectangle;
import com.spatial4j.core.shape.Shape;
import com.spatial4j.core.shape.ShapeCollection;
import com.spatial4j.core.shape.jts.JtsGeometry;
import com.spatial4j.core.shape.jts.JtsPoint;
import com.vividsolutions.jts.geom.*;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.geo.builders.ShapeBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.hamcrest.ElasticsearchGeoAssertions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.elasticsearch.common.geo.builders.ShapeBuilder.SPATIAL_CONTEXT;


/**
 * Tests for {@code GeoJSONShapeParser}
 */
public class GeoJSONShapeParserTests extends ESTestCase {

    private final static GeometryFactory GEOMETRY_FACTORY = SPATIAL_CONTEXT.getGeometryFactory();

    public void testParse_simplePoint() throws IOException {
        String pointGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Point")
                .startArray("coordinates").value(100.0).value(0.0).endArray()
                .endObject().string();

        Point expected = GEOMETRY_FACTORY.createPoint(new Coordinate(100.0, 0.0));
        assertGeometryEquals(new JtsPoint(expected, SPATIAL_CONTEXT), pointGeoJson);
    }

    public void testParse_lineString() throws IOException {
        String lineGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "LineString")
                .startArray("coordinates")
                .startArray().value(100.0).value(0.0).endArray()
                .startArray().value(101.0).value(1.0).endArray()
                .endArray()
                .endObject().string();

        List<Coordinate> lineCoordinates = new ArrayList<>();
        lineCoordinates.add(new Coordinate(100, 0));
        lineCoordinates.add(new Coordinate(101, 1));

        LineString expected = GEOMETRY_FACTORY.createLineString(
                lineCoordinates.toArray(new Coordinate[lineCoordinates.size()]));
        assertGeometryEquals(jtsGeom(expected), lineGeoJson);
    }

    public void testParse_multiLineString() throws IOException {
        String multilinesGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "MultiLineString")
                .startArray("coordinates")
                .startArray()
                .startArray().value(100.0).value(0.0).endArray()
                .startArray().value(101.0).value(1.0).endArray()
                .endArray()
                .startArray()
                .startArray().value(102.0).value(2.0).endArray()
                .startArray().value(103.0).value(3.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        MultiLineString expected = GEOMETRY_FACTORY.createMultiLineString(new LineString[]{
                GEOMETRY_FACTORY.createLineString(new Coordinate[]{
                        new Coordinate(100, 0),
                        new Coordinate(101, 1),
                }),
                GEOMETRY_FACTORY.createLineString(new Coordinate[]{
                        new Coordinate(102, 2),
                        new Coordinate(103, 3),
                }),
        });
        assertGeometryEquals(jtsGeom(expected), multilinesGeoJson);
    }

    public void testParse_circle() throws IOException {
        String multilinesGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "circle")
                .startArray("coordinates").value(100.0).value(0.0).endArray()
                .field("radius", "100m")
                .endObject().string();

        Circle expected = SPATIAL_CONTEXT.makeCircle(100.0, 0.0, 360 * 100 / GeoUtils.EARTH_EQUATOR);
        assertGeometryEquals(expected, multilinesGeoJson);
    }

    public void testParse_multiDimensionShapes() throws IOException {
        // multi dimension point
        String pointGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Point")
                .startArray("coordinates").value(100.0).value(0.0).value(15.0).value(18.0).endArray()
                .endObject().string();

        Point expectedPt = GEOMETRY_FACTORY.createPoint(new Coordinate(100.0, 0.0));
        assertGeometryEquals(new JtsPoint(expectedPt, SPATIAL_CONTEXT), pointGeoJson);

        // multi dimension linestring
        String lineGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "LineString")
                .startArray("coordinates")
                .startArray().value(100.0).value(0.0).value(15.0).endArray()
                .startArray().value(101.0).value(1.0).value(18.0).value(19.0).endArray()
                .endArray()
                .endObject().string();

        List<Coordinate> lineCoordinates = new ArrayList<>();
        lineCoordinates.add(new Coordinate(100, 0));
        lineCoordinates.add(new Coordinate(101, 1));

        LineString expectedLS = GEOMETRY_FACTORY.createLineString(
                lineCoordinates.toArray(new Coordinate[lineCoordinates.size()]));
        assertGeometryEquals(jtsGeom(expectedLS), lineGeoJson);
    }

    public void testParse_envelope() throws IOException {
        // test #1: envelope with expected coordinate order (TopLeft, BottomRight)
        String multilinesGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "envelope")
                .startArray("coordinates")
                .startArray().value(-50).value(30).endArray()
                .startArray().value(50).value(-30).endArray()
                .endArray()
                .endObject().string();

        Rectangle expected = SPATIAL_CONTEXT.makeRectangle(-50, 50, -30, 30);
        assertGeometryEquals(expected, multilinesGeoJson);

        // test #2: envelope with agnostic coordinate order (TopRight, BottomLeft)
        multilinesGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "envelope")
                .startArray("coordinates")
                .startArray().value(50).value(30).endArray()
                .startArray().value(-50).value(-30).endArray()
                .endArray()
                .endObject().string();

        expected = SPATIAL_CONTEXT.makeRectangle(-50, 50, -30, 30);
        assertGeometryEquals(expected, multilinesGeoJson);

        // test #3: "envelope" (actually a triangle) with invalid number of coordinates (TopRight, BottomLeft, BottomRight)
        multilinesGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "envelope")
                .startArray("coordinates")
                .startArray().value(50).value(30).endArray()
                .startArray().value(-50).value(-30).endArray()
                .startArray().value(50).value(-39).endArray()
                .endArray()
                .endObject().string();
        XContentParser parser = JsonXContent.jsonXContent.createParser(multilinesGeoJson);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, ElasticsearchParseException.class);

        // test #4: "envelope" with empty coordinates
        multilinesGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "envelope")
                .startArray("coordinates")
                .endArray()
                .endObject().string();
        parser = JsonXContent.jsonXContent.createParser(multilinesGeoJson);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, ElasticsearchParseException.class);
    }

    public void testParse_polygonNoHoles() throws IOException {
        String polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .startArray("coordinates")
                .startArray()
                .startArray().value(100.0).value(1.0).endArray()
                .startArray().value(101.0).value(1.0).endArray()
                .startArray().value(101.0).value(0.0).endArray()
                .startArray().value(100.0).value(0.0).endArray()
                .startArray().value(100.0).value(1.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        List<Coordinate> shellCoordinates = new ArrayList<>();
        shellCoordinates.add(new Coordinate(100, 0));
        shellCoordinates.add(new Coordinate(101, 0));
        shellCoordinates.add(new Coordinate(101, 1));
        shellCoordinates.add(new Coordinate(100, 1));
        shellCoordinates.add(new Coordinate(100, 0));

        LinearRing shell = GEOMETRY_FACTORY.createLinearRing(shellCoordinates.toArray(new Coordinate[shellCoordinates.size()]));
        Polygon expected = GEOMETRY_FACTORY.createPolygon(shell, null);
        assertGeometryEquals(jtsGeom(expected), polygonGeoJson);
    }

    public void testParse_invalidPoint() throws IOException {
        // test case 1: create an invalid point object with multipoint data format
        String invalidPoint1 = XContentFactory.jsonBuilder().startObject().field("type", "point")
                .startArray("coordinates")
                .startArray().value(-74.011).value(40.753).endArray()
                .endArray()
                .endObject().string();
        XContentParser parser = JsonXContent.jsonXContent.createParser(invalidPoint1);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, ElasticsearchParseException.class);

        // test case 2: create an invalid point object with an empty number of coordinates
        String invalidPoint2 = XContentFactory.jsonBuilder().startObject().field("type", "point")
                .startArray("coordinates")
                .endArray()
                .endObject().string();
        parser = JsonXContent.jsonXContent.createParser(invalidPoint2);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, ElasticsearchParseException.class);
    }

    public void testParse_invalidMultipoint() throws IOException {
        // test case 1: create an invalid multipoint object with single coordinate
        String invalidMultipoint1 = XContentFactory.jsonBuilder().startObject().field("type", "multipoint")
                .startArray("coordinates").value(-74.011).value(40.753).endArray()
                .endObject().string();
        XContentParser parser = JsonXContent.jsonXContent.createParser(invalidMultipoint1);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, ElasticsearchParseException.class);

        // test case 2: create an invalid multipoint object with null coordinate
        String invalidMultipoint2 = XContentFactory.jsonBuilder().startObject().field("type", "multipoint")
                .startArray("coordinates")
                .endArray()
                .endObject().string();
        parser = JsonXContent.jsonXContent.createParser(invalidMultipoint2);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, ElasticsearchParseException.class);

        // test case 3: create a valid formatted multipoint object with invalid number (0) of coordinates
        String invalidMultipoint3 = XContentFactory.jsonBuilder().startObject().field("type", "multipoint")
                .startArray("coordinates")
                .startArray().endArray()
                .endArray()
                .endObject().string();
        parser = JsonXContent.jsonXContent.createParser(invalidMultipoint3);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, ElasticsearchParseException.class);
    }

    public void testParse_invalidMultiPolygon() throws IOException {
        // test invalid multipolygon (an "accidental" polygon with inner rings outside outer ring)
        String multiPolygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "MultiPolygon")
                .startArray("coordinates")
                .startArray()//one poly (with two holes)
                .startArray()
                .startArray().value(102.0).value(2.0).endArray()
                .startArray().value(103.0).value(2.0).endArray()
                .startArray().value(103.0).value(3.0).endArray()
                .startArray().value(102.0).value(3.0).endArray()
                .startArray().value(102.0).value(2.0).endArray()
                .endArray()
                .startArray()// first hole
                .startArray().value(100.0).value(0.0).endArray()
                .startArray().value(101.0).value(0.0).endArray()
                .startArray().value(101.0).value(1.0).endArray()
                .startArray().value(100.0).value(1.0).endArray()
                .startArray().value(100.0).value(0.0).endArray()
                .endArray()
                .startArray()//second hole
                .startArray().value(100.2).value(0.8).endArray()
                .startArray().value(100.2).value(0.2).endArray()
                .startArray().value(100.8).value(0.2).endArray()
                .startArray().value(100.8).value(0.8).endArray()
                .startArray().value(100.2).value(0.8).endArray()
                .endArray()
                .endArray()
                .endArray()
                .endObject().string();

        XContentParser parser = JsonXContent.jsonXContent.createParser(multiPolygonGeoJson);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, InvalidShapeException.class);
    }

    public void testParse_OGCPolygonWithoutHoles() throws IOException {
        // test 1: ccw poly not crossing dateline
        String polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .startArray("coordinates")
                .startArray()
                .startArray().value(176.0).value(15.0).endArray()
                .startArray().value(-177.0).value(10.0).endArray()
                .startArray().value(-177.0).value(-10.0).endArray()
                .startArray().value(176.0).value(-15.0).endArray()
                .startArray().value(172.0).value(0.0).endArray()
                .startArray().value(176.0).value(15.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        XContentParser parser = JsonXContent.jsonXContent.createParser(polygonGeoJson);
        parser.nextToken();
        Shape shape = ShapeBuilder.parse(parser).build();
        
        ElasticsearchGeoAssertions.assertPolygon(shape);

        // test 2: ccw poly crossing dateline
        polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .startArray("coordinates")
                .startArray()
                .startArray().value(-177.0).value(10.0).endArray()
                .startArray().value(176.0).value(15.0).endArray()
                .startArray().value(172.0).value(0.0).endArray()
                .startArray().value(176.0).value(-15.0).endArray()
                .startArray().value(-177.0).value(-10.0).endArray()
                .startArray().value(-177.0).value(10.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(polygonGeoJson);
        parser.nextToken();
        shape = ShapeBuilder.parse(parser).build();
        
        ElasticsearchGeoAssertions.assertMultiPolygon(shape);

        // test 3: cw poly not crossing dateline
        polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .startArray("coordinates")
                .startArray()
                .startArray().value(176.0).value(15.0).endArray()
                .startArray().value(180.0).value(10.0).endArray()
                .startArray().value(180.0).value(-10.0).endArray()
                .startArray().value(176.0).value(-15.0).endArray()
                .startArray().value(172.0).value(0.0).endArray()
                .startArray().value(176.0).value(15.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(polygonGeoJson);
        parser.nextToken();
        shape = ShapeBuilder.parse(parser).build();

        ElasticsearchGeoAssertions.assertPolygon(shape);

        // test 4: cw poly crossing dateline
        polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .startArray("coordinates")
                .startArray()
                .startArray().value(176.0).value(15.0).endArray()
                .startArray().value(184.0).value(15.0).endArray()
                .startArray().value(184.0).value(0.0).endArray()
                .startArray().value(176.0).value(-15.0).endArray()
                .startArray().value(174.0).value(-10.0).endArray()
                .startArray().value(176.0).value(15.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(polygonGeoJson);
        parser.nextToken();
        shape = ShapeBuilder.parse(parser).build();

        ElasticsearchGeoAssertions.assertMultiPolygon(shape);
    }

    public void testParse_OGCPolygonWithHoles() throws IOException {
        // test 1: ccw poly not crossing dateline
        String polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .startArray("coordinates")
                .startArray()
                .startArray().value(176.0).value(15.0).endArray()
                .startArray().value(-177.0).value(10.0).endArray()
                .startArray().value(-177.0).value(-10.0).endArray()
                .startArray().value(176.0).value(-15.0).endArray()
                .startArray().value(172.0).value(0.0).endArray()
                .startArray().value(176.0).value(15.0).endArray()
                .endArray()
                .startArray()
                .startArray().value(-172.0).value(8.0).endArray()
                .startArray().value(174.0).value(10.0).endArray()
                .startArray().value(-172.0).value(-8.0).endArray()
                .startArray().value(-172.0).value(8.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        XContentParser parser = JsonXContent.jsonXContent.createParser(polygonGeoJson);
        parser.nextToken();
        Shape shape = ShapeBuilder.parse(parser).build();

        ElasticsearchGeoAssertions.assertPolygon(shape);

        // test 2: ccw poly crossing dateline
        polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .startArray("coordinates")
                .startArray()
                .startArray().value(-177.0).value(10.0).endArray()
                .startArray().value(176.0).value(15.0).endArray()
                .startArray().value(172.0).value(0.0).endArray()
                .startArray().value(176.0).value(-15.0).endArray()
                .startArray().value(-177.0).value(-10.0).endArray()
                .startArray().value(-177.0).value(10.0).endArray()
                .endArray()
                .startArray()
                .startArray().value(178.0).value(8.0).endArray()
                .startArray().value(-178.0).value(8.0).endArray()
                .startArray().value(-180.0).value(-8.0).endArray()
                .startArray().value(178.0).value(8.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(polygonGeoJson);
        parser.nextToken();
        shape = ShapeBuilder.parse(parser).build();

        ElasticsearchGeoAssertions.assertMultiPolygon(shape);

        // test 3: cw poly not crossing dateline
        polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .startArray("coordinates")
                .startArray()
                .startArray().value(176.0).value(15.0).endArray()
                .startArray().value(180.0).value(10.0).endArray()
                .startArray().value(179.0).value(-10.0).endArray()
                .startArray().value(176.0).value(-15.0).endArray()
                .startArray().value(172.0).value(0.0).endArray()
                .startArray().value(176.0).value(15.0).endArray()
                .endArray()
                .startArray()
                .startArray().value(177.0).value(8.0).endArray()
                .startArray().value(179.0).value(10.0).endArray()
                .startArray().value(179.0).value(-8.0).endArray()
                .startArray().value(177.0).value(8.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(polygonGeoJson);
        parser.nextToken();
        shape = ShapeBuilder.parse(parser).build();

        ElasticsearchGeoAssertions.assertPolygon(shape);

        // test 4: cw poly crossing dateline
        polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .startArray("coordinates")
                .startArray()
                .startArray().value(183.0).value(10.0).endArray()
                .startArray().value(183.0).value(-10.0).endArray()
                .startArray().value(176.0).value(-15.0).endArray()
                .startArray().value(172.0).value(0.0).endArray()
                .startArray().value(176.0).value(15.0).endArray()
                .startArray().value(183.0).value(10.0).endArray()
                .endArray()
                .startArray()
                .startArray().value(178.0).value(8.0).endArray()
                .startArray().value(182.0).value(8.0).endArray()
                .startArray().value(180.0).value(-8.0).endArray()
                .startArray().value(178.0).value(8.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(polygonGeoJson);
        parser.nextToken();
        shape = ShapeBuilder.parse(parser).build();

        ElasticsearchGeoAssertions.assertMultiPolygon(shape);
    }

    public void testParse_invalidPolygon() throws IOException {
        /**
         * The following 3 test cases ensure proper error handling of invalid polygons 
         * per the GeoJSON specification
         */
        // test case 1: create an invalid polygon with only 2 points
        String invalidPoly = XContentFactory.jsonBuilder().startObject().field("type", "polygon")
                .startArray("coordinates")
                .startArray()
                .startArray().value(-74.011).value(40.753).endArray()
                .startArray().value(-75.022).value(41.783).endArray()
                .endArray()
                .endArray()
                .endObject().string();
        XContentParser parser = JsonXContent.jsonXContent.createParser(invalidPoly);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, ElasticsearchParseException.class);

        // test case 2: create an invalid polygon with only 1 point
        invalidPoly = XContentFactory.jsonBuilder().startObject().field("type", "polygon")
                .startArray("coordinates")
                .startArray()
                .startArray().value(-74.011).value(40.753).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(invalidPoly);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, ElasticsearchParseException.class);

        // test case 3: create an invalid polygon with 0 points
        invalidPoly = XContentFactory.jsonBuilder().startObject().field("type", "polygon")
                .startArray("coordinates")
                .startArray()
                .startArray().endArray()
                .endArray()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(invalidPoly);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, ElasticsearchParseException.class);

        // test case 4: create an invalid polygon with null value points
        invalidPoly = XContentFactory.jsonBuilder().startObject().field("type", "polygon")
                .startArray("coordinates")
                .startArray()
                .startArray().nullValue().nullValue().endArray()
                .endArray()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(invalidPoly);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, IllegalArgumentException.class);

        // test case 5: create an invalid polygon with 1 invalid LinearRing
        invalidPoly = XContentFactory.jsonBuilder().startObject().field("type", "polygon")
                .startArray("coordinates")
                .nullValue().nullValue()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(invalidPoly);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, IllegalArgumentException.class);

        // test case 6: create an invalid polygon with 0 LinearRings
        invalidPoly = XContentFactory.jsonBuilder().startObject().field("type", "polygon")
                .startArray("coordinates").endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(invalidPoly);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, ElasticsearchParseException.class);

        // test case 7: create an invalid polygon with 0 LinearRings
        invalidPoly = XContentFactory.jsonBuilder().startObject().field("type", "polygon")
                .startArray("coordinates")
                .startArray().value(-74.011).value(40.753).endArray()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(invalidPoly);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, ElasticsearchParseException.class);
    }

    public void testParse_polygonWithHole() throws IOException {
        String polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .startArray("coordinates")
                .startArray()
                .startArray().value(100.0).value(1.0).endArray()
                .startArray().value(101.0).value(1.0).endArray()
                .startArray().value(101.0).value(0.0).endArray()
                .startArray().value(100.0).value(0.0).endArray()
                .startArray().value(100.0).value(1.0).endArray()
                .endArray()
                .startArray()
                .startArray().value(100.2).value(0.8).endArray()
                .startArray().value(100.2).value(0.2).endArray()
                .startArray().value(100.8).value(0.2).endArray()
                .startArray().value(100.8).value(0.8).endArray()
                .startArray().value(100.2).value(0.8).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        // add 3d point to test ISSUE #10501
        List<Coordinate> shellCoordinates = new ArrayList<>();
        shellCoordinates.add(new Coordinate(100, 0, 15.0));
        shellCoordinates.add(new Coordinate(101, 0));
        shellCoordinates.add(new Coordinate(101, 1));
        shellCoordinates.add(new Coordinate(100, 1, 10.0));
        shellCoordinates.add(new Coordinate(100, 0));

        List<Coordinate> holeCoordinates = new ArrayList<>();
        holeCoordinates.add(new Coordinate(100.2, 0.2));
        holeCoordinates.add(new Coordinate(100.8, 0.2));
        holeCoordinates.add(new Coordinate(100.8, 0.8));
        holeCoordinates.add(new Coordinate(100.2, 0.8));
        holeCoordinates.add(new Coordinate(100.2, 0.2));

        LinearRing shell = GEOMETRY_FACTORY.createLinearRing(
                shellCoordinates.toArray(new Coordinate[shellCoordinates.size()]));
        LinearRing[] holes = new LinearRing[1];
        holes[0] = GEOMETRY_FACTORY.createLinearRing(
                holeCoordinates.toArray(new Coordinate[holeCoordinates.size()]));
        Polygon expected = GEOMETRY_FACTORY.createPolygon(shell, holes);
        assertGeometryEquals(jtsGeom(expected), polygonGeoJson);
    }

    public void testParse_selfCrossingPolygon() throws IOException {
        // test self crossing ccw poly not crossing dateline
        String polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .startArray("coordinates")
                .startArray()
                .startArray().value(176.0).value(15.0).endArray()
                .startArray().value(-177.0).value(10.0).endArray()
                .startArray().value(-177.0).value(-10.0).endArray()
                .startArray().value(176.0).value(-15.0).endArray()
                .startArray().value(-177.0).value(15.0).endArray()
                .startArray().value(172.0).value(0.0).endArray()
                .startArray().value(176.0).value(15.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        XContentParser parser = JsonXContent.jsonXContent.createParser(polygonGeoJson);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertValidException(parser, InvalidShapeException.class);
    }

    public void testParse_multiPoint() throws IOException {
        String multiPointGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "MultiPoint")
                .startArray("coordinates")
                .startArray().value(100.0).value(0.0).endArray()
                .startArray().value(101.0).value(1.0).endArray()
                .endArray()
                .endObject().string();

        ShapeCollection expected = shapeCollection(
                SPATIAL_CONTEXT.makePoint(100, 0),
                SPATIAL_CONTEXT.makePoint(101, 1.0));
        assertGeometryEquals(expected, multiPointGeoJson);
    }

    public void testParse_multiPolygon() throws IOException {
        // test #1: two polygons; one without hole, one with hole
        String multiPolygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "MultiPolygon")
                .startArray("coordinates")
                .startArray()//first poly (without holes)
                .startArray()
                .startArray().value(102.0).value(2.0).endArray()
                .startArray().value(103.0).value(2.0).endArray()
                .startArray().value(103.0).value(3.0).endArray()
                .startArray().value(102.0).value(3.0).endArray()
                .startArray().value(102.0).value(2.0).endArray()
                .endArray()
                .endArray()
                .startArray()//second poly (with hole)
                .startArray()
                .startArray().value(100.0).value(0.0).endArray()
                .startArray().value(101.0).value(0.0).endArray()
                .startArray().value(101.0).value(1.0).endArray()
                .startArray().value(100.0).value(1.0).endArray()
                .startArray().value(100.0).value(0.0).endArray()
                .endArray()
                .startArray()//hole
                .startArray().value(100.2).value(0.8).endArray()
                .startArray().value(100.2).value(0.2).endArray()
                .startArray().value(100.8).value(0.2).endArray()
                .startArray().value(100.8).value(0.8).endArray()
                .startArray().value(100.2).value(0.8).endArray()
                .endArray()
                .endArray()
                .endArray()
                .endObject().string();

        List<Coordinate> shellCoordinates = new ArrayList<>();
        shellCoordinates.add(new Coordinate(100, 0));
        shellCoordinates.add(new Coordinate(101, 0));
        shellCoordinates.add(new Coordinate(101, 1));
        shellCoordinates.add(new Coordinate(100, 1));
        shellCoordinates.add(new Coordinate(100, 0));

        List<Coordinate> holeCoordinates = new ArrayList<>();
        holeCoordinates.add(new Coordinate(100.2, 0.2));
        holeCoordinates.add(new Coordinate(100.8, 0.2));
        holeCoordinates.add(new Coordinate(100.8, 0.8));
        holeCoordinates.add(new Coordinate(100.2, 0.8));
        holeCoordinates.add(new Coordinate(100.2, 0.2));

        LinearRing shell = GEOMETRY_FACTORY.createLinearRing(shellCoordinates.toArray(new Coordinate[shellCoordinates.size()]));
        LinearRing[] holes = new LinearRing[1];
        holes[0] = GEOMETRY_FACTORY.createLinearRing(holeCoordinates.toArray(new Coordinate[holeCoordinates.size()]));
        Polygon withHoles = GEOMETRY_FACTORY.createPolygon(shell, holes);

        shellCoordinates = new ArrayList<>();
        shellCoordinates.add(new Coordinate(102, 3));
        shellCoordinates.add(new Coordinate(103, 3));
        shellCoordinates.add(new Coordinate(103, 2));
        shellCoordinates.add(new Coordinate(102, 2));
        shellCoordinates.add(new Coordinate(102, 3));


        shell = GEOMETRY_FACTORY.createLinearRing(shellCoordinates.toArray(new Coordinate[shellCoordinates.size()]));
        Polygon withoutHoles = GEOMETRY_FACTORY.createPolygon(shell, null);

        Shape expected = shapeCollection(withoutHoles, withHoles);

        assertGeometryEquals(expected, multiPolygonGeoJson);

        // test #2: multipolygon; one polygon with one hole
        // this test converting the multipolygon from a ShapeCollection type
        // to a simple polygon (jtsGeom)
        multiPolygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "MultiPolygon")
                .startArray("coordinates")
                .startArray()
                .startArray()
                .startArray().value(100.0).value(1.0).endArray()
                .startArray().value(101.0).value(1.0).endArray()
                .startArray().value(101.0).value(0.0).endArray()
                .startArray().value(100.0).value(0.0).endArray()
                .startArray().value(100.0).value(1.0).endArray()
                .endArray()
                .startArray()// hole
                .startArray().value(100.2).value(0.8).endArray()
                .startArray().value(100.2).value(0.2).endArray()
                .startArray().value(100.8).value(0.2).endArray()
                .startArray().value(100.8).value(0.8).endArray()
                .startArray().value(100.2).value(0.8).endArray()
                .endArray()
                .endArray()
                .endArray()
                .endObject().string();

        shellCoordinates = new ArrayList<>();
        shellCoordinates.add(new Coordinate(100, 1));
        shellCoordinates.add(new Coordinate(101, 1));
        shellCoordinates.add(new Coordinate(101, 0));
        shellCoordinates.add(new Coordinate(100, 0));
        shellCoordinates.add(new Coordinate(100, 1));

        holeCoordinates = new ArrayList<>();
        holeCoordinates.add(new Coordinate(100.2, 0.8));
        holeCoordinates.add(new Coordinate(100.2, 0.2));
        holeCoordinates.add(new Coordinate(100.8, 0.2));
        holeCoordinates.add(new Coordinate(100.8, 0.8));
        holeCoordinates.add(new Coordinate(100.2, 0.8));

        shell = GEOMETRY_FACTORY.createLinearRing(shellCoordinates.toArray(new Coordinate[shellCoordinates.size()]));
        holes = new LinearRing[1];
        holes[0] = GEOMETRY_FACTORY.createLinearRing(holeCoordinates.toArray(new Coordinate[holeCoordinates.size()]));
        withHoles = GEOMETRY_FACTORY.createPolygon(shell, holes);

        assertGeometryEquals(jtsGeom(withHoles), multiPolygonGeoJson);
    }

    public void testParse_geometryCollection() throws IOException {
        String geometryCollectionGeoJson = XContentFactory.jsonBuilder().startObject()
                .field("type", "GeometryCollection")
                .startArray("geometries")
                    .startObject()
                        .field("type", "LineString")
                        .startArray("coordinates")
                            .startArray().value(100.0).value(0.0).endArray()
                            .startArray().value(101.0).value(1.0).endArray()
                        .endArray()
                    .endObject()
                    .startObject()
                        .field("type", "Point")
                        .startArray("coordinates").value(102.0).value(2.0).endArray()
                    .endObject()
                .endArray()
                .endObject()
                .string();

        Shape[] expected = new Shape[2];
        LineString expectedLineString = GEOMETRY_FACTORY.createLineString(new Coordinate[]{
                new Coordinate(100, 0),
                new Coordinate(101, 1),
        });
        expected[0] = jtsGeom(expectedLineString);
        Point expectedPoint = GEOMETRY_FACTORY.createPoint(new Coordinate(102.0, 2.0));
        expected[1] = new JtsPoint(expectedPoint, SPATIAL_CONTEXT);

        //equals returns true only if geometries are in the same order
        assertGeometryEquals(shapeCollection(expected), geometryCollectionGeoJson);
    }

    public void testThatParserExtractsCorrectTypeAndCoordinatesFromArbitraryJson() throws IOException {
        String pointGeoJson = XContentFactory.jsonBuilder().startObject()
                .startObject("crs")
                    .field("type", "name")
                    .startObject("properties")
                        .field("name", "urn:ogc:def:crs:OGC:1.3:CRS84")
                    .endObject()
                .endObject()
                .field("bbox", "foobar")
                .field("type", "point")
                .field("bubu", "foobar")
                .startArray("coordinates").value(100.0).value(0.0).endArray()
                .startObject("nested").startArray("coordinates").value(200.0).value(0.0).endArray().endObject()
                .startObject("lala").field("type", "NotAPoint").endObject()
                .endObject().string();

        Point expected = GEOMETRY_FACTORY.createPoint(new Coordinate(100.0, 0.0));
        assertGeometryEquals(new JtsPoint(expected, SPATIAL_CONTEXT), pointGeoJson);
    }

    public void testParse_orientationOption() throws IOException {
        // test 1: valid ccw (right handed system) poly not crossing dateline (with 'right' field)
        String polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .field("orientation", "right")
                .startArray("coordinates")
                .startArray()
                .startArray().value(176.0).value(15.0).endArray()
                .startArray().value(-177.0).value(10.0).endArray()
                .startArray().value(-177.0).value(-10.0).endArray()
                .startArray().value(176.0).value(-15.0).endArray()
                .startArray().value(172.0).value(0.0).endArray()
                .startArray().value(176.0).value(15.0).endArray()
                .endArray()
                .startArray()
                .startArray().value(-172.0).value(8.0).endArray()
                .startArray().value(174.0).value(10.0).endArray()
                .startArray().value(-172.0).value(-8.0).endArray()
                .startArray().value(-172.0).value(8.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        XContentParser parser = JsonXContent.jsonXContent.createParser(polygonGeoJson);
        parser.nextToken();
        Shape shape = ShapeBuilder.parse(parser).build();

        ElasticsearchGeoAssertions.assertPolygon(shape);

        // test 2: valid ccw (right handed system) poly not crossing dateline (with 'ccw' field)
        polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .field("orientation", "ccw")
                .startArray("coordinates")
                .startArray()
                .startArray().value(176.0).value(15.0).endArray()
                .startArray().value(-177.0).value(10.0).endArray()
                .startArray().value(-177.0).value(-10.0).endArray()
                .startArray().value(176.0).value(-15.0).endArray()
                .startArray().value(172.0).value(0.0).endArray()
                .startArray().value(176.0).value(15.0).endArray()
                .endArray()
                .startArray()
                .startArray().value(-172.0).value(8.0).endArray()
                .startArray().value(174.0).value(10.0).endArray()
                .startArray().value(-172.0).value(-8.0).endArray()
                .startArray().value(-172.0).value(8.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(polygonGeoJson);
        parser.nextToken();
        shape = ShapeBuilder.parse(parser).build();

        ElasticsearchGeoAssertions.assertPolygon(shape);

        // test 3: valid ccw (right handed system) poly not crossing dateline (with 'counterclockwise' field)
        polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .field("orientation", "counterclockwise")
                .startArray("coordinates")
                .startArray()
                .startArray().value(176.0).value(15.0).endArray()
                .startArray().value(-177.0).value(10.0).endArray()
                .startArray().value(-177.0).value(-10.0).endArray()
                .startArray().value(176.0).value(-15.0).endArray()
                .startArray().value(172.0).value(0.0).endArray()
                .startArray().value(176.0).value(15.0).endArray()
                .endArray()
                .startArray()
                .startArray().value(-172.0).value(8.0).endArray()
                .startArray().value(174.0).value(10.0).endArray()
                .startArray().value(-172.0).value(-8.0).endArray()
                .startArray().value(-172.0).value(8.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(polygonGeoJson);
        parser.nextToken();
        shape = ShapeBuilder.parse(parser).build();

        ElasticsearchGeoAssertions.assertPolygon(shape);

        // test 4: valid cw (left handed system) poly crossing dateline (with 'left' field)
        polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .field("orientation", "left")
                .startArray("coordinates")
                .startArray()
                .startArray().value(176.0).value(15.0).endArray()
                .startArray().value(-177.0).value(10.0).endArray()
                .startArray().value(-177.0).value(-10.0).endArray()
                .startArray().value(176.0).value(-15.0).endArray()
                .startArray().value(172.0).value(0.0).endArray()
                .startArray().value(176.0).value(15.0).endArray()
                .endArray()
                .startArray()
                .startArray().value(-178.0).value(8.0).endArray()
                .startArray().value(178.0).value(8.0).endArray()
                .startArray().value(180.0).value(-8.0).endArray()
                .startArray().value(-178.0).value(8.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(polygonGeoJson);
        parser.nextToken();
        shape = ShapeBuilder.parse(parser).build();

        ElasticsearchGeoAssertions.assertMultiPolygon(shape);

        // test 5: valid cw multipoly (left handed system) poly crossing dateline (with 'cw' field)
        polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .field("orientation", "cw")
                .startArray("coordinates")
                .startArray()
                .startArray().value(176.0).value(15.0).endArray()
                .startArray().value(-177.0).value(10.0).endArray()
                .startArray().value(-177.0).value(-10.0).endArray()
                .startArray().value(176.0).value(-15.0).endArray()
                .startArray().value(172.0).value(0.0).endArray()
                .startArray().value(176.0).value(15.0).endArray()
                .endArray()
                .startArray()
                .startArray().value(-178.0).value(8.0).endArray()
                .startArray().value(178.0).value(8.0).endArray()
                .startArray().value(180.0).value(-8.0).endArray()
                .startArray().value(-178.0).value(8.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(polygonGeoJson);
        parser.nextToken();
        shape = ShapeBuilder.parse(parser).build();

        ElasticsearchGeoAssertions.assertMultiPolygon(shape);

        // test 6: valid cw multipoly (left handed system) poly crossing dateline (with 'clockwise' field)
        polygonGeoJson = XContentFactory.jsonBuilder().startObject().field("type", "Polygon")
                .field("orientation", "clockwise")
                .startArray("coordinates")
                .startArray()
                .startArray().value(176.0).value(15.0).endArray()
                .startArray().value(-177.0).value(10.0).endArray()
                .startArray().value(-177.0).value(-10.0).endArray()
                .startArray().value(176.0).value(-15.0).endArray()
                .startArray().value(172.0).value(0.0).endArray()
                .startArray().value(176.0).value(15.0).endArray()
                .endArray()
                .startArray()
                .startArray().value(-178.0).value(8.0).endArray()
                .startArray().value(178.0).value(8.0).endArray()
                .startArray().value(180.0).value(-8.0).endArray()
                .startArray().value(-178.0).value(8.0).endArray()
                .endArray()
                .endArray()
                .endObject().string();

        parser = JsonXContent.jsonXContent.createParser(polygonGeoJson);
        parser.nextToken();
        shape = ShapeBuilder.parse(parser).build();

        ElasticsearchGeoAssertions.assertMultiPolygon(shape);
    }

    private void assertGeometryEquals(Shape expected, String geoJson) throws IOException {
        XContentParser parser = JsonXContent.jsonXContent.createParser(geoJson);
        parser.nextToken();
        ElasticsearchGeoAssertions.assertEquals(expected, ShapeBuilder.parse(parser).build());
    }

    private ShapeCollection<Shape> shapeCollection(Shape... shapes) {
        return new ShapeCollection<>(Arrays.asList(shapes), SPATIAL_CONTEXT);
    }

    private ShapeCollection<Shape> shapeCollection(Geometry... geoms) {
        List<Shape> shapes = new ArrayList<>(geoms.length);
        for (Geometry geom : geoms) {
            shapes.add(jtsGeom(geom));
        }
        return new ShapeCollection<>(shapes, SPATIAL_CONTEXT);
    }

    private JtsGeometry jtsGeom(Geometry geom) {
        return new JtsGeometry(geom, SPATIAL_CONTEXT, false, false);
    }

}
