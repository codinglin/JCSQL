# JCSQL
使用JAVA去仿写MySQL基本功能：
* 数据的可靠性和数据恢复
* 两段锁协议（2PL）实现可串行化调度
* MVCC
* 两种事务隔离级别（读提交和可重复读）
* 死锁处理
* 简单的表和字段管理
* 简陋的 SQL 解析（因为懒得写词法分析和自动机，就弄得比较简陋）
* 基于 socket 的 server 和 client

## 后端划分为五个模块
分别如下：
1. Transaction Manager（TM）
2. Data Manager（DM）
3. Version Manager（VM）
4. Index Manager（IM）
5. Table Manager（TBM）

每个模块的职责如下：

1. TM 通过维护 XID 文件来维护事务的状态，并提供接口供其他模块来查询某个事务的状态。
2. DM 直接管理数据库 DB 文件和日志文件。DM 的主要职责有：1) 分页管理 DB 文件，并进行缓存；2) 管理日志文件，保证在发生错误时可以根据日志进行恢复；3) 抽象 DB 文件为 DataItem 供上层模块使用，并提供缓存。
3. VM 基于两段锁协议实现了调度序列的可串行化，并实现了 MVCC 以消除读写阻塞。同时实现了两种隔离级别。
4. IM 实现了基于 B+ 树的索引，BTW，目前 where 只支持已索引字段。
5. TBM 实现了对字段和表的管理。同时，解析 SQL 语句，并根据语句操作表。

## 各个模块提供的操作
1. DM：insert(x), update(x), read(x).
  DM 提供针对数据项(DataItem)的基本插入，更新与读取操作，且这些操作是原子性的。DM会直接对数据库文件进行读写。
2. TM：begin(T), commit(T), abort(T), isActive(T), isCommitted(T), isAborted(T)。
  TM 提供了针对事务的开始，提交，回滚操作，同时提供了对事务状态的查询操作。
3. VM：insert(X), update(X), read(X), delete(X)。
  VM 提供了针对记录(Entry)的增删改查操作，VM在内部为每条记录维护多个版本，并根据不同的事务，返回不同的版本。
  VM 对这些实现，是建立在 DM 和 TM 的各个操作上的，还有一个事务可见性类 Visibility。
4. TBM：execute(statement)
  TBM 就是非常高层的模块，能直接执行用户输入的语句(statement)，然后进行执行。
  TBM 对语句的执行时建立在 VM 和 IM 提供的各个操作上的。
5. IM：value search(key), insert(key, value)
  IM 提供了对索引的基本操作

## TM 解析：

TM 维护 XID 文件来维护事务的状态，并提供接口供其他模块来查询某个事务的状态。

事务的 XID 从 1 开始标号，自增且不可重复。**特殊规定 XID 为 0 是一个超级事务**

每个事务都有三种状态：

1. active 正在进行, 尚未结束
2. committed, 已提交
3. aborted, 已撤销（回滚）

```java
// XID文件头长度
public static final int LEN_XID_HEADER_LENGTH = 8;
// 每个事务的占用长度
private static final int XID_FIELD_SIZE = 1;
```

> XID 文件的头部，保存一个 8 字节的数字，记录这个 XID 文件管理的事务的个数
>
> 事务 xid 在文件中的状态就存储在 (xid - 1) + 8 字节处，xid - 1 是因为 xid 为 0 的超级事务的状态不需要记录，永远为 committed 状态

## DM 解析：

1. AbstractCache：引用计数法的缓存框架，留了两个从数据源获取数据和释放缓存的抽象方法给具体实现类去实现。
2. PageImpl：数据页的数据结构，包含页号、是否脏数据页、数据内容、所在的PageCache缓存。
3. PageOne：校验页面，用于启动DM的时候进行文件校验。
4. PageX：每个数据页的管理器。initRaw()新建一个数据页并设置FSO值，FSO后面存的其实就是一个个DataItem数据包
5. PageCacheImpl：数据页的缓存具体实现类，除了重写获取和释放两个方法外，还完成了所有数据页的统一管理：
* 1）获取数据库中的数据页总数；getPageNumber()
* 2）新建一个数据页并写入数据库文件；newPage(byte[] initData)
* 3）从缓存中获取指定的数据页；getPage(int pgno)
* 4）删除指定位置后面的数据页；truncateByBgno(int maxPgno)
* 6、PageIndex：方便DataItem的快速定位插入，其实现原理可以理解为HashMap那种数组+链表结构（实际实现是 List+ArrayList），先是一个大小为41的数组 存的是区间号（区间号从1>开始），然后每个区间号数组后面跟一个数组存满足空闲大小的所有数据页信息（PageInfo）。
* 7、Recover：日志恢复策略，主要维护两个日志：updateLog和insertLog，重做所有已完成事务 redo，撤销所有未完成事务undo
* 8、DataManager：统揽全局的类，主要方法也就是读写和修改，全部通过DataItem进行。

