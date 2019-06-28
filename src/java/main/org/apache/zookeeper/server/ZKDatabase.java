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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.jute.BinaryOutputArchive;
import org.apache.jute.InputArchive;
import org.apache.jute.OutputArchive;
import org.apache.jute.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.server.DataTree.ProcessTxnResult;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog.PlayBackListener;
import org.apache.zookeeper.server.quorum.Leader;
import org.apache.zookeeper.server.quorum.Leader.Proposal;
import org.apache.zookeeper.server.quorum.QuorumPacket;
import org.apache.zookeeper.server.util.SerializeUtils;
import org.apache.zookeeper.txn.TxnHeader;

/**
 * This class maintains the in memory database of zookeeper
 * server states that includes the sessions, datatree and the
 * committed logs. It is booted up  after reading the logs
 * and snapshots from the disk.
 * ZookeeperDatabase 即Zookeeper数据库
 */
public class ZKDatabase {
    
    private static final Logger LOG = LoggerFactory.getLogger(ZKDatabase.class);
    
    /**
     * make sure on a clear you take care of 
     * all these members.
     * 数据DataTree
     */
    protected DataTree dataTree;
    /**
     * session超时事件
     */
    protected ConcurrentHashMap<Long, Integer> sessionsWithTimeouts;
    /**
     * 事务快照日志
     */
    protected FileTxnSnapLog snapLog;
    /**
     * 最小CommitLog，最大CommitLog
     */
    protected long minCommittedLog, maxCommittedLog;
    /**
     * commitlog
     */
    public static final int commitLogCount = 500;
    /**
     * commitLogBuffer
     */
    protected static int commitLogBuffer = 700;
    /**
     * 提议列表
     */
    protected LinkedList<Proposal> committedLog = new LinkedList<>();
    /**
     * 读写锁
     */
    protected ReentrantReadWriteLock logLock = new ReentrantReadWriteLock();
    /**
     * 是否初始化完成
     */
    volatile private boolean initialized = false;
    
    /**
     * the filetxnsnaplog that this zk database
     * maps to. There is a one to one relationship
     * between a filetxnsnaplog and zkdatabase.
     * @param snapLog the FileTxnSnapLog mapping this zkdatabase
     */
    public ZKDatabase(FileTxnSnapLog snapLog) {
        dataTree = new DataTree();
        sessionsWithTimeouts = new ConcurrentHashMap<Long, Integer>();
        this.snapLog = snapLog;
    }
    
    /**
     * checks to see if the zk database has been
     * initialized or not.
     * 是否初始化
     * @return true if zk database is initialized and false if not
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * clear the zkdatabase. 
     * Note to developers - be careful to see that 
     * the clear method does clear out all the
     * data structures in zkdatabase.
     * 清空
     */
    public void clear() {
        minCommittedLog = 0;
        maxCommittedLog = 0;
        /* to be safe we just create a new 
         * datatree.
         */
        dataTree = new DataTree();
        sessionsWithTimeouts.clear();
        WriteLock lock = logLock.writeLock();
        try {            
            lock.lock();
            committedLog.clear();
        } finally {
            lock.unlock();
        }
        initialized = false;
    }
    
    /**
     * the datatree for this zkdatabase
     * 获取DataTree
     * @return the datatree for this zkdatabase
     */
    public DataTree getDataTree() {
        return this.dataTree;
    }
 
    /**
     * the committed log for this zk database
     * 获取最大的CommitLog
     * @return the committed log for this zkdatabase
     */
    public long getmaxCommittedLog() {
        return maxCommittedLog;
    }
    
    
    /**
     * the minimum committedtransaction log
     * available in memory
     * @return the minimum committed transaction
     * 获取最小的commitlg
     * log available in memory
     */
    public long getminCommittedLog() {
        return minCommittedLog;
    }
    /**
     * Get the lock that controls the committedLog. If you want to get the pointer to the committedLog, you need
     * to use this lock to acquire a read lock before calling getCommittedLog()
     * @return the lock that controls the committed log
     * 获取读写锁
     */
    public ReentrantReadWriteLock getLogLock() {
        return logLock;
    }
    

    public synchronized LinkedList<Proposal> getCommittedLog() {
        ReadLock rl = logLock.readLock();
        // only make a copy if this thread isn't already holding a lock
        //如果当前共享资源没有被加锁
        if(logLock.getReadHoldCount() <=0) {
            try {
                rl.lock();
                return new LinkedList<>(this.committedLog);
            } finally {
                rl.unlock();
            }
        } 
        return this.committedLog;
    }      
    
    /**
     * get the last processed zxid from a datatree
     * 获取最后一个提交的processZxid
     * @return the last processed zxid of a datatree
     */
    public long getDataTreeLastProcessedZxid() {
        return dataTree.lastProcessedZxid;
    }
    
