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

package org.elasticsearch.cloud.aws;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.discovery.ec2.Ec2Discovery;

public class Ec2Module extends AbstractModule {

    @Override
    protected void configure() {
        bind(AwsEc2Service.class).to(AwsEc2ServiceImpl.class).asEagerSingleton();
    }

    /**
     * Check if discovery is meant to start
     * @return true if we can start discovery features
     */
    public static boolean isEc2DiscoveryActive(Settings settings, ESLogger logger) {
        // User set discovery.type: ec2
        if (!Ec2Discovery.EC2.equalsIgnoreCase(settings.get("discovery.type"))) {
            logger.trace("discovery.type not set to {}", Ec2Discovery.EC2);
            return false;
        }

        return true;
    }
}
