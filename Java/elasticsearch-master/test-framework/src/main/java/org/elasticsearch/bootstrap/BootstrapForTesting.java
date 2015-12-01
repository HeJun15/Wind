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

package org.elasticsearch.bootstrap;

import com.carrotsearch.randomizedtesting.RandomizedRunner;

import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestSecurityManager;
import org.elasticsearch.SecureSM;
import org.elasticsearch.bootstrap.Bootstrap;
import org.elasticsearch.bootstrap.ESPolicy;
import org.elasticsearch.bootstrap.Security;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.SuppressForbidden;
import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.plugins.PluginInfo;
import org.junit.Assert;

import java.io.FilePermission;
import java.io.InputStream;
import java.net.SocketPermission;
import java.net.URL;
import java.nio.file.Path;
import java.security.Permission;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import static com.carrotsearch.randomizedtesting.RandomizedTest.systemPropertyAsBoolean;

/** 
 * Initializes natives and installs test security manager
 * (init'd early by base classes to ensure it happens regardless of which
 * test case happens to be first, test ordering, etc). 
 * <p>
 * The idea is to mimic as much as possible what happens with ES in production
 * mode (e.g. assign permissions and install security manager the same way)
 */
public class BootstrapForTesting {
    
    // TODO: can we share more code with the non-test side here
    // without making things complex???

