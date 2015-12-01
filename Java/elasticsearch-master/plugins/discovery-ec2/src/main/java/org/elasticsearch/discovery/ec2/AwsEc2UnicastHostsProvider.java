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

package org.elasticsearch.discovery.ec2;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;
import org.elasticsearch.Version;
import org.elasticsearch.cloud.aws.AwsEc2Service;
import org.elasticsearch.cloud.aws.AwsEc2Service.DISCOVERY_EC2;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.SingleObjectCache;
import org.elasticsearch.discovery.zen.ping.unicast.UnicastHostsProvider;
import org.elasticsearch.transport.TransportService;

import java.util.*;

/**
 *
 */
public class AwsEc2UnicastHostsProvider extends AbstractComponent implements UnicastHostsProvider {

    private static enum HostType {
        PRIVATE_IP,
        PUBLIC_IP,
        PRIVATE_DNS,
        PUBLIC_DNS
    }

    private final TransportService transportService;

    private final AmazonEC2 client;

    private final Version version;

    private final boolean bindAnyGroup;

    private final Set<String> groups;

    private final Map<String, String> tags;

    private final Set<String> availabilityZones;

    private final HostType hostType;

    private final DiscoNodesCache discoNodes;

    @Inject
    public AwsEc2UnicastHostsProvider(Settings settings, TransportService transportService, AwsEc2Service awsEc2Service, Version version) {
        super(settings);
        this.transportService = transportService;
        this.client = awsEc2Service.client();
        this.version = version;

        this.hostType = HostType.valueOf(settings.get(DISCOVERY_EC2.HOST_TYPE, "private_ip")
                .toUpperCase(Locale.ROOT));

        this.discoNodes = new DiscoNodesCache(this.settings.getAsTime(DISCOVERY_EC2.NODE_CACHE_TIME,
                                              TimeValue.timeValueMillis(10_000L)));

        this.bindAnyGroup = settings.getAsBoolean(DISCOVERY_EC2.ANY_GROUP, true);
        this.groups = new HashSet<>();
        groups.addAll(Arrays.asList(settings.getAsArray(DISCOVERY_EC2.GROUPS)));

        this.tags = settings.getByPrefix(DISCOVERY_EC2.TAG_PREFIX).getAsMap();

        Set<String> availabilityZones = new HashSet<>();
        availabilityZones.addAll(Arrays.asList(settings.getAsArray(DISCOVERY_EC2.AVAILABILITY_ZONES)));
        if (settings.get(DISCOVERY_EC2.AVAILABILITY_ZONES) != null) {
            availabilityZones.addAll(Strings.commaDelimitedListToSet(settings.get(DISCOVERY_EC2.AVAILABILITY_ZONES)));
        }
        this.availabilityZones = availabilityZones;

        if (logger.isDebugEnabled()) {
            logger.debug("using host_type [{}], tags [{}], groups [{}] with any_group [{}], availability_zones [{}]", hostType, tags, groups, bindAnyGroup, availabilityZones);
        }
    }

    @Override
    public List<DiscoveryNode> buildDynamicNodes() {
        return discoNodes.getOrRefresh();
    }

