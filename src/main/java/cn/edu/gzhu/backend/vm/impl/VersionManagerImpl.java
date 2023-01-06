package cn.edu.gzhu.backend.vm.impl;

import cn.edu.gzhu.backend.common.AbstractCache;
import cn.edu.gzhu.backend.dm.DataManager;
import cn.edu.gzhu.backend.tm.TransactionManager;
import cn.edu.gzhu.backend.tm.impl.TransactionManagerImpl;
import cn.edu.gzhu.backend.utils.Panic;
import cn.edu.gzhu.backend.vm.*;
import cn.edu.gzhu.common.Error;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class VersionManagerImpl extends AbstractCache<Entry> implements VersionManager {
    public TransactionManager tm;
    public DataManager dm;
    private Map<Long, Transaction> activeTransaction;
    private Lock lock;
    private LockTable lockTable;

    public VersionManagerImpl(TransactionManager tm, DataManager dm) {
        super(0);
        this.tm = tm;
        this.dm = dm;
        this.activeTransaction = new HashMap<>();
        activeTransaction.put(TransactionManagerImpl.SUPER_XID, Transaction.newTransaction(TransactionManagerImpl.SUPER_XID, 0, null));
        this.lock = new ReentrantLock();
        this.lockTable = new LockTable();
    }

    /**
     * 读取一个 entry，注意判断下可见性即可
     * @param xid
     * @param uid
     * @return
     * @throws Exception
     */
    @Override
    public byte[] read(long xid, long uid) throws Exception {
        lock.lock();
        Transaction transaction = activeTransaction.get(xid);
        lock.unlock();
        if(transaction.err != null){
            throw transaction.err;
        }
        Entry entry = null;
        try {
            entry = super.get(uid);
        } catch (Exception e){
            if(e == Error.NullEntryException){
                return null;
            } else {
                throw e;
            }
        }
        try {
            if(Visibility.isVisible(tm, transaction, entry)){
                return entry.data();
            } else {
                return null;
            }
        } finally {
            entry.release();
        }
    }

    /**
     * 将数据包裹成 Entry，交给 DM 插入即可
     * @param xid
     * @param data
     * @return
     * @throws Exception
     */
    @Override
    public long insert(long xid, byte[] data) throws Exception {
        lock.lock();
        Transaction transaction = activeTransaction.get(xid);
        lock.unlock();
        if(transaction.err != null){
            throw  transaction.err;
        }
        byte[] raw = Entry.wrapEntryRaw(xid, data);
        return dm.insert(xid, raw);
    }

    /**
     * 1. 可见性判断。 2. 获取资源的锁。 3. 版本跳跃判断。 删除的操作只有一个设置 XMAX。
     * @param xid
     * @param uid
     * @return
     * @throws Exception
     */
    @Override
    public boolean delete(long xid, long uid) throws Exception {
        lock.lock();
        Transaction transaction = activeTransaction.get(xid);
        lock.unlock();
        if(transaction.err != null){
            throw transaction.err;
        }
        Entry entry = null;
        try {
            entry = super.get(uid);
        } catch (Exception e){
            if(e == Error.NullEntryException){
                return false;
            } else {
                throw e;
            }
        }
        try{
            if(!Visibility.isVersionSkip(tm, transaction, entry)){
                return false;
            }
            Lock l = null;
            try{
                l = lockTable.add(xid, uid);
            } catch (Exception e){
                transaction.err = Error.ConcurrentUpdateException;
                internAbort(xid, true);
                transaction.autoAborted = true;
                throw transaction.err;
            }
            if(l != null){
                l.lock();
                l.unlock();
            }
            if(entry.getXMax() == xid){
                return false;
            }
            if(Visibility.isVersionSkip(tm, transaction, entry)){
                transaction.err = Error.ConcurrentUpdateException;
                internAbort(xid, true);
                transaction.autoAborted = true;
                throw transaction.err;
            }
            entry.setXMax(xid);
            return true;
        } finally {
            entry.release();
        }
    }

    /**
     * 开启一个事务，并初始化事务的结构，并将其存放在 activeTransaction 中，用于检查和快照使用：
     * @param level
     * @return
     */
    @Override
    public long begin(int level) {
        lock.lock();
        try {
            long xid = tm.begin();
            Transaction transaction = Transaction.newTransaction(xid, level, activeTransaction);
            activeTransaction.put(xid, transaction);
            return xid;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 提交一个事务，主要就是 free 掉相关的结构，并且释放持有的锁，并修改 TM 状态
     * @param xid
     * @throws Exception
     */
    @Override
    public void commit(long xid) throws Exception {
        lock.lock();
        Transaction transaction = activeTransaction.get(xid);
        lock.unlock();
        try{
            if(transaction.err != null) {
                throw transaction.err;
            }
        } catch (NullPointerException e){
            System.out.println(xid);
            System.out.println(activeTransaction.keySet());
            Panic.panic(e);
        }
        lock.lock();
        activeTransaction.remove(xid);
        lock.unlock();
        lockTable.remove(xid);
        tm.commit(xid);
    }

    /**
     * abort 事务的方法有两种：手动和自动。
     * 手动指的是调用 abort() 方法，而自动则是在事务被检测出死锁时，会自动撤销回滚事务;或者出现版本跳跃时，也会自动回滚。
     * @param xid
     */
    @Override
    public void abort(long xid) {
        internAbort(xid, false);
    }

    private void internAbort(long xid, boolean autoAborted) {
        lock.lock();
        Transaction transaction = activeTransaction.get(xid);
        if(!autoAborted){
            activeTransaction.remove(xid);
        }
        lock.unlock();
        if(transaction.autoAborted) return;
        lockTable.remove(xid);
        tm.abort(xid);
    }

    @Override
    protected Entry getForCache(long uid) throws Exception {
        Entry entry = Entry.loadEntry(this, uid);
        if(entry == null){
            throw Error.NullEntryException;
        }
        return entry;
    }

    @Override
    protected void releaseForCache(Entry entry) {
        entry.remove();
    }

    public void releaseEntry(Entry entry) {
        super.release(entry.getUid());
    }
}
