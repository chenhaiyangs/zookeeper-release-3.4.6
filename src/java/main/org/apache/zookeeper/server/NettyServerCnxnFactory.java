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

import static org.jboss.netty.buffer.ChannelBuffers.dynamicBuffer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

public class NettyServerCnxnFactory extends ServerCnxnFactory {
    Logger LOG = LoggerFactory.getLogger(NettyServerCnxnFactory.class);

    ServerBootstrap bootstrap;
    /**
     * 父channel
     */
    Channel parentChannel;

    ChannelGroup allChannels = new DefaultChannelGroup("zkServerCnxns");
    /**
     * 每一个对应的连接
     */
    HashMap<InetAddress, Set<NettyServerCnxn>> ipMap = new HashMap<>( );
    /**
     * netty客户端启动的最大的连接
     */
    InetSocketAddress localAddress;
    /**
     * 客户端最大连接数
     */
    int maxClientCnxns = 60;
    
    /**
     * This is an inner class since we need to extend SimpleChannelHandler, but
     * NettyServerCnxnFactory already extends ServerCnxnFactory. By making it inner
     * this class gets access to the member variables and methods.
     */
    @Sharable
    class CnxnChannelHandler extends SimpleChannelHandler {

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Channel closed " + e);
            }
            allChannels.remove(ctx.getChannel());
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Channel connected " + e);
            }
            allChannels.add(ctx.getChannel());
            NettyServerCnxn cnxn = new NettyServerCnxn(ctx.getChannel(), zkServer, NettyServerCnxnFactory.this);
            ctx.setAttachment(cnxn);
            addCnxn(cnxn);
        }

        @Override
        public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Channel disconnected " + e);
            }
            NettyServerCnxn cnxn = (NettyServerCnxn) ctx.getAttachment();
            if (cnxn != null) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Channel disconnect caused close " + e);
                }
                cnxn.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            LOG.warn("Exception caught " + e, e.getCause());
            NettyServerCnxn cnxn = (NettyServerCnxn) ctx.getAttachment();
            if (cnxn != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Closing " + cnxn);
                    cnxn.close();
                }
            }
        }

        /**
         * 处理逻辑
         * @param ctx ctx
         * @param e 消息事件
         * @throws Exception e
         */
        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            if (LOG.isTraceEnabled()) {
                LOG.trace("message received called " + e.getMessage());
            }
            try {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("New message " + e.toString()
                            + " from " + ctx.getChannel());
                }
                NettyServerCnxn cnxn = (NettyServerCnxn)ctx.getAttachment();
                synchronized(cnxn) {
                    processMessage(e, cnxn);
                }
            } catch(Exception ex) {
                LOG.error("Unexpected exception in receive", ex);
                throw ex;
            }
        }

        /**
         * 处理消息
         * @param e 事件类型
         * @param cnxn 连接
         */
        private void processMessage(MessageEvent e, NettyServerCnxn cnxn) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(Long.toHexString(cnxn.sessionId) + " queuedBuffer: " + cnxn.queuedBuffer);
            }

            //收到此内容就表示设置channel为可读
            if (e instanceof NettyServerCnxn.ResumeMessageEvent) {
                LOG.debug("Received ResumeMessageEvent");
                //如果queueBuffer 缓冲区Buffer不为null
                if (cnxn.queuedBuffer != null) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("processing queue "
                                + Long.toHexString(cnxn.sessionId)
                                + " queuedBuffer 0x"
                                + ChannelBuffers.hexDump(cnxn.queuedBuffer));
                    }
                    cnxn.receiveMessage(cnxn.queuedBuffer);
                    //不可读时清空queuedBuffer
                    if (!cnxn.queuedBuffer.readable()) {
                        LOG.debug("Processed queue - no bytes remaining");
                        cnxn.queuedBuffer = null;
                    } else {
                        LOG.debug("Processed queue - bytes remaining");
                    }
                } else {
                    LOG.debug("queue empty");
                }
                //设置其可读
                cnxn.channel.setReadable(true);
            } else {
                ChannelBuffer buf = (ChannelBuffer)e.getMessage();
                if (LOG.isTraceEnabled()) {
                    LOG.trace(Long.toHexString(cnxn.sessionId)
                            + " buf 0x"
                            + ChannelBuffers.hexDump(buf));
                }

                //节流模式
                if (cnxn.throttled) {
                    LOG.debug("Received message while throttled");
                    // we are throttled, so we need to queue
                    if (cnxn.queuedBuffer == null) {
                        LOG.debug("allocating queue");
                        //生成一个动态容量的Buffer
                        cnxn.queuedBuffer = dynamicBuffer(buf.readableBytes());
                    }
                    //写进buf
                    cnxn.queuedBuffer.writeBytes(buf);
                    LOG.debug(Long.toHexString(cnxn.sessionId)
                            + " queuedBuffer 0x"
                            + ChannelBuffers.hexDump(cnxn.queuedBuffer));
                //非节流模式
                } else {
                    LOG.debug("not throttled");
                    //如果queueBuffer不为空
                    if (cnxn.queuedBuffer != null) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace(Long.toHexString(cnxn.sessionId)
                                    + " queuedBuffer 0x"
                                    + ChannelBuffers.hexDump(cnxn.queuedBuffer));
                        }
                        //写进buf
                        cnxn.queuedBuffer.writeBytes(buf);
                        if (LOG.isTraceEnabled()) {
                            LOG.trace(Long.toHexString(cnxn.sessionId)
                                    + " queuedBuffer 0x"
                                    + ChannelBuffers.hexDump(cnxn.queuedBuffer));
                        }

                        //处理buffer
                        cnxn.receiveMessage(cnxn.queuedBuffer);
                        //不可读就清空queuedBuffer
                        if (!cnxn.queuedBuffer.readable()) {
                            LOG.debug("Processed queue - no bytes remaining");
                            cnxn.queuedBuffer = null;
                        } else {
                            LOG.debug("Processed queue - bytes remaining");
                        }
                        //如果queueBuffer为空
                    } else {
                        //直接处理
                        cnxn.receiveMessage(buf);
                        if (buf.readable()) {
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Before copy " + buf);
                            }
                            cnxn.queuedBuffer = dynamicBuffer(buf.readableBytes()); 
                            cnxn.queuedBuffer.writeBytes(buf);
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Copy is " + cnxn.queuedBuffer);
                                LOG.trace(Long.toHexString(cnxn.sessionId)
                                        + " queuedBuffer 0x"
                                        + ChannelBuffers.hexDump(cnxn.queuedBuffer));
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e) throws Exception {
            if (LOG.isTraceEnabled()) {
                LOG.trace("write complete " + e);
            }
        }
        
    }

    /**
     * Netty的channelHandler
     */
    CnxnChannelHandler channelHandler = new CnxnChannelHandler();

    /**
     * 启动NettyServerCnxnFactory
     */
    NettyServerCnxnFactory() {
        bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));
        // parent channel
        bootstrap.setOption("reuseAddress", true);
        // child channels
        bootstrap.setOption("child.tcpNoDelay", true);
        /* set socket linger to off, so that socket close does not block */
        bootstrap.setOption("child.soLinger", -1);

        bootstrap.getPipeline().addLast("servercnxnfactory", channelHandler);
    }


    /**
     * 关闭所有的连接
     * @author ;
     */
    @Override
    public void closeAll() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("closeAll()");
        }

        NettyServerCnxn[] allCnxns = null;
        synchronized (cnxns) {
            allCnxns = cnxns.toArray(new NettyServerCnxn[cnxns.size()]);
        }
        // got to clear all the connections that we have in the selector
        for (NettyServerCnxn cnxn : allCnxns) {
            try {
                cnxn.close();
            } catch (Exception e) {
                LOG.warn("Ignoring exception closing cnxn sessionid 0x"
                                + Long.toHexString(cnxn.getSessionId()), e);
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("allChannels size:" + allChannels.size() + " cnxns size:"
                    + allCnxns.length);
        }
    }

    /**
     * 关闭Session
     * @param sessionId sessionId
     */
    @Override
    public void closeSession(long sessionId) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("closeSession sessionid:0x" + sessionId);
        }
        NettyServerCnxn[] allCnxns = null;
        synchronized (cnxns) {
            allCnxns = cnxns.toArray(new NettyServerCnxn[cnxns.size()]);
        }
        for (NettyServerCnxn cnxn : allCnxns) {
            if (cnxn.getSessionId() == sessionId) {
                try {
                    cnxn.close();
                } catch (Exception e) {
                    LOG.warn("exception during session close", e);
                }
                break;
            }
        }
    }

    @Override
    public void configure(InetSocketAddress addr, int maxClientCnxns) throws IOException {
        configureSaslLogin();
        localAddress = addr;
        this.maxClientCnxns = maxClientCnxns;
    }

    /** {@inheritDoc} */
    @Override
    public int getMaxClientCnxnsPerHost() {
        return maxClientCnxns;
    }

    /** {@inheritDoc} */
    @Override
    public void setMaxClientCnxnsPerHost(int max) {
        maxClientCnxns = max;
    }

    @Override
    public int getLocalPort() {
        return localAddress.getPort();
    }

    boolean killed;
    @Override
    public void join() throws InterruptedException {
        synchronized(this) {
            while(!killed) {
                wait();
            }
        }
    }

    @Override
    public void shutdown() {
        LOG.info("shutdown called " + localAddress);
        if (login != null) {
            login.shutdown();
        }
        // null if factory never started
        if (parentChannel != null) {
            parentChannel.close().awaitUninterruptibly();
            closeAll();
            allChannels.close().awaitUninterruptibly();
            bootstrap.releaseExternalResources();
        }

        if (zkServer != null) {
            zkServer.shutdown();
        }
        synchronized(this) {
            killed = true;
            notifyAll();
        }
    }

    /**
     * 启动Netty的客户端
     */
    @Override
    public void start() {
        LOG.info("binding to port " + localAddress);
        parentChannel = bootstrap.bind(localAddress);
    }

    /**
     * 启动Netty的NIO服务
     * @param zks zookeeperServer
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public void startup(ZooKeeperServer zks) throws IOException,InterruptedException {
        start();
        zks.startdata();
        zks.startup();
        setZooKeeperServer(zks);
    }

    @Override
    public Iterable<ServerCnxn> getConnections() {
        return cnxns;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    private void addCnxn(NettyServerCnxn cnxn) {
        synchronized (cnxns) {
            cnxns.add(cnxn);
            synchronized (ipMap){
                InetAddress addr =
                    ((InetSocketAddress)cnxn.channel.getRemoteAddress())
                        .getAddress();
                Set<NettyServerCnxn> s = ipMap.get(addr);
                if (s == null) {
                    s = new HashSet<>();
                }
                s.add(cnxn);
                ipMap.put(addr,s);
            }
        }
    }

}
