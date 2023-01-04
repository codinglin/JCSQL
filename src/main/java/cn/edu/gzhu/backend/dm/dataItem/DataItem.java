package cn.edu.gzhu.backend.dm.dataItem;

import cn.edu.gzhu.backend.common.SubArray;
import cn.edu.gzhu.backend.dm.dataItem.impl.DataItemImpl;
import cn.edu.gzhu.backend.dm.page.Page;
import cn.edu.gzhu.backend.utils.Parser;
import com.google.common.primitives.Bytes;

public interface DataItem {
    SubArray data();

    void before();

    void unBefore();

    void after(long xid);

    void release();

    void lock();

    void unlock();

    void rLock();

    void rUnLock();

    Page page();

    long getUid();

    byte[] getOldRaw();

    SubArray getRaw();

    public static byte[] wrapDataItemRaw(byte[] raw){
        byte[] valid = new byte[1];
        byte[] size = Parser.short2Byte((short) raw.length);
        return Bytes.concat(valid, size, raw);
    }

    // 从页面的 offset 处解析出 dataItem
//    public static DataItem parserDataItem(Page page, short offset, DataManagetI)

    public static void setDataItemRawInvalid(byte[] raw){
        raw[DataItemImpl.OF_VALID] = (byte) 1;
    }
}
