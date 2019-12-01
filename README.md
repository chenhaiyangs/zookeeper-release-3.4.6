# zookeeper源码阅读记录->添加中文注释


### zookeeper是什么？
ZooKeeper 是一个开放源码的分布式协调服务，它是集群的管理者，监视着集群中各个节点的状态根据节点提交的反馈进行下一步合理操作。<br/>
最终，将简单易用的接口和性能高效、功能稳定的系统提供给用户。<br/>

zookeeper是在内存中以树的形式存储数据并设计了一套Watcher机制允许客户端订阅各种Watcher。<br/>

分布式应用程序可以基于 Zookeeper 实现诸如数据发布/订阅、负载均衡、命名服务、分布式协调/通知、集群管理、Master 选举、分布式锁和分布式队列等功能。<br/>

### zookeeper特性

写操作支持顺序一致性。<br/>
读操作也不支持强一致性。只能支持最终一致性（主要是因为zookeeper的过半提交机制）<br/>
单一视图 <br/>
事务原子性 <br/>

### zookeeper的IO模型

在3.4.6版本中，Zookeeper提供两种IO模型。<br/>

#### 基于NIO的单线程模型

在NIOServerCnxnFactory中实现的单线程模型，在一个线程了完成select操作以及IO读写。<br/>

#### 基于Netty实现的线程模型

实现类是NettyServerCnxnFactory <br/>

### zookeeper的通讯协议
基于org.apache.jute协议进行网络收发包。<br/>
也是利用jute协议实现了定期存储快照文件和日志文件的文件流写入。<br/>

### zookeeper的读写性能

在集群模型下，
读是直接在请求节点读。<br/>
写是将请求提交给leader进行写。leader实行两阶段提交的方式。因此如果在集群规模较大时，写性能不佳。<br/>

### zookeeper的日志文件和快照文件

zookeeper每一次请求都会追加事务日志到txnlog,当txnlog到了一个大小就会生成新的txnlog,此时也会存储全量内容到快照文件。snaplog。<br/>
zookeeper在启动时会加载最近的100个快照日志，并应用最新的一个，如果校验不通过就下一个，直到找到一个可用的，然后在应用比快照日志zxid往后的事务日志，恢复内存数据。<br/>
所以，事务日志和快照日志应该要定期清理。只留下能保留当前完整数据的最新快照和快照之后的事务日志。<br/>

### zookeeper的节点角色

#### leader
leader节点负责事务请求的应用。并将事务广播到所有节点。<br/>
#### follower
follower是从节点，非事务请求自己处理，事务请求提交给leader，再由leader提交给集群中的所有follower和observer;<br/>
#### observer
observer节点是不参与投票的。为了缓解集群容量过大写性能下降的问题，引入observer节点来承担部分读的流量。<br/>

### zookeeper集群选举zab协议

zookeeper集群中存在两种状态：崩溃恢复模型和原子广播状态。<br/>
崩溃恢复状态的系统行为，见img文件夹的图。这时zk集群不对外提供服务<br/>
在广播模式ZooKeeper Server会接受Client请求，所有的写请求都被转发给领导者，再由领导者将更新广播给跟随者。<br/>
当半数以上的跟随者已经将修改持久化之后，领导者才会提交这个更新，然后客户端才会收到一个更新成功的响应。<br/>
这个用来达成共识的协议被设计成具有原子性，因此每个修改要么成功要么失败。<br/>

### zookeeper是如何保证事务顺序一致性的

ZooKeeper 采用了递增的事务 id 来识别，所有的 proposal（提议）都在被提出的时候加上了 zxid 。zxid 实际上是一个 64 位数字。<br/>

高 32 位是 epoch 用来标识 Leader 是否发生了改变，如果有新的 Leader 产生出来，epoch会自增。<br/>
低 32 位用来递增计数。
当新产生的 peoposal 的时候，会依据数据库的两阶段过程，首先会向其他的 Server 发出事务执行请求，如果超过半数的机器都能执行并且能够成功，那么就会开始执行。<br/>

### zookeeper的思维图

##### 选举逻辑视图
 ![选举逻辑视图](https://github.com/chenhaiyangs/zookeeper-release-3.4.6/blob/master/img/选举逻辑视图.png)
##### 选举时序图
![选举时序图](https://github.com/chenhaiyangs/zookeeper-release-3.4.6/blob/master/img/clipboard.png)
##### zookeeper全景图
![zookeeper全景图](https://github.com/chenhaiyangs/zookeeper-release-3.4.6/blob/master/img/zookeeper-all.jpg)
