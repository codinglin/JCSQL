package cn.edu.gzhu.backend.tm;

import cn.edu.gzhu.backend.tm.impl.TransactionManagerImpl;
import cn.edu.gzhu.backend.utils.Panic;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import cn.edu.gzhu.common.Error;

/**
 * 每个事务都有一个XID，这个 ID 唯一标识了这个事务。事务的 XID 从 1 开始标号，并自增，不可重复。
 * 并特殊规定 XID 0 是一个超级事务（Super Transaction）。
 * 当一些操作在没有申请事务的情况下进行，那么可以将操作的 XID 设置为 0。XID 为 0 的事务的状态永远是 committed。
 *
 * 每个事务都有下面三种状态：1.active，正在进行，尚未结束。 2.committed，已提交。 3.aborted，已撤销（回滚）。
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

    public static TransactionManagerImpl create(String path){
        File file = new File(path + TransactionManagerImpl.XID_SUFFIX);
        try {
            if(!file.createNewFile()){
                Panic.panic(Error.FileExistsException);
            }
        } catch (Exception e){
            Panic.panic(e);
        }
        if(!file.canRead() || !file.canWrite()){
            Panic.panic(Error.FileCannotRWException);
        }
        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e){
            Panic.panic(e);
        }
        // 写空 XID 文件头
        ByteBuffer buf = ByteBuffer.wrap(new byte[TransactionManagerImpl.LEN_XID_HEADER_LENGTH]);
        try {
            fc.position(0);
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }

        return new TransactionManagerImpl(raf, fc);
    }

    public static TransactionManagerImpl open(String path){
        File f = new File(path+TransactionManagerImpl.XID_SUFFIX);
        if(!f.exists()) {
            Panic.panic(Error.FileNotExistsException);
        }
        if(!f.canRead() || !f.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }

        return new TransactionManagerImpl(raf, fc);
    }
}
