package cn.edu.gzhu.backend.tm;

/**
 * 
 */
public interface TransactionManager {
    // 开启一个新事物
    long begin();
    // 提交一个事务
    void commit(long xid);
    // 取消一个事务
    void abort(long xid);
    // 查询一个事务的状态是否是正在进行的状态
    boolean isActive(long xid);
    // 查询一个事务的状态是否是已提交
    boolean isCommitted(long xid);
    // 查询一个事务的状态是否是已取消
    boolean isAborted(long xid);
    // 关闭TM
    void close();
}
