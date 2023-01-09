package cn.edu.gzhu.server;

import cn.edu.gzhu.backend.parser.Parser;
import cn.edu.gzhu.backend.parser.statement.*;
import cn.edu.gzhu.backend.tbm.BeginRes;
import cn.edu.gzhu.backend.tbm.TableManager;

import cn.edu.gzhu.common.Error;

public class Executor {
    private long xid;
    private TableManager tableManager;

    public Executor(TableManager tableManager) {
        this.tableManager = tableManager;
        this.xid = 0;
    }

    public void close() {
        if(xid != 0) {
            System.out.println("Abnormal Abort: " + xid);
            tableManager.abort(xid);
        }
    }

    public byte[] execute(byte[] sql) throws Exception {
        System.out.println("Execute: " + new String(sql));
        Object stat = Parser.parse(sql);
        if(Begin.class.isInstance(stat)) {
            if(xid != 0) {
                throw Error.NestedTransactionException;
            }
            BeginRes res = tableManager.begin((Begin) stat);
            xid = res.xid;
            return res.result;
        } else if(Commit.class.isInstance(stat)) {
            if(xid == 0){
                throw Error.NoTransactionException;
            }
            byte[] res = tableManager.commit(xid);
            xid = 0;
            return res;
        } else if (Abort.class.isInstance(stat)) {
            if(xid == 0) {
                throw Error.NoTransactionException;
            }
            byte[] res = tableManager.abort(xid);
            xid = 0;
            return res;
        } else {
            return execute2(stat);
        }
    }

    private byte[] execute2(Object stat) throws Exception {
        boolean tmpTransaction = false;
        Exception err = null;
        if(xid == 0) {
            tmpTransaction = true;
            BeginRes res = tableManager.begin(new Begin());
            xid = res.xid;
        }
        try {
            byte[] res = null;
            if(Show.class.isInstance(stat)) {
                res = tableManager.show(xid);
            } else if(Create.class.isInstance(stat)) {
                res = tableManager.create(xid, (Create)stat);
            } else if(Select.class.isInstance(stat)) {
                res = tableManager.read(xid, (Select)stat);
            } else if(Insert.class.isInstance(stat)) {
                res = tableManager.insert(xid, (Insert)stat);
            } else if(Delete.class.isInstance(stat)) {
                res = tableManager.delete(xid, (Delete)stat);
            } else if(Update.class.isInstance(stat)) {
                res = tableManager.update(xid, (Update)stat);
            }
            return res;
        } catch (Exception e){
            err = e;
            throw err;
        } finally {
            if(tmpTransaction) {
                if(err != null) {
                    tableManager.abort(xid);
                } else {
                    tableManager.commit(xid);
                }
                xid = 0;
            }
        }
    }
}
