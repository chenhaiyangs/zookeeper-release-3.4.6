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

import java.io.PrintWriter;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.SessionExpiredException;
import org.apache.zookeeper.KeeperException.SessionMovedException;

/**
 * This is the basic interface that ZooKeeperServer uses to track sessions. The
 * standalone and leader ZooKeeperServer use the same SessionTracker. The
 * FollowerZooKeeperServer uses a SessionTracker which is basically a simple
 * shell to track information to be forwarded to the leader.
 * Zookeeper的Session会话管理器
 */
public interface SessionTracker {

    /**
     * zookeeper中的一个Session的抽象接口
     */
    interface Session {
        /**
         * 获取SessionId
         * @return ;
         */
        long getSessionId();

        /**
         * 超时时间
         * @return ;
         */
        int getTimeout();

        /**
         * 是否已经关闭
         * @return ;
         */
        boolean isClosing();
    }

    /**
     * Session超时控制器
     */
    interface SessionExpirer {
        /**
         * expire session
         * @param session ;
         */
        void expire(Session session);

        /**
         * 获取serverid
         * @return ;
         */
        long getServerId();
    }

    /**
     * 创建一个Session
     * @param sessionTimeout 超时时间
     * @return sessionId
     */
    long createSession(int sessionTimeout);

    void addSession(long id, int to);

    /**
     * 看看Session还好不好
     * @param sessionId ;
     * @param sessionTimeout;
     * @return false if session is no longer active
     */
    boolean touchSession(long sessionId, int sessionTimeout);

    /**
     * 设置Session为关闭状态
     * Mark that the session is in the process of closing.
     * @param sessionId ；
     *
     */
    void setSessionClosing(long sessionId);

    /**
     * 关闭Session
     */
    void shutdown();

    /**
     * 移除Session
     * @param sessionId ;
     */
    void removeSession(long sessionId);

    /**
     * 检查Session
     * @param sessionId sessionId
     * @param owner 拥有者
     * @throws KeeperException.SessionExpiredException ;
     * @throws SessionMovedException ;
     */
    void checkSession(long sessionId, Object owner) throws KeeperException.SessionExpiredException, SessionMovedException;

    /**
     * 设置session的拥有者
     * @param id sessionid
     * @param owner owner;
     * @throws SessionExpiredException ;
     */
    void setOwner(long id, Object owner) throws SessionExpiredException;

    /**
     * DumpSession
     * Text dump of session information, suitable for debugging.
     * @param pwriter the output writer
     */
    void dumpSessions(PrintWriter pwriter);
}
