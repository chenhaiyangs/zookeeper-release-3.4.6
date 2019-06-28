/**
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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.jute.Record;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.proto.ReplyHeader;
import org.apache.zookeeper.proto.RequestHeader;

/**
 * Interface to a Server connection - represents a connection from a client
 * to the server.
 * 所有Server的抽象基类
 * @author ;
 */
public abstract class ServerCnxn implements Stats, Watcher {
    // This is just an arbitrary object to represent requests issued by
    // (aka owned by) this class
    final public static Object me = new Object();
    /**
     * 权限相关
     */
    protected ArrayList<Id> authInfo = new ArrayList<>();

    /**
     * If the client is of old version, we don't send r-o mode info to it.
     * The reason is that if we would, old C client doesn't read it, which
     * results in TCP RST packet, i.e. "connection reset by peer".
     */
    boolean isOldClient = true;

    abstract int getSessionTimeout();

    abstract void close();

    public abstract void sendResponse(ReplyHeader h, Record r, String tag) throws IOException;

    /**
     * notify the client the session is closing and close/cleanup socket
     * 通知客户端session关闭了，然后客户端应该关闭连接
     */
    abstract void sendCloseSession();

    /**
     * 事件处理
     * @param event event
     */
    @Override
    public abstract void process(WatchedEvent event);

    /**
     * session相关
     * @return ;
     */
    abstract long getSessionId();
    abstract void setSessionId(long sessionId);

    /**
     * auth info for the cnxn, returns an unmodifyable list
     */
    public List<Id> getAuthInfo() {
        return Collections.unmodifiableList(authInfo);
    }

    /**
     * 添加权限
     * @param id
     */
    public void addAuthInfo(Id id) {
        if (authInfo.contains(id) == false) {
            authInfo.add(id);
        }
    }

    public boolean removeAuthInfo(Id id) {
        return authInfo.remove(id);
    }

    /**
     * 发送响应给客户端
     * @param closeConn closeConn
     */
    abstract void sendBuffer(ByteBuffer closeConn);

    /**
     * 开启接收
     */
    abstract void enableRecv();

    /**
     * 关闭接收
     */
    abstract void disableRecv();

    /**
     * 设置 sessionTimeout的超时时间
     * @param sessionTimeout ;
     */
    abstract void setSessionTimeout(int sessionTimeout);

    protected ZooKeeperSaslServer zooKeeperSaslServer = null;

    protected static class CloseRequestException extends IOException {
        private static final long serialVersionUID = -7854505709816442681L;
        public CloseRequestException(String msg) {
            super(msg);
        }
    }
    protected static class EndOfStreamException extends IOException {
        private static final long serialVersionUID = -8255690282104294178L;
        public EndOfStreamException(String msg) {
            super(msg);
        }
        @Override
        public String toString() {
            return "EndOfStreamException: " + getMessage();
        }
    }

    /**
     * ==============================
     * 下面是一堆命令
     */
    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int confCmd = ByteBuffer.wrap("conf".getBytes()).getInt();

    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int consCmd = ByteBuffer.wrap("cons".getBytes()).getInt();

    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int crstCmd = ByteBuffer.wrap("crst".getBytes()).getInt();

    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int dumpCmd = ByteBuffer.wrap("dump".getBytes()).getInt();

    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int enviCmd = ByteBuffer.wrap("envi".getBytes()).getInt();

    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int getTraceMaskCmd = ByteBuffer.wrap("gtmk".getBytes()).getInt();

    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int ruokCmd = ByteBuffer.wrap("ruok".getBytes()).getInt();
    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int setTraceMaskCmd = ByteBuffer.wrap("stmk".getBytes()).getInt();

    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int srvrCmd = ByteBuffer.wrap("srvr".getBytes()).getInt();

    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int srstCmd = ByteBuffer.wrap("srst".getBytes()).getInt();

    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int statCmd = ByteBuffer.wrap("stat".getBytes()).getInt();

    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int wchcCmd = ByteBuffer.wrap("wchc".getBytes()).getInt();

    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int wchpCmd = ByteBuffer.wrap("wchp".getBytes()).getInt();

    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int wchsCmd = ByteBuffer.wrap("wchs".getBytes()).getInt();

    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int mntrCmd = ByteBuffer.wrap("mntr".getBytes()).getInt();

