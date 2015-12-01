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
package org.elasticsearch.test.rest.test;

import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.rest.spec.RestApiParser;

import java.io.IOException;

import static org.hamcrest.Matchers.containsString;

/**
 *
 */
public class RestApiParserFailingTests extends ESTestCase {
    public void testBrokenSpecShouldThrowUsefulExceptionWhenParsingFailsOnParams() throws Exception {
        parseAndExpectFailure(BROKEN_SPEC_PARAMS, "Expected params field in rest api definition to contain an object");
    }

    public void testBrokenSpecShouldThrowUsefulExceptionWhenParsingFailsOnParts() throws Exception {
        parseAndExpectFailure(BROKEN_SPEC_PARTS, "Expected parts field in rest api definition to contain an object");
    }

    private void parseAndExpectFailure(String brokenJson, String expectedErrorMessage) throws Exception {
        XContentParser parser = JsonXContent.jsonXContent.createParser(brokenJson);
        try {
            new RestApiParser().parse(parser);
            fail("Expected to fail parsing but did not happen");
        } catch (IOException e) {
            assertThat(e.getMessage(), containsString(expectedErrorMessage));
        }
    }

    // see params section is broken, an inside param is missing
    private static final String BROKEN_SPEC_PARAMS = "{\n" +
            "  \"ping\": {" +
            "    \"documentation\": \"http://www.elasticsearch.org/guide/\"," +
            "    \"methods\": [\"HEAD\"]," +
            "    \"url\": {" +
            "      \"path\": \"/\"," +
            "      \"paths\": [\"/\"]," +
            "      \"parts\": {" +
            "      }," +
            "      \"params\": {" +
            "        \"type\" : \"boolean\",\n" +
            "        \"description\" : \"Whether specified concrete indices should be ignored when unavailable (missing or closed)\"\n" +
            "      }" +
            "    }," +
            "    \"body\": null" +
            "  }" +
            "}";

    // see parts section is broken, an inside param is missing
    private static final String BROKEN_SPEC_PARTS = "{\n" +
            "  \"ping\": {" +
            "    \"documentation\": \"http://www.elasticsearch.org/guide/\"," +
            "    \"methods\": [\"HEAD\"]," +
            "    \"url\": {" +
            "      \"path\": \"/\"," +
            "      \"paths\": [\"/\"]," +
            "      \"parts\": {" +
            "          \"type\" : \"boolean\",\n" +
            "      }," +
            "      \"params\": {\n" +
            "        \"ignore_unavailable\": {\n" +
            "          \"type\" : \"boolean\",\n" +
            "          \"description\" : \"Whether specified concrete indices should be ignored when unavailable (missing or closed)\"\n" +
            "        } \n" +
            "    }," +
            "    \"body\": null" +
            "  }" +
            "}";

}
