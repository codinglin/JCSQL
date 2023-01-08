package cn.edu.gzhu.backend.tbm;

import cn.edu.gzhu.backend.dm.DataManager;
import cn.edu.gzhu.backend.parser.statement.*;
import cn.edu.gzhu.backend.vm.VersionManager;

public interface TableManager {
    BeginRes begin(Begin begin);
    byte[] commit(long xid) throws Exception;
    byte[] abort(long xid);
    byte[] show(long xid);
    byte[] create(long xid, Create create) throws Exception;
    byte[] insert(long xid, Insert insert) throws Exception;
    byte[] read(long xid, Select select) throws Exception;
    byte[] update(long xid, Update update) throws Exception;
    byte[] delete(long xid, Delete delete) throws Exception;

    public static TableManager create(String path, VersionManager vm, DataManager dm) {
        Booter booter = Booter.create(path);
        return null;
    }
}
