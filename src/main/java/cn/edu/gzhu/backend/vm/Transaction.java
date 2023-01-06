package cn.edu.gzhu.backend.vm;

import cn.edu.gzhu.backend.tm.impl.TransactionManagerImpl;

import java.util.HashMap;
import java.util.Map;

// vm 对一个事务的抽象

/**
 * 为了实现可重复读
 * primary: 事务只能读取它开始时，就已经结束的那些事务产生的数据版本
 * 这条规定，增加了事务需要忽略：
 *  1. 在本事务后开始的事务的数据
 *  2. 本事务开始时还是 active 状态的事务的数据
 * 对于第一条，只需要比较事务 ID，即可确定。
 * 而对于第二条，则需要在事务 Ti 开始时，记录下当前或缺的所有事务 SP(Ti), 如果记录的某个版本，XMIN 在 SP[Ti]中，也应当对 Ti 不可见。
 */
public class Transaction {
    public long xid;
    public int level;
    public Map<Long, Boolean> snapshot;
    public Exception err;
    public boolean autoAborted;

    public static Transaction newTransaction(long xid, int level, Map<Long, Transaction> active){
        Transaction transaction = new Transaction();
        transaction.xid = xid;
        transaction.level = level;
        if(level != 0){
            transaction.snapshot = new HashMap<>();
            for (Long x : active.keySet()) {
                transaction.snapshot.put(x, true);
            }
        }
        return transaction;
    }

    public boolean isInSnapshot(long xid){
        if(xid == TransactionManagerImpl.SUPER_XID){
            return false;
        }
        return snapshot.containsKey(xid);
    }
}