流程：

首先从DataManager进去创建DM（打开DM就不谈了，只是多了个检验PageOne和更新PageIndex），需要执行的操作是：
* 1）新建PageCache，DM里面有 页面缓存 和 DataItem缓存 两个实现；DataItem缓存也是在PageCache中获取的，DataItem缓存不存在的时候就去PageCache缓存获取，PageCache缓存没有才去数据库文件中获取；
* 2）新建日志，
* 3）构建DM管理器；
* 4）初始化校验页面1： dm.initPageOne()nnnDataManager的所有功能（主要功能就是CRUD，进行数据的读写修改都是靠DataItem进行操作的 ，所以PageX管理页面的时候FSO后面的DATA其实就是一个个的DataItem包）：
1. 初始化校验页面1：
  initPageOne() 和 启动时候进行校验：loadCheckPageOne()
2. 读取数据 read(long uid)：
  从DataItem缓存中读取一个DataItem数据包并进行校验，如果DataItem缓存中没有就会调用 DataManager下的getForCache(long uid)从PageCache缓存中读取DataItem数据包并加入DataItem缓存（其实PageCache缓存和DataItem缓存都是共用的一个cache Map存的，只是key不一样，page的key是页号，DataItem的key是uid，页号+偏移量），如果PgeCache也没有就去数据库文件读取。
3. 插入数据 insert(long xid, byte[] data)：
  先把数据打包成DataItem格式，然后在 pageIndex 中获取一个足以存储插入内容的页面的页号； 获取页面后，需要先写入插入日志Recover.insertLog(xid, pg, raw)，接着才可以通过 pageX 在目标数据页插入数据PageX.insert(pg, raw)，并返回插入位置的偏移。如果在pageIndex中没有空闲空间足够插入数据了，就需要新建一个数据页pc.newPage(PageX.initRaw())。最后需要将页面信息重新插入 pageIndex。
4. 修改数据就是先读取数据，然后修改DataItem内容，再插入DataItem数据。但是在修改数据操作的前后需要调用DataItemImp.after()进行解写锁并记录更新日志，这里需要依赖DataManager里面的logDataItem(long xid, DataItem di)方法；
5. 释放缓存：
  释放DataItem的缓存，实质上就是释放DataItem所在页的PageCache缓存

**详细说明：**

```java
public class PageImpl implements Page {
    private int pageNumber;
    private byte[] data;
    private boolean dirty;
    private Lock lock;

    private PageCache pc;
}
```

pageNumber 是这个页面的页号，**该页号从 1 开始**。data 就是这个页实际包含的字节数据。dirty 标志着这个页面是否是脏页面，在缓存驱逐的时候，脏页面需要被写回磁盘。

### 数据缓存页

考虑到实现遍历，采用引用计数缓存框架

`AbstractCache<T>` 是一个抽象类，内部有两个抽象方法，留给实现类去实现具体的操作：

```java
/**
 * 当资源不在缓存时的获取行为
 */
protected abstract T getForCache(long key) throws Exception;
/**
 * 当资源被驱逐时的写回行为
 */
protected abstract void releaseForCache(T obj);
```



### 数据页管理

#### 第一页

我们设置数据页的第一页为校验页面，在每次数据库启动时，会生成一串随机字节，存储在 100 ~ 107 字节。在数据库正常关闭时，会将这串字节拷贝到第一页的 108 ~ 115 字节。这样数据库在每次启动时，检查第一页两处的字节是否相同，以此来判断上一次是否正常关闭。如果是异常关闭，就需要执行数据库的回复流程。

#### 普通页

一个普通页面以一个 2 字节无符号数起始，表示这一页的空闲位置的偏移，剩下的部分都是实际存储的数据。

### 日志文件的读写

日志的二进制文件，按照如下的格式进行排布：

