package cn.edu.gzhu.backend.dm;

import cn.edu.gzhu.backend.dm.dataItem.DataItem;
import cn.edu.gzhu.backend.dm.logger.Logger;
import cn.edu.gzhu.backend.dm.page.PageCache;
import cn.edu.gzhu.backend.dm.page.PageOne;
import cn.edu.gzhu.backend.tm.TransactionManager;

/**
 * DM的主要职责有：
 *  1）分页管理 DB 文件，并进行缓存。
 *  2）管理日志文件，保证在发生错误时可以根据日志进行回复。
 *  3）抽象 DB 文件为 DataItem 供上层模块使用，并提供缓存。
 * 可以归纳为以下两点：1. 上层模块和文件系统之间的抽象层，向下直接读取文件，向上提供数据的包装。 2. 日志功能
 *
 * 引入引用计数策略：只有上层模块主动释放引用，缓存在确保没有模块在使用这个资源了，才回去驱逐资源。
 */
public interface DataManager {
    DataItem read(long uid) throws Exception;
    long insert(long xid, byte[] data) throws Exception;
    void close();

    public static DataManager create(String path, long memory, TransactionManager tm){
        PageCache pc = PageCache.create(path, memory);
        Logger logger = Logger.create(path);
        DataManagerImpl dm = new DataManagerImpl(pc, logger, tm);
        dm.initPageOne();
        return dm;
    }

    public static DataManager open(String path, long memory, TransactionManager tm){
        PageCache pc = PageCache.open(path, memory);
        Logger logger = Logger.open(path);
        DataManagerImpl dm = new DataManagerImpl(pc, logger, tm);
        if(!dm.loadCheckPageOne()){
            Recover.recover(tm, logger, pc);
        }
        dm.fillPageIndex();
        PageOne.setVcOpen(dm.pageOne);
        dm.pc.flushPage(dm.pageOne);
        return dm;
    }
}
