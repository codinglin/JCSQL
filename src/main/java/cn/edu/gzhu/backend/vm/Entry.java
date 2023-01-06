package cn.edu.gzhu.backend.vm;

import cn.edu.gzhu.backend.common.SubArray;
import cn.edu.gzhu.backend.dm.dataItem.DataItem;
import cn.edu.gzhu.backend.utils.Parser;
import cn.edu.gzhu.backend.vm.impl.VersionManagerImpl;
import com.google.common.primitives.Bytes;

import java.util.Arrays;

/**
 * VM 想上层抽象出 Entry
 * Entry 结构： [XMIN] [XMAX] [DATA]
 * XMIN 是创建该条记录（版本）的事务编号，而 XMAX 则是删除该条记录（版本）的事务编号。
 * DATA 就是这条记录持有的数据。
 */
public class Entry {
    private static final int OF_XMIN = 0;
    private static final int OF_XMAX = OF_XMIN + 8;
    private static final int OF_DATA = OF_XMAX + 8;

    private long uid;
    private DataItem dataItem;
    private VersionManager vm;

    public static Entry newEntry(VersionManager vm, DataItem dataItem, long uid) {
        Entry entry = new Entry();
        entry.uid = uid;
        entry.dataItem = dataItem;
        entry.vm = vm;
        return entry;
    }

    public static Entry loadEntry(VersionManager vm, long uid) throws Exception {
        DataItem dataItem = ((VersionManagerImpl) vm).dm.read(uid);
        return newEntry(vm, dataItem, uid);
    }

    public static byte[] wrapEntryRaw (long xid, byte[] data){
        byte[] xMin = Parser.long2Byte(xid);
        byte[] xMax = new byte[8];
        return Bytes.concat(xMin, xMax, data);
    }

    public void release() {
        ((VersionManagerImpl) vm).releaseEntry(this);
    }

    public void remove() {
        dataItem.release();
    }

    // 以拷贝的形式返回内容
    public byte[] data() {
        dataItem.rLock();
        try {
            SubArray subArray = dataItem.data();
            byte[] data = new byte[subArray.end - subArray.start - OF_DATA];
            System.arraycopy(subArray.raw, subArray.start + OF_DATA, data, 0, data.length);
            return data;
        } finally {
            dataItem.rUnLock();
        }
    }

    public long getXMin() {
        dataItem.rLock();
        try {
            SubArray subArray = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(subArray.raw, subArray.start + OF_XMIN, subArray.start + OF_XMAX));
        } finally {
            dataItem.rUnLock();
        }
    }

    public long getXMax() {
        dataItem.rLock();
        try {
            SubArray subArray = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(subArray.raw, subArray.start + OF_XMAX, subArray.start + OF_DATA));
        } finally {
            dataItem.rUnLock();
        }
    }

    public void setXMax(long xid) {
        dataItem.before();
        try {
            SubArray subArray = dataItem.data();
            System.arraycopy(Parser.long2Byte(xid), 0, subArray.raw, subArray.start + OF_XMAX, 8);
        } finally {
            dataItem.after(xid);
        }
    }

    public long getUid() {
        return uid;
    }
}
