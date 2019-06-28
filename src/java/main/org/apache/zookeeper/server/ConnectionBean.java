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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Date;

import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.zookeeper.jmx.MBeanRegistry;
import org.apache.zookeeper.jmx.ZKMBeanInfo;
/**
 * Implementation of connection MBean interface.
 * JMX相关内容
 */
public class ConnectionBean implements ConnectionMXBean, ZKMBeanInfo {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionBean.class);

    private final ServerCnxn connection;
    private final Stats stats;

    private final ZooKeeperServer zk;
    
    private final String remoteIP;
    private final long sessionId;

    public ConnectionBean(ServerCnxn connection,ZooKeeperServer zk){
        this.connection = connection;
        this.stats = connection;
        this.zk = zk;
        
        InetSocketAddress sockAddr = connection.getRemoteSocketAddress();
        if (sockAddr == null) {
            remoteIP = "Unknown";
        } else {
            InetAddress addr = sockAddr.getAddress();
            if (addr instanceof Inet6Address) {
                remoteIP = ObjectName.quote(addr.getHostAddress());
            } else {
                remoteIP = addr.getHostAddress();
            }
        }
        sessionId = connection.getSessionId();
    }

    @Override
    public String getSessionId() {
        return "0x" + Long.toHexString(sessionId);
    }

    @Override
    public String getSourceIP() {
        InetSocketAddress sockAddr = connection.getRemoteSocketAddress();
        if (sockAddr == null) {
            return null;
        }
        return sockAddr.getAddress().getHostAddress()
            + ":" + sockAddr.getPort();
    }

    @Override
    public String getName() {
        return MBeanRegistry.getInstance().makeFullPath("Connections", remoteIP,
                getSessionId());
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public String[] getEphemeralNodes() {
        if(zk.getZKDatabase()  !=null){
            String[] res = zk.getZKDatabase().getEphemerals(sessionId)
                .toArray(new String[0]);
            Arrays.sort(res);
            return res;
        }
        return null;
    }

    @Override
    public String getStartedTime() {
        return stats.getEstablished().toString();
    }

    @Override
    public void terminateSession() {
        try {
            zk.closeSession(sessionId);
        } catch (Exception e) {
            LOG.warn("Unable to closeSession() for session: 0x" 
                    + getSessionId(), e);
        }
    }

    @Override
    public void terminateConnection() {
        connection.sendCloseSession();
    }

    @Override
    public void resetCounters() {
        stats.resetStats();
    }

    @Override
    public String toString() {
        return "ConnectionBean{ClientIP=" + ObjectName.quote(getSourceIP())
            + ",SessionId=0x" + getSessionId() + "}";
    }

    @Override
    public long getOutstandingRequests() {
        return stats.getOutstandingRequests();
    }

    @Override
    public long getPacketsReceived() {
        return stats.getPacketsReceived();
    }

    @Override
    public long getPacketsSent() {
        return stats.getPacketsSent();
    }

    @Override
    public int getSessionTimeout() {
        return connection.getSessionTimeout();
    }

    @Override
    public long getMinLatency() {
        return stats.getMinLatency();
    }

    @Override
    public long getAvgLatency() {
        return stats.getAvgLatency();
    }

    @Override
    public long getMaxLatency() {
        return stats.getMaxLatency();
    }

    @Override
    public String getLastOperation() {
        return stats.getLastOperation();
    }

    @Override
    public String getLastCxid() {
        return "0x" + Long.toHexString(stats.getLastCxid());
    }

    @Override
    public String getLastZxid() {
        return "0x" + Long.toHexString(stats.getLastZxid());
    }

    @Override
    public String getLastResponseTime() {
        return new Date(stats.getLastResponseTime()).toString();
    }

    @Override
    public long getLastLatency() {
        return stats.getLastLatency();
    }
}
