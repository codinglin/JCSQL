package cn.edu.gzhu.backend.vm;

import cn.edu.gzhu.backend.tm.TransactionManager;

public class Visibility {
    /**
     * 取出要修改的数据 X 的最新提交版本，并检查该最新版本的创建者对当前事务是否可见
     * @param tm
     * @param t
     * @param entry
     * @return
     */
    public static boolean isVersionSkip(TransactionManager tm, Transaction t, Entry entry){
        long xMax = entry.getXMax();
        if(t.level == 0) {
            return false;
        } else {
            return tm.isCommitted(xMax) && (xMax > t.xid || t.isInSnapshot(xMax));
        }
    }

    public static boolean isVisible(TransactionManager tm, Transaction t, Entry e){
        if(t.level == 0){
            return readCommitted(tm, t, e);
        } else {
            return repeatableRead(tm, t, e);
        }
    }

    /**
     * 为了读提交的隔离级别设计
     * 若条件为 true，则版本对 Ti 可见。那么获取 Ti 适合的版本，只需要从最新版本开始，依次向前检查可见性，如果为 true，就可以直接返回。
     * @param tm
     * @param t
     * @param entry
     * @return
     */
    private static boolean readCommitted(TransactionManager tm, Transaction t, Entry entry){
        long xid = t.xid;
        long xMin = entry.getXMin();
        long xMax = entry.getXMax();
        if(xMin == xid && xMax == 0) {  // 由 Ti 创建且还未被删除
            return true;
        }
        if(tm.isCommitted(xMin)){   // 由一个已提交的事务创建且
            if(xMax == 0) return true;  // 尚未删除或
            if(xMax != xid) {   // 由一个未提交的事务删除
                if(!tm.isCommitted(xMax)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 可重复读
     * @param tm
     * @param t
     * @param entry
     * @return
     */
    private static boolean repeatableRead(TransactionManager tm, Transaction t, Entry entry) {
        long xid = t.xid;
        long xMin = entry.getXMin();
        long xMax = entry.getXMax();
        if(xMin == xid && xMax == 0) return true; // 由 Ti 创建且尚未被删除
        if(tm.isCommitted(xMin) && xMin < xid && !t.isInSnapshot(xMin)){ // 由一个已提交的事务创建且这个事务小于 Ti 且这个事务在 Ti 开始前提交
            if(xMax == 0) return true; // 且尚未被删除或
            if(xMax != xid){ // 由其他事务删除但是
                if(!tm.isCommitted(xMax) || xMax > xid || t.isInSnapshot(xMax)){ // 这个事务尚未提交或这个事务在 Ti 开始之后才开始或这个事务在 Ti 开始前还未提交
                    return true;
                }
            }
        }
        return false;
    }
}
