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

/**
 * Basic Server Statistics
 * 服务器统计器
 */
public class ServerStats {
    /**
     * 发送包个数
     */
    private long packetsSent;
    /**
     * 接收个数
     */
    private long packetsReceived;
    /**
     * 最长延迟
     */
    private long maxLatency;
    /**
     * 最短延迟
     */
    private long minLatency = Long.MAX_VALUE;
    /**
     * 总延迟
     */
    private long totalLatency = 0;
    /**
     * 延迟次数
     */
    private long count = 0;

    private final Provider provider;

    /**
     * Provider接口，ZookeeperServer是其中的一个实现
     */
    public interface Provider {
        /**
         * //获取队列中还没有被处理的请求数量
         * @return ;
         */
        long getOutstandingRequests();

        /**
         * //获取最后一个处理的zxid
         * @return ;
         */
        long getLastProcessedZxid();

        /**
         * //获取服务器状态
         * @return ;
         */
        String getState();

        /**
         * 获取存活的客户端连接数量
         * @return ;
         */
        int getNumAliveConnections();
    }
    
    public ServerStats(Provider provider) {
        this.provider = provider;
    }

    /**
     * 获取最小的延迟
     * @return ;
     */
    synchronized public long getMinLatency() {
        return minLatency == Long.MAX_VALUE ? 0 : minLatency;
    }

    /**
     * 获取平均延迟
     * @return ;
     */
    synchronized public long getAvgLatency() {
        if (count != 0) {
            return totalLatency / count;
        }
        return 0;
    }

    synchronized public long getMaxLatency() {
        return maxLatency;
    }

    public long getOutstandingRequests() {
        return provider.getOutstandingRequests();
    }
    
    public long getLastProcessedZxid(){
        return provider.getLastProcessedZxid();
    }
    
    synchronized public long getPacketsReceived() {
        return packetsReceived;
    }

    synchronized public long getPacketsSent() {
        return packetsSent;
    }

    public String getServerState() {
        return provider.getState();
    }
    
    /** The number of client connections alive to this server */
    public int getNumAliveClientConnections() {
    	return provider.getNumAliveConnections();
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append("Latency min/avg/max: " + getMinLatency() + "/"
                + getAvgLatency() + "/" + getMaxLatency() + "\n");
        sb.append("Received: " + getPacketsReceived() + "\n");
        sb.append("Sent: " + getPacketsSent() + "\n");
        sb.append("Connections: " + getNumAliveClientConnections() + "\n");

        if (provider != null) {
            sb.append("Outstanding: " + getOutstandingRequests() + "\n");
            sb.append("Zxid: 0x"+ Long.toHexString(getLastProcessedZxid())+ "\n");
        }
        sb.append("Mode: " + getServerState() + "\n");
        return sb.toString();
    }


    /**
     * 下面是跟新延迟的一些函数
     * 当前时间减去请求创建的时间就是延迟
     * @param requestCreateTime ;
     */
    synchronized void updateLatency(long requestCreateTime) {
        long latency = System.currentTimeMillis() - requestCreateTime;
        totalLatency += latency;
        count++;
        if (latency < minLatency) {
            minLatency = latency;
        }
        if (latency > maxLatency) {
            maxLatency = latency;
        }
    }

    /**
     * 重置延迟相关
     */
    synchronized public void resetLatency(){
        totalLatency = 0;
        count = 0;
        maxLatency = 0;
        minLatency = Long.MAX_VALUE;
    }
    synchronized public void resetMaxLatency(){
        maxLatency = getMinLatency();
    }

    /**
     * 收包个数+1
     */
    synchronized public void incrementPacketsReceived() {
        packetsReceived++;
    }

    /**
     * 发包个数+1
     */
    synchronized public void incrementPacketsSent() {
        packetsSent++;
    }

    /**
     * 重置收发包计数器
     */
    synchronized public void resetRequestCounters(){
        packetsReceived = 0;
        packetsSent = 0;
    }
    /**
     * 重置两种：
     * 延迟和收发包计数
     */
    synchronized public void reset() {
        resetLatency();
        resetRequestCounters();
    }

}
