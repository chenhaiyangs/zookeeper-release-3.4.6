/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zookeeper.server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

import org.apache.zookeeper.Version;
import org.apache.zookeeper.jmx.ZKMBeanInfo;

/**
 * This class implements the ZooKeeper server MBean interface.
 * ZookeeperServerMBean
 * 注册到JMX中的ZookeeperServer的Bean
 */
public class ZooKeeperServerBean implements ZooKeeperServerMXBean, ZKMBeanInfo {
    private final Date startTime;
    private final String name;
    
    protected final ZooKeeperServer zks;
    
    public ZooKeeperServerBean(ZooKeeperServer zks) {
        startTime = new Date();
        this.zks = zks;
        name = "StandaloneServer_port" + zks.getClientPort();
    }

    @Override
    public String getClientPort() {
        try {
            return InetAddress.getLocalHost().getHostAddress() + ":"
                + zks.getClientPort();
        } catch (UnknownHostException e) {
            return "localhost:" + zks.getClientPort();
        }
    }

    @Override
    public String getName() {
        return name;
    }
    @Override
    public boolean isHidden() {
        return false;
    }
    @Override
    public String getStartTime() {
        return startTime.toString();
    }
    @Override
    public String getVersion() {
        return Version.getFullVersion();
    }
    @Override
    public long getAvgRequestLatency() {
        return zks.serverStats().getAvgLatency();
    }
    @Override
    public long getMaxRequestLatency() {
        return zks.serverStats().getMaxLatency();
    }
    @Override
    public long getMinRequestLatency() {
        return zks.serverStats().getMinLatency();
    }
    @Override
    public long getOutstandingRequests() {
        return zks.serverStats().getOutstandingRequests();
    }
    @Override
    public int getTickTime() {
        return zks.getTickTime();
    }
    @Override
    public void setTickTime(int tickTime) {
        zks.setTickTime(tickTime);
    }
    @Override
    public int getMaxClientCnxnsPerHost() {
        ServerCnxnFactory fac = zks.getServerCnxnFactory();
        if (fac == null) {
            return -1;
        }
        return fac.getMaxClientCnxnsPerHost();
    }
    @Override
    public void setMaxClientCnxnsPerHost(int max) {
        // if fac is null the exception will be propagated to the client
        zks.getServerCnxnFactory().setMaxClientCnxnsPerHost(max);
    }
    @Override
    public int getMinSessionTimeout() {
        return zks.getMinSessionTimeout();
    }
    @Override
    public void setMinSessionTimeout(int min) {
        zks.setMinSessionTimeout(min);
    }
    @Override
    public int getMaxSessionTimeout() {
        return zks.getMaxSessionTimeout();
    }
    @Override
    public void setMaxSessionTimeout(int max) {
        zks.setMaxSessionTimeout(max);
    }

    @Override
    public long getPacketsReceived() {
        return zks.serverStats().getPacketsReceived();
    }
    @Override
    public long getPacketsSent() {
        return zks.serverStats().getPacketsSent();
    }
    @Override
    public void resetLatency() {
        zks.serverStats().resetLatency();
    }
    @Override
    public void resetMaxLatency() {
        zks.serverStats().resetMaxLatency();
    }
    @Override
    public void resetStatistics() {
        ServerStats serverStats = zks.serverStats();
        serverStats.resetRequestCounters();
        serverStats.resetLatency();
    }
    @Override
    public long getNumAliveConnections() {
        return zks.getNumAliveConnections();
    }
}
