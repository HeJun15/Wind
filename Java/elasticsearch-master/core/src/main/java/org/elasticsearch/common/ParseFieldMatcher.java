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

package org.elasticsearch.common;

import org.elasticsearch.common.settings.Settings;

import java.util.EnumSet;

/**
 * Matcher to use in combination with {@link ParseField} while parsing requests. Matches a {@link ParseField}
 * against a field name and throw deprecation exception depending on the current value of the {@link #PARSE_STRICT} setting.
 */
public class ParseFieldMatcher {
    public static final String PARSE_STRICT = "index.query.parse.strict";
    public static final ParseFieldMatcher EMPTY = new ParseFieldMatcher(ParseField.EMPTY_FLAGS);
    public static final ParseFieldMatcher STRICT = new ParseFieldMatcher(ParseField.STRICT_FLAGS);

    private final EnumSet<ParseField.Flag> parseFlags;

    public ParseFieldMatcher(Settings settings) {
        if (settings.getAsBoolean(PARSE_STRICT, false)) {
            this.parseFlags = EnumSet.of(ParseField.Flag.STRICT);
        } else {
            this.parseFlags = ParseField.EMPTY_FLAGS;
        }
    }

    public ParseFieldMatcher(EnumSet<ParseField.Flag> parseFlags) {
        this.parseFlags = parseFlags;
    }

    /**
     * Matches a {@link ParseField} against a field name, and throws deprecation exception depending on the current
     * value of the {@link #PARSE_STRICT} setting.
     * @param fieldName the field name found in the request while parsing
     * @param parseField the parse field that we are looking for
     * @throws IllegalArgumentException whenever we are in strict mode and the request contained a deprecated field
     * @return true whenever the parse field that we are looking for was found, false otherwise
     */
    public boolean match(String fieldName, ParseField parseField) {
        return parseField.match(fieldName, parseFlags);
    }
}
