package cn.edu.gzhu.backend.dm;

import cn.edu.gzhu.backend.dm.dataItem.DataItem;
import cn.edu.gzhu.backend.dm.logger.Logger;
import cn.edu.gzhu.backend.dm.page.PageCache;
import cn.edu.gzhu.backend.dm.page.PageOne;
import cn.edu.gzhu.backend.tm.TransactionManager;

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