```
[XChecksum][Log1][Log2][Log3]...[LogN][BadTail]
```

其中 XChecksum 是一个四字节的整数，是对后续所有日志计算的校验和。Log1 ~ LogN 是常规的日志数据，BadTail 是在数据库崩溃时，没有来得及写完的日志数据，这个 BadTail 不一定存在。

每条日志的格式如下：

```
[Size][Checksum][Data]
```

其中，Size 是一个四字节整数，标识了 Data 段的字节数。Checksum 则是该条日志的校验和。

单条日志的校验和，其实就是通过一个指定的种子实现的：

```java
private int calChecksum(int xCheck, byte[] log) {
    for (byte b : log) {
        xCheck = xCheck * SEED + b;
    }
    return xCheck;
}
```

对所有日志求出校验和，求和就能得到日志文件的校验和 XChecksum。

#### 实现Logger为迭代器模式

实现 next() 方法，不断地从文件中读取下一条日志，并将其中的 Data 解析出来并返回。

```java
private byte[] internNext() {
    if(position + OF_DATA >= fileSize) {
        return null;
    }
    // 读取size
    ByteBuffer tmp = ByteBuffer.allocate(4);
    fc.position(position);
    fc.read(tmp);
    int size = Parser.parseInt(tmp.array());
    if(position + size + OF_DATA > fileSize) {
        return null;
    }

    // 读取checksum+data
    ByteBuffer buf = ByteBuffer.allocate(OF_DATA + size);
    fc.position(position);
    fc.read(buf);
    byte[] log = buf.array();

    // 校验 checksum
    int checkSum1 = calChecksum(0, Arrays.copyOfRange(log, OF_DATA, log.length));
    int checkSum2 = Parser.parseInt(Arrays.copyOfRange(log, OF_CHECKSUM, OF_DATA));
    if(checkSum1 != checkSum2) {
        return null;
    }
    position += log.length;
    return log;
}
```

在打开一个日志文件时，需要**首先校验日志文件的 XChecksum**，并**移除文件尾部可能存在的 BadTail**，由于 BadTail 该条日志尚未写入完成，文件的校验和也就不会包含该日志的校验和，去掉 BadTail 即可保证日志文件的一致性。

向日志文件写入日志时，也是首先将数据包裹成日志格式，写入文件后，再更新文件的校验和，更新校验和时，会刷新缓冲区，保证内容写入磁盘。

```java
public void log(byte[] data) {
    byte[] log = wrapLog(data);
    ByteBuffer buf = ByteBuffer.wrap(log);
    lock.lock();
    try {
        fc.position(fc.size());
        fc.write(buf);
    } catch(IOException e) {
        Panic.panic(e);
    } finally {
        lock.unlock();
    }
    updateXChecksum(log);
}

private void updateXChecksum(byte[] log) {
    this.xChecksum = calChecksum(this.xChecksum, log);
    fc.position(0);
    fc.write(ByteBuffer.wrap(Parser.int2Byte(xChecksum)));
    fc.force(false);
}

private byte[] wrapLog(byte[] data) {
    byte[] checksum = Parser.int2Byte(calChecksum(0, data));
    byte[] size = Parser.int2Byte(data.length);
    return Bytes.concat(size, checksum, data);
}
```

### 恢复策略

DM 为上层模块，提供了两种操作，分别是插入新数据 (I) 和 更新现有数据 (U)。

DM的日志策略是：

**在进行 I 和 U 操作之前，必须先进行对应的日志操作，在保证日志写入磁盘后，才进行数据操作**

这个日志策略，使得 DM 对于数据操作的磁盘同步，可以更加随意。日志在数据操作之前，保证到达了磁盘，那么即使该数据操作最后没有来得及同步到磁盘，数据库就发生了崩溃，后续也可以通过磁盘上的日志恢复该数据。

对于两种数据操作，DM 记录的日志如下：

* （Ti, I, A, X）：表示事务 Ti 在 A 位置插入一条数据 x
* （Ti, U, A, oldx, newx）：表示事务 Ti 将 A 位置的数据，从 oldx 更新为 new x

#### 单线程

由于单线程，Ti、Tj、Tk 的日志永远不会相交。假设日志中最后一个事务是 Ti：

1. 对 Ti 之前所有的事务日志进行重做 (redo)
2. 接着检查 Ti 的状态（XID 文件），如果 Ti 的状态是已完成（包括 committed 和 aborted），就将 Ti 重做，否则进行撤销 (undo)