    protected List<DiscoveryNode> fetchDynamicNodes() {

        List<DiscoveryNode> discoNodes = new ArrayList<>();

        DescribeInstancesResult descInstances;
        try {
            // Query EC2 API based on AZ, instance state, and tag.

            // NOTE: we don't filter by security group during the describe instances request for two reasons:
            // 1. differences in VPCs require different parameters during query (ID vs Name)
            // 2. We want to use two different strategies: (all security groups vs. any security groups)
            descInstances = client.describeInstances(buildDescribeInstancesRequest());
        } catch (AmazonClientException e) {
            logger.info("Exception while retrieving instance list from AWS API: {}", e.getMessage());
            logger.debug("Full exception:", e);
            return discoNodes;
        }

        logger.trace("building dynamic unicast discovery nodes...");
        for (Reservation reservation : descInstances.getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                // lets see if we can filter based on groups
                if (!groups.isEmpty()) {
                    List<GroupIdentifier> instanceSecurityGroups = instance.getSecurityGroups();
                    ArrayList<String> securityGroupNames = new ArrayList<String>();
                    ArrayList<String> securityGroupIds = new ArrayList<String>();
                    for (GroupIdentifier sg : instanceSecurityGroups) {
                        securityGroupNames.add(sg.getGroupName());
                        securityGroupIds.add(sg.getGroupId());
                    }
                    if (bindAnyGroup) {
                        // We check if we can find at least one group name or one group id in groups.
                        if (Collections.disjoint(securityGroupNames, groups)
                                && Collections.disjoint(securityGroupIds, groups)) {
                            logger.trace("filtering out instance {} based on groups {}, not part of {}", instance.getInstanceId(), instanceSecurityGroups, groups);
                            // continue to the next instance
                            continue;
                        }
                    } else {
                        // We need tp match all group names or group ids, otherwise we ignore this instance
                        if (!(securityGroupNames.containsAll(groups) || securityGroupIds.containsAll(groups))) {
                            logger.trace("filtering out instance {} based on groups {}, does not include all of {}", instance.getInstanceId(), instanceSecurityGroups, groups);
                            // continue to the next instance
                            continue;
                        }
                    }
                }

                String address = null;
                switch (hostType) {
                    case PRIVATE_DNS:
                        address = instance.getPrivateDnsName();
                        break;
                    case PRIVATE_IP:
                        address = instance.getPrivateIpAddress();
                        break;
                    case PUBLIC_DNS:
                        address = instance.getPublicDnsName();
                        break;
                    case PUBLIC_IP:
                        address = instance.getPublicIpAddress();
                        break;
                }
                if (address != null) {
                    try {
                        // we only limit to 1 port per address, makes no sense to ping 100 ports
                        TransportAddress[] addresses = transportService.addressesFromString(address, 1);
                        for (int i = 0; i < addresses.length; i++) {
                            logger.trace("adding {}, address {}, transport_address {}", instance.getInstanceId(), address, addresses[i]);
                            discoNodes.add(new DiscoveryNode("#cloud-" + instance.getInstanceId() + "-" + i, addresses[i], version.minimumCompatibilityVersion()));
                        }
                    } catch (Exception e) {
                        logger.warn("failed ot add {}, address {}", e, instance.getInstanceId(), address);
                    }
                } else {
                    logger.trace("not adding {}, address is null, host_type {}", instance.getInstanceId(), hostType);
                }
            }
        }

        logger.debug("using dynamic discovery nodes {}", discoNodes);

        return discoNodes;
    }

    private DescribeInstancesRequest buildDescribeInstancesRequest() {
        DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest()
            .withFilters(
                new Filter("instance-state-name").withValues("running", "pending")
            );

        for (Map.Entry<String, String> tagFilter : tags.entrySet()) {
            // for a given tag key, OR relationship for multiple different values
            describeInstancesRequest.withFilters(
                new Filter("tag:" + tagFilter.getKey()).withValues(tagFilter.getValue())
            );
        }

        if (!availabilityZones.isEmpty()) {
            // OR relationship amongst multiple values of the availability-zone filter
            describeInstancesRequest.withFilters(
                new Filter("availability-zone").withValues(availabilityZones)
            );
        }

        return describeInstancesRequest;
    }

    private final class DiscoNodesCache extends SingleObjectCache<List<DiscoveryNode>> {

        private boolean empty = true;

        protected DiscoNodesCache(TimeValue refreshInterval) {
            super(refreshInterval,  new ArrayList<>());
        }

        @Override
        protected boolean needsRefresh() {
            return (empty || super.needsRefresh());
        }

        @Override
        protected List<DiscoveryNode> refresh() {
            List<DiscoveryNode> nodes = fetchDynamicNodes();
            empty = nodes.isEmpty();
            return nodes;
        }
    }
}