    /**
     * set the datatree initialized or not
     * 设置DataTree的初始化状态
     * @param b set the datatree initialized to b
     */
    public void setDataTreeInit(boolean b) {
        dataTree.initialized = b;
    }
    
    /**
     * return the sessions in the datatree
     * 获取DataTree的Session列表
     * @return the data tree sessions
     */
    public Collection<Long> getSessions() {
        return dataTree.getSessions();
    }
    
    /**
     * get sessions with timeouts
     * 获取TimeOut的Session列表
     * @return the hashmap of sessions with timeouts
     */
    public ConcurrentHashMap<Long, Integer> getSessionWithTimeOuts() {
        return sessionsWithTimeouts;
    }

    
    /**
     * load the database from the disk onto memory and also add 
     * the transactions to the committedlog in memory.
     * @return the last valid zxid on disk
     * 加载数据库到内存
     * @throws IOException IO异常
     */
    public long loadDataBase() throws IOException {
        PlayBackListener listener=new PlayBackListener(){
            @Override
            public void onTxnLoaded(TxnHeader hdr,Record txn){
                Request r = new Request(null, 0, hdr.getCxid(),hdr.getType(),null, null);
                r.txn = txn;
                r.hdr = hdr;
                r.zxid = hdr.getZxid();
                addCommittedProposal(r);
            }
        };
        
        long zxid = snapLog.restore(dataTree,sessionsWithTimeouts,listener);
        initialized = true;
        return zxid;
    }
    