    /*
     * See <a href="{@docRoot}/../../../docs/zookeeperAdmin.html#sc_zkCommands">
     * Zk Admin</a>. this link is for all the commands.
     */
    protected final static int isroCmd = ByteBuffer.wrap("isro".getBytes()).getInt();

    protected final static HashMap<Integer, String> cmd2String = new HashMap<>();

    // specify all of the commands that are available
    static {
        cmd2String.put(confCmd, "conf");
        cmd2String.put(consCmd, "cons");
        cmd2String.put(crstCmd, "crst");
        cmd2String.put(dumpCmd, "dump");
        cmd2String.put(enviCmd, "envi");
        cmd2String.put(getTraceMaskCmd, "gtmk");
        cmd2String.put(ruokCmd, "ruok");
        cmd2String.put(setTraceMaskCmd, "stmk");
        cmd2String.put(srstCmd, "srst");
        cmd2String.put(srvrCmd, "srvr");
        cmd2String.put(statCmd, "stat");
        cmd2String.put(wchcCmd, "wchc");
        cmd2String.put(wchpCmd, "wchp");
        cmd2String.put(wchsCmd, "wchs");
        cmd2String.put(mntrCmd, "mntr");
        cmd2String.put(isroCmd, "isro");
    }

    /**
     * 接收包
     */
    protected void packetReceived() {
        incrPacketsReceived();
        ServerStats serverStats = serverStats();
        if (serverStats != null) {
            serverStats().incrementPacketsReceived();
        }
    }

    /**
     * 发送包
     */
    protected void packetSent() {
        incrPacketsSent();
        ServerStats serverStats = serverStats();
        if (serverStats != null) {
            serverStats().incrementPacketsSent();
        }
    }

    /**
     * 由具体的子类提供ServerStats,这个是全局的
     * @return ;
     */
    protected abstract ServerStats serverStats();

    /**
     * 连接创建时间
     */
    protected final Date established = new Date();

    protected final AtomicLong packetsReceived = new AtomicLong();
    protected final AtomicLong packetsSent = new AtomicLong();
    protected long minLatency;
    protected long maxLatency;
    protected String lastOp;
    //客户端操作序列号，最后一个
    protected long lastCxid;
    //最后一个事务id
    protected long lastZxid;
    protected long lastResponseTime;
    protected long lastLatency;

    protected long count;
    protected long totalLatency;

    @Override
    public synchronized void resetStats() {
        packetsReceived.set(0);
        packetsSent.set(0);
        minLatency = Long.MAX_VALUE;
        maxLatency = 0;
        lastOp = "NA";
        lastCxid = -1;
        lastZxid = -1;
        lastResponseTime = 0;
        lastLatency = 0;

        count = 0;
        totalLatency = 0;
    }

    protected long incrPacketsReceived() {
        return packetsReceived.incrementAndGet();
    }
    
    protected void incrOutstandingRequests(RequestHeader h) { }

    protected long incrPacketsSent() {
        return packetsSent.incrementAndGet();
    }

