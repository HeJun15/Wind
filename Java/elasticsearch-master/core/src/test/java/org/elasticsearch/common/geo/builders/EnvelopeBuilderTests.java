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

package org.elasticsearch.common.geo.builders;

import com.spatial4j.core.shape.Rectangle;
import com.vividsolutions.jts.geom.Coordinate;

import org.elasticsearch.common.geo.builders.ShapeBuilder.Orientation;
import org.elasticsearch.test.geo.RandomShapeGenerator;

import java.io.IOException;

public class EnvelopeBuilderTests extends AbstractShapeBuilderTestCase<EnvelopeBuilder> {

    @Override
    protected EnvelopeBuilder createTestShapeBuilder() {
        EnvelopeBuilder envelope = new EnvelopeBuilder(randomFrom(Orientation.values()));
        Rectangle box = RandomShapeGenerator.xRandomRectangle(getRandom(), RandomShapeGenerator.xRandomPoint(getRandom()));
        envelope.topLeft(box.getMinX(), box.getMaxY())
                .bottomRight(box.getMaxX(), box.getMinY());
        return envelope;
    }

    @Override
    protected EnvelopeBuilder mutate(EnvelopeBuilder original) throws IOException {
        EnvelopeBuilder mutation = copyShape(original);
        if (randomBoolean()) {
            // toggle orientation
            mutation.orientation = (original.orientation == Orientation.LEFT ? Orientation.RIGHT : Orientation.LEFT);
        } else {
            // move one corner to the middle of original
            switch (randomIntBetween(0, 3)) {
            case 0:
                mutation.topLeft(new Coordinate(randomDoubleBetween(-180.0, original.bottomRight.x, true), original.topLeft.y));
                break;
            case 1:
                mutation.topLeft(new Coordinate(original.topLeft.x, randomDoubleBetween(original.bottomRight.y, 90.0, true)));
                break;
            case 2:
                mutation.bottomRight(new Coordinate(randomDoubleBetween(original.topLeft.x, 180.0, true), original.bottomRight.y));
                break;
            case 3:
                mutation.bottomRight(new Coordinate(original.bottomRight.x, randomDoubleBetween(-90.0, original.topLeft.y, true)));
                break;
            }
        }
        return mutation;
    }
}
