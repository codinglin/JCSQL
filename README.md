# JCSQL
使用JAVA去仿写MySQL基本功能

后端划分为五个模块，分别为：

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

## VM 解析：
DM 层向上层提供了数据项（Data Item）的概念，VM 通过管理所有的数据项，向上层提供了记录（Entry）的概念。

上层模块通过 VM 操作数据的最小单位，就是记录。VM 则在其内部，为每个记录，维护了多个版本（Version）。每当上层模块对某个记录进行修改时，VM 就会为这个记录创建一个新的版本。
