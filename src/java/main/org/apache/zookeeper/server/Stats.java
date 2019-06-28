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

import java.util.Date;

/**
 * Statistics on the ServerCnxn
 * 在ServerCnxn 上使用的连接的状态Stats
 */
interface Stats {
    /** Date/time the connection was established
     * 获取连接建立的时间
     * @since 3.3.0 */
    Date getEstablished();
    /**
     * The number of requests that have been submitted but not yet
     * responded to.
     * 请求已经被提交，但是还没有被响应的个数
     */
    long getOutstandingRequests();
    /**
     * Number of packets received
     * 接收到的包的个数
     */
    long getPacketsReceived();
    /**
     * Number of packets sent (incl notifications)
     * 发出的包的个数
     */
    long getPacketsSent();
    /**
     * Min latency in ms
     * 最小的延迟
     * @since 3.3.0
     */
    long getMinLatency();
    /**
     * Average latency in ms
     * 平均延迟
     * @since 3.3.0
     */
    long getAvgLatency();
    /**
     * Max latency in ms
     * @since 3.3.0
     * 最大延迟
     */
    long getMaxLatency();
    /**
     * Last operation performed by this connection
     * 这个连接最后一次操作的内容
     * @since 3.3.0
     */
    String getLastOperation();
    /**
     * Last cxid of this connection
     * @since 3.3.0
     */
    long getLastCxid();
    /**
     * Last zxid of this connection
     * @since 3.3.0
     */
    long getLastZxid();
    /**
     * Last time server sent a response to client on this connection
     * 最后一次服务端给客户端响应的时间
     * @since 3.3.0
     */
    long getLastResponseTime();
    /**
     * Latency of last response to client on this connection in ms
     * 最后一次响应，延迟占比
     * @since 3.3.0
     */
    long getLastLatency();

    /**
     * Reset counters
     * @since 3.3.0
     * 重置状态
     */
    void resetStats();
}