    static {
        // make sure java.io.tmpdir exists always (in case code uses it in a static initializer)
        Path javaTmpDir = PathUtils.get(Objects.requireNonNull(System.getProperty("java.io.tmpdir"),
                                                               "please set ${java.io.tmpdir} in pom.xml"));
        try {
            Security.ensureDirectoryExists(javaTmpDir);
        } catch (Exception e) {
            throw new RuntimeException("unable to create test temp directory", e);
        }

        // just like bootstrap, initialize natives, then SM
        Bootstrap.initializeNatives(javaTmpDir, true, true, true);

        // initialize probes
        Bootstrap.initializeProbes();
        
        // initialize sysprops
        BootstrapInfo.getSystemProperties();
        
        // check for jar hell
        try {
            JarHell.checkJarHell();
        } catch (Exception e) {
            throw new RuntimeException("found jar hell in test classpath", e);
        }

        // install security manager if requested
        if (systemPropertyAsBoolean("tests.security.manager", true)) {
            try {
                // initialize paths the same exact way as bootstrap
                Permissions perms = new Permissions();
                Security.addClasspathPermissions(perms);
                // crazy jython
                for (URL url : JarHell.parseClassPath()) {
                    Path path = PathUtils.get(url.toURI());

                    // crazy jython...
                    String filename = path.getFileName().toString();
                    if (filename.contains("jython") && filename.endsWith(".jar")) {
                        // just enough so it won't fail when it does not exist
                        perms.add(new FilePermission(path.getParent().toString(), "read,readlink"));
                        perms.add(new FilePermission(path.getParent().resolve("Lib").toString(), "read,readlink"));
                    }
                }
                // java.io.tmpdir
                Security.addPath(perms, "java.io.tmpdir", javaTmpDir, "read,readlink,write,delete");
                // custom test config file
                if (Strings.hasLength(System.getProperty("tests.config"))) {
                    perms.add(new FilePermission(System.getProperty("tests.config"), "read,readlink"));
                }
                // jacoco coverage output file
                if (Boolean.getBoolean("tests.coverage")) {
                    Path coverageDir = PathUtils.get(System.getProperty("tests.coverage.dir"));
                    perms.add(new FilePermission(coverageDir.resolve("jacoco.exec").toString(), "read,write"));
                    // in case we get fancy and use the -integration goals later:
                    perms.add(new FilePermission(coverageDir.resolve("jacoco-it.exec").toString(), "read,write"));
                }
                // intellij hack: intellij test runner wants setIO and will
                // screw up all test logging without it!
                if (System.getProperty("tests.maven") == null) {
                    perms.add(new RuntimePermission("setIO"));
                }
                
                // add bind permissions for testing
                // ephemeral ports (note, on java 7 before update 51, this is a different permission)
                // this should really be the only one allowed for tests, otherwise they have race conditions
                perms.add(new SocketPermission("localhost:0", "listen,resolve"));
                // ... but tests are messy. like file permissions, just let them live in a fantasy for now.
                // TODO: cut over all tests to bind to ephemeral ports
                perms.add(new SocketPermission("localhost:1024-", "listen,resolve"));
                
                // read test-framework permissions
                final Policy testFramework = Security.readPolicy(Bootstrap.class.getResource("test-framework.policy"), JarHell.parseClassPath());
                final Policy esPolicy = new ESPolicy(perms, getPluginPermissions(), true);
                Policy.setPolicy(new Policy() {
                    @Override
                    public boolean implies(ProtectionDomain domain, Permission permission) {
                        // implements union
                        return esPolicy.implies(domain, permission) || testFramework.implies(domain, permission);
                    }
                });
                System.setSecurityManager(new SecureSM(true));
                Security.selfTest();

                // guarantee plugin classes are initialized first, in case they have one-time hacks.
                // this just makes unit testing more realistic
                for (URL url : Collections.list(BootstrapForTesting.class.getClassLoader().getResources(PluginInfo.ES_PLUGIN_PROPERTIES))) {
                    Properties properties = new Properties();
                    try (InputStream stream = url.openStream()) {
                        properties.load(stream);
                    }
                    if (Boolean.parseBoolean(properties.getProperty("jvm"))) {
                        String clazz = properties.getProperty("classname");
                        if (clazz != null) {
                            Class.forName(clazz);
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("unable to install test security manager", e);
            }
        }
    }

    /** 
     * we dont know which codesources belong to which plugin, so just remove the permission from key codebases
     * like core, test-framework, etc. this way tests fail if accesscontroller blocks are missing.
     */
    @SuppressForbidden(reason = "accesses fully qualified URLs to configure security")
    static Map<String,Policy> getPluginPermissions() throws Exception {
        List<URL> pluginPolicies = Collections.list(BootstrapForTesting.class.getClassLoader().getResources(PluginInfo.ES_PLUGIN_POLICY));
        if (pluginPolicies.isEmpty()) {
            return Collections.emptyMap();
        }
        
        // compute classpath minus obvious places, all other jars will get the permission.
        Set<URL> codebases = new HashSet<>(Arrays.asList(parseClassPathWithSymlinks()));
        Set<URL> excluded = new HashSet<>(Arrays.asList(
                // es core
                Bootstrap.class.getProtectionDomain().getCodeSource().getLocation(),
                // es test framework
                BootstrapForTesting.class.getProtectionDomain().getCodeSource().getLocation(),
                // lucene test framework
                LuceneTestCase.class.getProtectionDomain().getCodeSource().getLocation(),
                // randomized runner
                RandomizedRunner.class.getProtectionDomain().getCodeSource().getLocation(),
                // junit library
                Assert.class.getProtectionDomain().getCodeSource().getLocation()
        ));
        codebases.removeAll(excluded);
        
        // parse each policy file, with codebase substitution from the classpath
        final List<Policy> policies = new ArrayList<>();
        for (URL policyFile : pluginPolicies) {
            policies.add(Security.readPolicy(policyFile, codebases.toArray(new URL[codebases.size()])));
        }
        
        // consult each policy file for those codebases
        Map<String,Policy> map = new HashMap<>();
        for (URL url : codebases) {
            map.put(url.getFile(), new Policy() {
                @Override
                public boolean implies(ProtectionDomain domain, Permission permission) {
                    // implements union
                    for (Policy p : policies) {
                        if (p.implies(domain, permission)) {
                            return true;
                        }
                    }
                    return false;
                }
            });
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * return parsed classpath, but with symlinks resolved to destination files for matching
     * this is for matching the toRealPath() in the code where we have a proper plugin structure
     */
    @SuppressForbidden(reason = "does evil stuff with paths and urls because devs and jenkins do evil stuff with paths and urls")
    static URL[] parseClassPathWithSymlinks() throws Exception {
        URL raw[] = JarHell.parseClassPath();
        for (int i = 0; i < raw.length; i++) {
            raw[i] = PathUtils.get(raw[i].toURI()).toRealPath().toUri().toURL();
        }
        return raw;
    }

    // does nothing, just easy way to make sure the class is loaded.
    public static void ensureInitialized() {}
}