    /**
     * maintains a list of last <i>committedLog</i>
     *  or so committed requests. This is used for
     * fast follower synchronization.
     * 提交一个事务
     * @param request committed request
     */
    public void addCommittedProposal(Request request) {
        WriteLock wl = logLock.writeLock();
        try {
            wl.lock();
            if (committedLog.size() > commitLogCount) {
                committedLog.removeFirst();
                minCommittedLog = committedLog.getFirst().packet.getZxid();
            }
            if (committedLog.size() == 0) {
                minCommittedLog = request.zxid;
                maxCommittedLog = request.zxid;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BinaryOutputArchive boa = BinaryOutputArchive.getArchive(baos);
            try {
                request.hdr.serialize(boa, "hdr");
                if (request.txn != null) {
                    request.txn.serialize(boa, "txn");
                }
                baos.close();
            } catch (IOException e) {
                LOG.error("This really should be impossible", e);
            }
            //leader发起的协议
            QuorumPacket pp = new QuorumPacket(Leader.PROPOSAL, request.zxid, baos.toByteArray(), null);
            Proposal p = new Proposal();
            p.packet = pp;
            p.request = request;
            committedLog.add(p);
            maxCommittedLog = p.packet.getZxid();
        } finally {
            wl.unlock();
        }
    }

    
    /**
     * remove a cnxn from the datatree
     * 从dataTree中remove一个watcher,连接实现了Watcher接口
     * @param cnxn the cnxn to remove from the datatree
     */
    public void removeCnxn(ServerCnxn cnxn) {
        dataTree.removeCnxn(cnxn);
    }

    /**
     * kill a given session in the datatree
     * 从DataTree中review掉DataSession
     * @param sessionId the session id to be killed
     * @param zxid the zxid of kill session transaction
     */
    public void killSession(long sessionId, long zxid) {
        dataTree.killSession(sessionId, zxid);
    }

    /**
     * write a text dump of all the ephemerals in the datatree
     * @param pwriter the output to write to
     */
    public void dumpEphemerals(PrintWriter pwriter) {
        dataTree.dumpEphemerals(pwriter);
    }

    /**
     * the node count of the datatree
     * @return the node count of datatree
     */
    public int getNodeCount() {
        return dataTree.getNodeCount();
    }

    /**
     * the paths for  ephemeral session id 
     * @param sessionId the session id for which paths match to 
     * @return the paths for a session id
     */
    public HashSet<String> getEphemerals(long sessionId) {
        return dataTree.getEphemerals(sessionId);
    }

    /**
     * the last processed zxid in the datatree
     * @param zxid the last processed zxid in the datatree
     */
    public void setlastProcessedZxid(long zxid) {
        dataTree.lastProcessedZxid = zxid;
    }

    /**
     * the process txn on the data
     * @param hdr the txnheader for the txn
     * @param txn the transaction that needs to be processed
     * @return the result of processing the transaction on this
     * datatree/zkdatabase
     * 提交事务
     */
    public ProcessTxnResult processTxn(TxnHeader hdr, Record txn) {
        return dataTree.processTxn(hdr, txn);
    }

    /**
     * stat the path 
     * @param path the path for which stat is to be done
     * @param serverCnxn the servercnxn attached to this request
     * @return the stat of this node
     * @throws KeeperException.NoNodeException
     */
    public Stat statNode(String path, ServerCnxn serverCnxn) throws KeeperException.NoNodeException {
        return dataTree.statNode(path, serverCnxn);
    }
    
    /**
     * get the datanode for this path
     * @param path the path to lookup
     * @return the datanode for getting the path
     */
    public DataNode getNode(String path) {
      return dataTree.getNode(path);
    }

    /**
     * convert from long to the acl entry
     * @param aclL the long for which to get the acl
     * @return the acl corresponding to this long entry
     */
    public List<ACL> convertLong(Long aclL) {
        return dataTree.convertLong(aclL);
    }

    /**
     * get data and stat for a path 
     * @param path the path being queried
     * @param stat the stat for this path
     * @param watcher the watcher function
     * @return
     * @throws KeeperException.NoNodeException
     */
    public byte[] getData(String path, Stat stat, Watcher watcher) throws KeeperException.NoNodeException {
        return dataTree.getData(path, stat, watcher);
    }

    /**
     * set watches on the datatree
     * @param relativeZxid the relative zxid that client has seen
     * @param dataWatches the data watches the client wants to reset
     * @param existWatches the exists watches the client wants to reset
     * @param childWatches the child watches the client wants to reset
     * @param watcher the watcher function
     */
    public void setWatches(long relativeZxid, List<String> dataWatches, List<String> existWatches, List<String> childWatches, Watcher watcher) {
        dataTree.setWatches(relativeZxid, dataWatches, existWatches, childWatches, watcher);
    }
    
    /**
     * get acl for a path
     * @param path the path to query for acl
     * @param stat the stat for the node
     * @return the acl list for this path
     * @throws NoNodeException
     */
    public List<ACL> getACL(String path, Stat stat) throws NoNodeException {
        return dataTree.getACL(path, stat);
    }

    /**
     * get children list for this path
     * @param path the path of the node
     * @param stat the stat of the node
     * @param watcher the watcher function for this path
     * @return the list of children for this path
     * @throws KeeperException.NoNodeException
     */
    public List<String> getChildren(String path, Stat stat, Watcher watcher)
    throws KeeperException.NoNodeException {
        return dataTree.getChildren(path, stat, watcher);
    }

    /**
     * check if the path is special or not
     * @param path the input path
     * @return true if path is special and false if not
     */
    public boolean isSpecialPath(String path) {
        return dataTree.isSpecialPath(path);
    }

    /**
     * get the acl size of the datatree
     * @return the acl size of the datatree
     */
    public int getAclSize() {
        return dataTree.longKeyMap.size();
    }

    /**
     * Truncate the ZKDatabase to the specified zxid
     * @param zxid the zxid to truncate zk database to
     * @return true if the truncate is successful and false if not
     * @throws IOException
     */
    public boolean truncateLog(long zxid) throws IOException {
        clear();

        // truncate the log
        boolean truncated = snapLog.truncateLog(zxid);

        if (!truncated) {
            return false;
        }

        loadDataBase();
        return true;
    }
    
    /**
     * deserialize a snapshot from an input archive
     * 从快照任文件恢复ZKDataBase
     * @param ia the input archive you want to deserialize from
     * @throws IOException ;
     */
    public void deserializeSnapshot(InputArchive ia) throws IOException {
        clear();
        SerializeUtils.deserializeSnapshot(getDataTree(),ia,getSessionWithTimeOuts());
        initialized = true;
    }   
    
    /**
     * serialize the snapshot
     * 把DataTree恢复到快照文件
     * @param oa the output archive to which the snapshot needs to be serialized
     * @throws IOException ;
     * @throws InterruptedException ;
     */
    public void serializeSnapshot(OutputArchive oa) throws IOException, InterruptedException {
        SerializeUtils.serializeSnapshot(getDataTree(), oa, getSessionWithTimeOuts());
    }

    /**
     * append to the underlying transaction log
     * 提交一个事务，在事务文件中
     * @param si the request to append
     * @return true if the append was succesfull and false if not
     */
    public boolean append(Request si) throws IOException {
        return this.snapLog.append(si);
    }

    /**
     * roll the underlying log
     */
    public void rollLog() throws IOException {
        this.snapLog.rollLog();
    }

    /**
     * commit to the underlying transaction log
     * @throws IOException
     */
    public void commit() throws IOException {
        this.snapLog.commit();
    }
    
    /**
     * close this database. free the resources
     * @throws IOException
     */
    public void close() throws IOException {
        this.snapLog.close();
    }
    
}
