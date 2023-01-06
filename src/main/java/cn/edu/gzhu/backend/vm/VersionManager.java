package cn.edu.gzhu.backend.vm;

import cn.edu.gzhu.backend.dm.DataManager;
import cn.edu.gzhu.backend.tm.TransactionManager;
import cn.edu.gzhu.backend.vm.impl.VersionManagerImpl;

public interface VersionManager {
    byte[] read(long xid, long uid) throws Exception;
    long insert(long xid, byte[] data) throws Exception;
    boolean delete(long xid, long uid) throws Exception;

    long begin(int level);
    void commit(long xid) throws Exception;
    void abort(long xid);

    public static VersionManager newVersionManager(TransactionManager tm, DataManager dm){
        return new VersionManagerImpl(tm, dm);
    }
}