## VM 解析：
DM 层向上层提供了数据项（Data Item）的概念，VM 通过管理所有的数据项，向上层提供了记录（Entry）的概念。

上层模块通过 VM 操作数据的最小单位，就是记录。VM 则在其内部，为每个记录，维护了多个版本（Version）。每当上层模块对某个记录进行修改时，VM 就会为这个记录创建一个新的版本。

## Transport:
将SQL语句和错误数据一起打包成一个package，然后packager调用Transporter将这个package通过socket连接进行发送和接收。encoder的编解码主要就是对sql语句里面的异常进行封包和解包处理工作。

## Server:
从服务端的Launcher入口进去，新建数据库或者打开存在的数据库，这里就要开启前面的模块了（tm、dm、vm、tbm）； 然后开启Server，Server启动一个 ServerSocket 监听端口，当有请求到来时直接把请求丢给一个新线程HandleSocket处理。HandleSocket初始化一个Packager，循环接收来自客户端的数据并交给Executor处理，再将处理结果打包成Package通过Packager.send(packge)发送出去。Executor就是SQL语句执行的核心，调用 Parser 获取到对应语句的结构化信息对象，并根据对象的类型，调用 TBM 的不同方法进行处理。

## Client：
从客户端的Launcher入口进去，主要就是链接服务器，打开一个Shell类；shell类完成对用户输入的获取并调用client.execute()执行语句，还有关闭退出客户端功能。client就一个主要方法execute() ，接收 shell 发过来的sql语句，并打包成pkg进行单次收发操作roundTrip()，得到执行结果并返回；

## read 语句的流程
假设现在要执行 read * from student where id = 123456789 ，并且在 id 上已经建有索引，执行过程如下：
1. TBM 接受语句，进行解析。
2. TBM 调用 IM 的 search 方法，查找对应记录所在的地址。
3. TBM 调用 VM 的 read 方法，并将地址作为参数，从VM中尝试读取记录内容。
4. VM 通过 DM 的 read 方法，读取该条记录的最新版本。
5. VM 检测该版本是否对该事务可见，其中需要 Visibility.isVisible() 方法。
6. 如果可见，则返回该版本的数据。
7. 如果不可见，则读取上一个版本，并重复 5,6,7 步骤。
8. TBM 取得记录的二进制内容后，对其进行解析，还原出记录内容。
9. TBM 将记录的内容返回给客户端。

## insert 语句的流程
假设现在要执行 insert into student values ("zhangsan", 123456789) 这条语句。
执行过程如下：
1. TBM 接收语句，并进行解析。
2. TBM 将 values 的值，二进制化。
3. TBM 利用 VM 的 insert 操作，将二进制化后的数据，插入到数据库。
4. VM 为该条数据建立版本控制，并利用 DM 的 insert方法，将数据插入到数据库。
5. DM将数据插入到数据库，并返回其被存储的地址。
6. VM 将得到的地址，作为该条记录的 handler, 返回给 TBM。
7. TBM 计算该条语句的 key，并将 handler 作为 data，并调用 IM 的 insert，建立索引。
8. IM 利用 DM 提供的 read 和 insert 等操作，将 key 和 data 存入索引中。
9. TBM 返回客户端插入成功的信息。

## 运行方式
注意首先需要在 pom.xml 中调整编译版本，如果导入 IDE，请更改项目的编译版本以适应你的 JDK

首先执行以下命令编译源码：

```
mvn compile
```
接着执行以下命令以 /tmp/mydb 作为路径创建数据库：

```
mvn exec:java -Dexec.mainClass="cn.edu.gzhu.server.Launcher" -Dexec.args="-create /tmp/mydb"
```
随后通过以下命令以默认参数启动数据库服务：
```
mvn exec:java -Dexec.mainClass="cn.edu.gzhu.server.Launcher" -Dexec.args="-open /tmp/mydb"
```
这时数据库服务就已经启动在本机的 9999 端口。重新启动一个终端，执行以下命令启动客户端连接数据库：

```
mvn exec:java -Dexec.mainClass="cn.edu.gzhu.client.Launcher"
```
会启动一个交互式命令行，就可以在这里输入类 SQL 语法，回车会发送语句到服务，并输出执行的结果。

## 致谢
https://github.com/CN-GuoZiyang