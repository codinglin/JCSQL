package cn.edu.gzhu.backend.tm.impl;

import cn.edu.gzhu.backend.tm.TransactionManager;
import cn.edu.gzhu.backend.utils.Panic;
import cn.edu.gzhu.backend.utils.Parser;
import cn.edu.gzhu.common.Error;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TransactionManagerImpl implements TransactionManager {
    // XID文件头长度
    static final int LEN_XID_HEADER_LENGTH = 8;
    // 每个事务的占用长度
    private static final int XID_FIELD_SIZE = 1;

    // 事务的三种状态
    private static final byte FIELD_TRAN_ACTIVE = 0;
    private static final byte FIELD_TRAN_COMMITTED = 1;
    private static final byte FIELD_TRAN_ABORTED = 2;

    // 超级事务，永远为committed状态
    public static final long SUPER_XID = 0;

    public static final String XID_SUFFIX = ".xid";

    private RandomAccessFile file;
    private FileChannel fc;
    private long xidCounter;
    private Lock counterLock;

    TransactionManagerImpl(RandomAccessFile raf, FileChannel fc){
        this.file = raf;
        this.fc = fc;
        counterLock = new ReentrantLock();
        checkXIDCounter();
    }

    /**
     * 检查XID文件是否合法
     * 读取XID_FILE_HEADER中的XIDCounter，根据它计算文件的理论长度，对比实际长度
     */
    private void checkXIDCounter() {
        long fileLength = 0;
        try{
            fileLength = file.length();
        } catch (IOException e) {
            Panic.panic(Error.BadXIDFileException);
        }
        if(fileLength < LEN_XID_HEADER_LENGTH){
            Panic.panic(Error.BadXIDFileException);
        }
        ByteBuffer buf = ByteBuffer.allocate(LEN_XID_HEADER_LENGTH);
        try{
            fc.position(0);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        this.xidCounter = Parser.parseLong(buf.array());
        long end = getXidPosition(this.xidCounter + 1);
        if(end != fileLength){
            Panic.panic(Error.BadXIDFileException);
        }
    }

    // 根据事务 xid 取得其在 xid 文件中对应的位置
    private long getXidPosition(long xid) {
        return LEN_XID_HEADER_LENGTH + (xid - 1) * XID_FIELD_SIZE;
    }


    // 将 xid 加一，并更新 XID Header
    private void IncreaseXIDCounter() {
        xidCounter ++;
        ByteBuffer buf = ByteBuffer.wrap(Parser.long2Byte(xidCounter));
        try {
            fc.position();
            fc.write(buf);
        } catch (IOException e){
            Panic.panic(e);
        }
        try {
            fc.force(false);
        } catch (IOException e){
            Panic.panic(e);
        }
    }

    // 更新 xid 事务的状态为 status
    private void updateXID(long xid, byte status) {
        long offset = getXidPosition(xid);
        byte[] tmp = new byte[XID_FIELD_SIZE];
        tmp[0] = status;
        ByteBuffer buf = ByteBuffer.wrap(tmp);
        try {
            fc.position(offset);
            fc.write(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        try {
            fc.force(false);
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    // 开始一个事务，并返回 XID
    @Override
    public long begin() {
        counterLock.lock();
        try{
            long xid = xidCounter + 1;
            updateXID(xid, FIELD_TRAN_ACTIVE);
            IncreaseXIDCounter();
            return xid;
        }finally {
            counterLock.unlock();
        }
    }

    // 提交 XID 事务
    @Override
    public void commit(long xid) {
        updateXID(xid, FIELD_TRAN_COMMITTED);
    }

    // 回滚 XID 事务
    @Override
    public void abort(long xid) {
        updateXID(xid, FIELD_TRAN_ABORTED);
    }

    // 检查 XID 事务是否处于 status 状态
    private boolean checkXID(long xid, byte status){
        long offset = getXidPosition(xid);
        ByteBuffer buf = ByteBuffer.wrap(new byte[XID_FIELD_SIZE]);
        try {
            fc.position(offset);
            fc.read(buf);
        } catch (IOException e) {
            Panic.panic(e);
        }
        return buf.array()[0] == status;
    }

    @Override
    public boolean isActive(long xid) {
        if(xid == SUPER_XID){
            return false;
        }
        return checkXID(xid, FIELD_TRAN_ACTIVE);
    }

    @Override
    public boolean isCommitted(long xid) {
        if(xid == SUPER_XID){
            return true;
        }
        return checkXID(xid, FIELD_TRAN_COMMITTED);
    }

    @Override
    public boolean isAborted(long xid) {
        if(xid == SUPER_XID){
            return false;
        }
        return checkXID(xid, FIELD_TRAN_ABORTED);
    }

    @Override
    public void close() {
        try {
            fc.close();
            file.close();
        } catch (IOException e) {
            Panic.panic(e);
        }
    }
}
