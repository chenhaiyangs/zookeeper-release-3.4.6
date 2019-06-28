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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认的是使用NIOServerCnxnFactory
 * @author ;
 */
public class NIOServerCnxnFactory extends ServerCnxnFactory implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(NIOServerCnxnFactory.class);

    static {
        /*
         * 注册了一个程序线程崩溃检测，如果程序崩溃，则打印日志
         */
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    LOG.error("Thread " + t + " died", e);
                }
            });
        /*
         * this is to avoid the jvm bug:
         * NullPointerException in Selector.open()
         * http://bugs.sun.com/view_bug.do?bug_id=6427854
         */
        try {
            Selector.open().close();
        } catch(IOException ie) {
            LOG.error("Selector failed to open", ie);
        }
    }

    /**
     * 处理ServerSocketChannel
     */
    ServerSocketChannel ss;

    /**
     * NIO Selector; 处理Accept事件的selector
     */
    final Selector selector = Selector.open();

    /**
     * We use this buffer to do efficient socket I/O. Since there is a single
     * sender thread per NIOServerCnxn instance, we can use a member variable to
     * only allocate it once.
     * 直接内存
     *
    */
    final ByteBuffer directBuffer = ByteBuffer.allocateDirect(64 * 1024);
    /**
     * 每个ip地址对应的连接库
     */
    final HashMap<InetAddress, Set<NIOServerCnxn>> ipMap = new HashMap<InetAddress, Set<NIOServerCnxn>>( );
    /**
     * 这个配置参数将限制连接到ZooKeeper的客户端的数量，限制并发连接的数量，它通过IP来区分不同的客户端。
     * 此配置选项可以用来阻止某些类别的Dos攻击。该参数默认是60，将它设置为0将会取消对并发连接的限制。
     */
    int maxClientCnxns = 60;

    /**
     * Construct a new server connection factory which will accept an unlimited number
     * of concurrent connections from each client (up to the file descriptor
     * limits of the operating system). startup(zks) must be called subsequently.
     * @throws IOException
     */
    public NIOServerCnxnFactory() throws IOException {
    }

    /**
     * 线程-守护线程
     */
    Thread thread;
    @Override
    public void configure(InetSocketAddress addr, int maxcc) throws IOException {
        configureSaslLogin();

        thread = new Thread(this, "NIOServerCxn.Factory:" + addr);
        thread.setDaemon(true);
        maxClientCnxns = maxcc;

        //启动NIO服务器，非阻塞模式
        this.ss = ServerSocketChannel.open();
        /*
         * 为了确保一个进程关闭了ServerSocket后，
         * 即使操作系统还没释放端口，同一个主机上的其他进程还可以立刻重用该端口，
         * 可以调用ServerSocket的setResuseAddress(true)方法：
         */
        ss.socket().setReuseAddress(true);
        LOG.info("binding to port " + addr);
        ss.socket().bind(addr);
        ss.configureBlocking(false);

        ss.register(selector, SelectionKey.OP_ACCEPT);
    }


    /**
     *
     * 客户端最大连接数
     * {@inheritDoc}
     */
    @Override
    public int getMaxClientCnxnsPerHost() {
        return maxClientCnxns;
    }

    /**
     *
     * 客户端最大连接数
     * {@inheritDoc}
     */
    @Override
    public void setMaxClientCnxnsPerHost(int max) {
        maxClientCnxns = max;
    }

    /**
     * 让NIOServerCnxnFactory运行起来的方式
     */
    @Override
    public void start() {
        // ensure thread is started once and only once
        if (thread.getState() == Thread.State.NEW) {
            thread.start();
        }
    }

    /**
     * 启动ZookeeperServer
     * @param zks zks
     * @throws IOException ;
     * @throws InterruptedException ;
     */
    @Override
    public void startup(ZooKeeperServer zks) throws IOException,
            InterruptedException {
        start();
        zks.startdata();
        zks.startup();
        setZooKeeperServer(zks);
    }


    @Override
    public InetSocketAddress getLocalAddress(){
        return (InetSocketAddress)ss.socket().getLocalSocketAddress();
    }

    @Override
    public int getLocalPort(){
        return ss.socket().getLocalPort();
    }

    /**
     * 添加一条新过来的连接
     * @param cnxn cnxn
     */
    private void addCnxn(NIOServerCnxn cnxn) {
        synchronized (cnxns) {
            cnxns.add(cnxn);
            synchronized (ipMap){
                InetAddress addr = cnxn.sock.socket().getInetAddress();
                Set<NIOServerCnxn> s = ipMap.get(addr);
                if (s == null) {
                    // in general we will see 1 connection from each
                    // host, setting the initial cap to 2 allows us
                    // to minimize mem usage in the common case
                    // of 1 entry --  we need to set the initial cap
                    // to 2 to avoid rehash when the first entry is added
                    s = new HashSet<NIOServerCnxn>(2);
                    s.add(cnxn);
                    ipMap.put(addr,s);
                } else {
                    s.add(cnxn);
                }
            }
        }
    }

    /**
     * 创建一个NIO连接
     * @param sock sock
     * @param sk sk
     * @return ;
     * @throws IOException
     */
    protected NIOServerCnxn createConnection(SocketChannel sock, SelectionKey sk) throws IOException {
        return new NIOServerCnxn(zkServer, sock, sk, this);
    }

    /**
     * 获取客户端的连接数
     * @param cl 客户端
     * @return ;
     */
    private int getClientCnxnCount(InetAddress cl) {
        // The ipMap lock covers both the map, and its contents
        // (that is, the cnxn sets shouldn't be modified outside of
        // this lock)
        synchronized (ipMap) {
            Set<NIOServerCnxn> s = ipMap.get(cl);
            if (s == null) {
                return 0;
            }
            return s.size();
        }
    }

    /**
     * 启动NIO服务的代码
     */
    @Override
    public void run() {
        while (!ss.socket().isClosed()) {
            try {
                selector.select(1000);
                Set<SelectionKey> selected;
                synchronized (this) {
                    selected = selector.selectedKeys();
                }
                ArrayList<SelectionKey> selectedList = new ArrayList<SelectionKey>(selected);
                //将连接乱序
                Collections.shuffle(selectedList);
                for (SelectionKey k : selectedList) {
                    if ((k.readyOps() & SelectionKey.OP_ACCEPT) != 0) {
                        SocketChannel sc = ((ServerSocketChannel) k.channel()).accept();
                        InetAddress ia = sc.socket().getInetAddress();
                        int cnxncount = getClientCnxnCount(ia);
                        //连接数检查
                        if (maxClientCnxns > 0 && cnxncount >= maxClientCnxns){
                            LOG.warn("Too many connections from " + ia + " - max is " + maxClientCnxns );
                            sc.close();
                        } else {
                            LOG.info("Accepted socket connection from " + sc.socket().getRemoteSocketAddress());
                            sc.configureBlocking(false);
                            //注册读事件，这次，accept和read都注册到了一个selector上了
                            SelectionKey sk = sc.register(selector, SelectionKey.OP_READ);
                            NIOServerCnxn cnxn = createConnection(sc, sk);
                            //selectionKey附加对象
                            sk.attach(cnxn);
                            addCnxn(cnxn);
                        }
                    } else if ((k.readyOps() & (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) != 0) {
                        //获取NIO连接
                        NIOServerCnxn c = (NIOServerCnxn) k.attachment();
                        //执行真正的业务逻辑操作
                        c.doIO(k);
                    } else {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Unexpected ops in select " + k.readyOps());
                        }
                    }
                }
                selected.clear();
            } catch (RuntimeException e) {
                LOG.warn("Ignoring unexpected runtime exception", e);
            } catch (Exception e) {
                LOG.warn("Ignoring exception", e);
            }
        }
        closeAll();
        LOG.info("NIOServerCnxn factory exited run method");
    }

    /**
     * clear all the connections in the selector
     * 关闭连接closeAll
     */
    @Override
    @SuppressWarnings("unchecked")
    synchronized public void closeAll() {
        selector.wakeup();
        HashSet<NIOServerCnxn> cnxns;
        synchronized (this.cnxns) {
            cnxns = (HashSet<NIOServerCnxn>)this.cnxns.clone();
        }
        // got to clear all the connections that we have in the selector
        for (NIOServerCnxn cnxn: cnxns) {
            try {
                // don't hold this.cnxns lock as deadlock may occur
                cnxn.close();
            } catch (Exception e) {
                LOG.warn("Ignoring exception closing cnxn sessionid 0x" + Long.toHexString(cnxn.sessionId), e);
            }
        }
    }

    @Override
    public void shutdown() {
        try {
            ss.close();
            closeAll();
            thread.interrupt();
            thread.join();
            if (login != null) {
                login.shutdown();
            }
        } catch (InterruptedException e) {
            LOG.warn("Ignoring interrupted exception during shutdown", e);
        } catch (Exception e) {
            LOG.warn("Ignoring unexpected exception during shutdown", e);
        }
        try {
            selector.close();
        } catch (IOException e) {
            LOG.warn("Selector closing", e);
        }
        if (zkServer != null) {
            zkServer.shutdown();
        }
    }

    @Override
    public synchronized void closeSession(long sessionId) {
        //唤醒阻塞在selector上的线程
        selector.wakeup();
        closeSessionWithoutWakeup(sessionId);
    }

    /**
     * 根据sessionId关闭连接
     * @param sessionId sessionId
     */
    @SuppressWarnings("unchecked")
    private void closeSessionWithoutWakeup(long sessionId) {
        HashSet<NIOServerCnxn> cnxns;
        synchronized (this.cnxns) {
            cnxns = (HashSet<NIOServerCnxn>)this.cnxns.clone();
        }

        for (NIOServerCnxn cnxn : cnxns) {
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
    public void join() throws InterruptedException {
        thread.join();
    }

    @Override
    public Iterable<ServerCnxn> getConnections() {
        return cnxns;
    }
}