    /**
     * 更新连接级别的事件
     * @param cxid 客户端cxid
     * @param zxid 事务id
     * @param op 操作
     * @param start 开始时间
     * @param end 结束时间 （这两个主要是算延迟的）
     */
    protected synchronized void updateStatsForResponse(long cxid, long zxid,
            String op, long start, long end) {
        // don't overwrite with "special" xids - we're interested
        // in the clients last real operation
        if (cxid >= 0) {

            lastCxid = cxid;
        }
        lastZxid = zxid;
        lastOp = op;
        lastResponseTime = end;
        long elapsed = end - start;
        lastLatency = elapsed;
        if (elapsed < minLatency) {
            minLatency = elapsed;
        }
        if (elapsed > maxLatency) {
            maxLatency = elapsed;
        }
        count++;
        totalLatency += elapsed;
    }
    @Override
    public Date getEstablished() {
        return (Date)established.clone();
    }
    @Override
    public abstract long getOutstandingRequests();
    @Override
    public long getPacketsReceived() {
        return packetsReceived.longValue();
    }

    @Override
    public long getPacketsSent() {
        return packetsSent.longValue();
    }
    @Override
    public synchronized long getMinLatency() {
        return minLatency == Long.MAX_VALUE ? 0 : minLatency;
    }
    @Override
    public synchronized long getAvgLatency() {
        return count == 0 ? 0 : totalLatency / count;
    }
    @Override
    public synchronized long getMaxLatency() {
        return maxLatency;
    }
    @Override
    public synchronized String getLastOperation() {
        return lastOp;
    }
    @Override
    public synchronized long getLastCxid() {
        return lastCxid;
    }
    @Override
    public synchronized long getLastZxid() {
        return lastZxid;
    }
    @Override
    public synchronized long getLastResponseTime() {
        return lastResponseTime;
    }
    @Override
    public synchronized long getLastLatency() {
        return lastLatency;
    }

    /**
     * Prints detailed stats information for the connection.
     *
     * @see #dumpConnectionInfo(PrintWriter, boolean) for brief stats
     */
    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter pwriter = new PrintWriter(sw);
        dumpConnectionInfo(pwriter, false);
        pwriter.flush();
        pwriter.close();
        return sw.toString();
    }

    /**
     * 远程连接地址
     * @return ;
     */
    public abstract InetSocketAddress getRemoteSocketAddress();

    /**
     * 获取感兴趣的操作
     * 在NIOServerCnxn中，是调用的 sk.interestOps()
     * @return ;
     */
    public abstract int getInterestOps();
    
    /**
     * Print information about the connection.
     * @param brief iff true prints brief details, otw full detail
     * @return information about this connection
     */
    protected synchronized void dumpConnectionInfo(PrintWriter pwriter, boolean brief) {
        pwriter.print(" ");
        pwriter.print(getRemoteSocketAddress());
        pwriter.print("[");
        int interestOps = getInterestOps();
        pwriter.print(interestOps == 0 ? "0" : Integer.toHexString(interestOps));
        pwriter.print("](queued=");
        pwriter.print(getOutstandingRequests());
        pwriter.print(",recved=");
        pwriter.print(getPacketsReceived());
        pwriter.print(",sent=");
        pwriter.print(getPacketsSent());

        if (!brief) {
            long sessionId = getSessionId();
            if (sessionId != 0) {
                pwriter.print(",sid=0x");
                pwriter.print(Long.toHexString(sessionId));
                pwriter.print(",lop=");
                pwriter.print(getLastOperation());
                pwriter.print(",est=");
                pwriter.print(getEstablished().getTime());
                pwriter.print(",to=");
                pwriter.print(getSessionTimeout());
                long lastCxid = getLastCxid();
                if (lastCxid >= 0) {
                    pwriter.print(",lcxid=0x");
                    pwriter.print(Long.toHexString(lastCxid));
                }
                pwriter.print(",lzxid=0x");
                pwriter.print(Long.toHexString(getLastZxid()));
                pwriter.print(",lresp=");
                pwriter.print(getLastResponseTime());
                pwriter.print(",llat=");
                pwriter.print(getLastLatency());
                pwriter.print(",minlat=");
                pwriter.print(getMinLatency());
                pwriter.print(",avglat=");
                pwriter.print(getAvgLatency());
                pwriter.print(",maxlat=");
                pwriter.print(getMaxLatency());
            }
        }
        pwriter.print(")");
    }

}
