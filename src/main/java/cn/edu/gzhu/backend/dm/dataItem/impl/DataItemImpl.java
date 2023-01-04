package cn.edu.gzhu.backend.dm.dataItem.impl;

import cn.edu.gzhu.backend.common.SubArray;
import cn.edu.gzhu.backend.dm.dataItem.DataItem;
import cn.edu.gzhu.backend.dm.page.Page;

import java.util.concurrent.locks.Lock;

/**
 * dataItem 结构如下：
 * [ValidFlag] [DataSize] [Data]
 * ValidFlag 1字节，0为合法，1为非法
 * DataSize  2字节，标识Data的长度
 */
public class DataItemImpl implements DataItem {
    public static final int OF_VALID = 0;
    public static final int OF_SIZE = 1;
    public static final int OF_DATA = 3;

    private SubArray raw;
    private byte[] oldRaw;
    private Lock rLock;
    private Lock wLock;


    @Override
    public SubArray data() {
        return null;
    }

    @Override
    public void before() {

    }

    @Override
    public void unBefore() {

    }

    @Override
    public void after(long xid) {

    }

    @Override
    public void release() {

    }

    @Override
    public void lock() {

    }

    @Override
    public void unlock() {

    }

    @Override
    public void rLock() {

    }

    @Override
    public void rUnLock() {

    }

    @Override
    public Page page() {
        return null;
    }

    @Override
    public long getUid() {
        return 0;
    }

    @Override
    public byte[] getOldRaw() {
        return new byte[0];
    }

    @Override
    public SubArray getRaw() {
        return null;
    }
}
