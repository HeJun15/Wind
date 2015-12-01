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

package org.elasticsearch;

import org.elasticsearch.common.SuppressForbidden;
import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * Information about a build of Elasticsearch.
 */
public class Build {
    /**
     * The current build of Elasticsearch. Filled with information scanned at
     * startup from the jar.
     */
    public static final Build CURRENT;

    static {
        final String shortHash;
        final String date;

        Path path = getElasticsearchCodebase();
        if (path.toString().endsWith(".jar")) {
            try (JarInputStream jar = new JarInputStream(Files.newInputStream(path))) {
                Manifest manifest = jar.getManifest();
                shortHash = manifest.getMainAttributes().getValue("Change");
                date = manifest.getMainAttributes().getValue("Build-Date");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            // not running from a jar (unit tests, IDE)
            shortHash = "Unknown";
            date = "Unknown";
        }
        if (shortHash == null) {
            throw new IllegalStateException("Error finding the build shortHash. " +
                "Stopping Elasticsearch now so it doesn't run in subtly broken ways. This is likely a build bug.");
        }
        if (date == null) {
            throw new IllegalStateException("Error finding the build date. " +
                "Stopping Elasticsearch now so it doesn't run in subtly broken ways. This is likely a build bug.");
        }

        CURRENT = new Build(shortHash, date);
    }

    /**
     * Returns path to elasticsearch codebase path
     */
    @SuppressForbidden(reason = "looks up path of elasticsearch.jar directly")
    static Path getElasticsearchCodebase() {
        URL url = Build.class.getProtectionDomain().getCodeSource().getLocation();
        try {
            return PathUtils.get(url.toURI());
        } catch (URISyntaxException bogus) {
            throw new RuntimeException(bogus);
        }
    }

    private String shortHash;
    private String date;

    Build(String shortHash, String date) {
        this.shortHash = shortHash;
        this.date = date;
    }

    public String shortHash() {
        return shortHash;
    }

    public String date() {
        return date;
    }

    public static Build readBuild(StreamInput in) throws IOException {
        String hash = in.readString();
        String date = in.readString();
        return new Build(hash, date);
    }

    public static void writeBuild(Build build, StreamOutput out) throws IOException {
        out.writeString(build.shortHash());
        out.writeString(build.date());
    }

    @Override
    public String toString() {
        return "[" + shortHash + "][" + date + "]";
    }
}
