package cn.edu.gzhu.backend.dm;

import cn.edu.gzhu.backend.common.SubArray;
import cn.edu.gzhu.backend.dm.dataItem.DataItem;
import cn.edu.gzhu.backend.dm.logger.Logger;
import cn.edu.gzhu.backend.dm.page.Page;
import cn.edu.gzhu.backend.dm.page.PageCache;
import cn.edu.gzhu.backend.dm.page.PageX;
import cn.edu.gzhu.backend.tm.TransactionManager;
import cn.edu.gzhu.backend.utils.Panic;
import cn.edu.gzhu.backend.utils.Parser;
import com.google.common.primitives.Bytes;
import org.checkerframework.checker.units.qual.A;

import java.util.*;

/**
 * 在进行 插入和更新 操作之前，必须先进行对于的日志操作，在保证日志写入磁盘后，才进行数据操作。
 * 这个日志策略，使得 DM 对于数据操作的磁盘同步，可以更加随意。
 * 日志在操作数据之前，保证到达了磁盘，那么即使该数据操作最后没有来得及同步到磁盘，数据库就发生了崩溃，后续也可以通过磁盘上的日志恢复该数据。
 *
 * 为了保证数据的可恢复，VM 层传递到 DM 的操作序列需要满足以下两个规则：
 *  规定 1：正在进行的事务，不会读取其他任何未提交的事务产生的数据。
 *  规定 2：正在进行的事务，不会修改其他任何未提交的事务修改或产生的数据。
 */
public class Recover {
    private static final byte LOG_TYPE_INSERT = 0;
    private static final byte LOG_TYPE_UPDATE = 1;

    private static final int REDO = 0;
    private static final int UNDO = 1;

    static class InsertLogInfo {
        long xid;
        int pageNum;
        short offset;
        byte[] raw;
    }

    static class UpdateLogInfo {
        long xid;
        int pageNum;
        short offset;
        byte[] oldRaw;
        byte[] newRaw;
    }

    public static void recover(TransactionManager tm, Logger logger, PageCache pageCache){
        System.out.println("Recovering ...");
        logger.rewind();
        int maxPageNum = 0;
        while (true) {
            byte[] log = logger.next();
            if(log == null) break;
            int pageNum;
            if(isInsertLog(log)){
                InsertLogInfo insertLogInfo = parseInsertLog(log);
                pageNum = insertLogInfo.pageNum;
            } else {
                UpdateLogInfo updateLogInfo = parseUpdateLog(log);
                pageNum = updateLogInfo.pageNum;
            }
            if(pageNum > maxPageNum){
                maxPageNum = pageNum;
            }
        }
        if(maxPageNum == 0){
            maxPageNum = 1;
        }
        pageCache.truncateByPageNum(maxPageNum);
        System.out.println("Truncate to " + maxPageNum + " pages.");

        redoTransactions(tm, logger, pageCache);
        System.out.println("Redo Transactions Over.");

        undoTransactions(tm, logger, pageCache);
        System.out.println("Undo Transactions Over.");

        System.out.println("Recovery Over.");
    }

    private static void redoTransactions(TransactionManager tm, Logger logger, PageCache pageCache) {
        logger.rewind();
        while (true) {
            byte[] log = logger.next();
            if(log == null) break;
            if(isInsertLog(log)){
                InsertLogInfo insertLogInfo = parseInsertLog(log);
                long xid = insertLogInfo.xid;
                if(!tm.isActive(xid)){
                    doInsertLog(pageCache, log, REDO);
                }
            } else{
                UpdateLogInfo updateLogInfo = parseUpdateLog(log);
                long xid = updateLogInfo.xid;
                if(!tm.isAborted(xid)){
                    doUpdateLog(pageCache, log, REDO);
                }
            }
        }
    }

    private static void undoTransactions(TransactionManager tm, Logger logger, PageCache pageCache) {
        Map<Long, List<byte[]>> logCache = new HashMap<>();
        logger.rewind();
        while (true) {
            byte[] log = logger.next();
            if(log == null) break;
            if(isInsertLog(log)){
                InsertLogInfo insertLogInfo = parseInsertLog(log);
                long xid = insertLogInfo.xid;
                if(tm.isActive(xid)) {
                    if(!logCache.containsKey(xid)){
                        logCache.put(xid, new ArrayList<>());
                    }
                    logCache.get(xid).add(log);
                }
            } else {
                UpdateLogInfo updateLogInfo = parseUpdateLog(log);
                long xid = updateLogInfo.xid;
                if(tm.isActive(xid)){
                    if(!logCache.containsKey(xid)){
                        logCache.put(xid, new ArrayList<>());
                    }
                    logCache.get(xid).add(log);
                }
            }
        }

        // 对所有 active log 进行倒序 undo
        for (Map.Entry<Long, List<byte[]>> entry : logCache.entrySet()) {
            List<byte[]> logs = entry.getValue();
            for (int i = logs.size() - 1; i >= 0 ; i--) {
                byte[] log = logs.get(i);
                if(isInsertLog(log)){
                    doInsertLog(pageCache, log, UNDO);
                } else {
                    doUpdateLog(pageCache, log, UNDO);
                }
            }
            tm.abort(entry.getKey());
        }
    }

    // [LogType] [XID] [UID] [OldRaw] [NewRaw]
    private static final int OF_TYPE = 0;
    private static final int OF_XID = OF_TYPE + 1;
    private static final int OF_UPDATE_UID = OF_XID + 8;
    private static final int OF_UPDATE_RAW = OF_UPDATE_UID + 8;

    public static byte[] updateLog(long xid, DataItem dataItem){
        byte[] logType = {LOG_TYPE_UPDATE};
        byte[] xidRaw = Parser.long2Byte(xid);
        byte[] uidRaw = Parser.long2Byte(dataItem.getUid());
        byte[] oldRaw = dataItem.getOldRaw();
        SubArray raw = dataItem.getRaw();
        byte[] newRaw = Arrays.copyOfRange(raw.raw, raw.start, raw.end);
        return Bytes.concat(logType, xidRaw, uidRaw, oldRaw, newRaw);
    }

    private static UpdateLogInfo parseUpdateLog(byte[] log) {
        UpdateLogInfo updateLogInfo = new UpdateLogInfo();
        updateLogInfo.xid = Parser.parseLong(Arrays.copyOfRange(log, OF_XID, OF_UPDATE_UID));
        long uid = Parser.parseLong(Arrays.copyOfRange(log, OF_UPDATE_UID, OF_UPDATE_RAW));
        updateLogInfo.offset = (short) (uid & ((1L << 16) - 1));
        uid >>>= 32;
        updateLogInfo.pageNum = (int)(uid & ((1L << 32) - 1));
        int length = (log.length - OF_UPDATE_RAW) / 2;
        updateLogInfo.oldRaw = Arrays.copyOfRange(log, OF_UPDATE_RAW, OF_UPDATE_RAW + length);
        updateLogInfo.newRaw = Arrays.copyOfRange(log, OF_UPDATE_RAW + length, OF_UPDATE_RAW + length * 2);
        return updateLogInfo;
    }

    private static void doUpdateLog(PageCache pageCache, byte[] log, int flag) {
        int pageNum;
        short offset;
        byte[] raw;
        if(flag == REDO){
            UpdateLogInfo updateLogInfo = parseUpdateLog(log);
            pageNum = updateLogInfo.pageNum;
            offset = updateLogInfo.offset;
            raw = updateLogInfo.newRaw;
        } else {
            UpdateLogInfo updateLogInfo = parseUpdateLog(log);
            pageNum = updateLogInfo.pageNum;
            offset = updateLogInfo.offset;
            raw = updateLogInfo.oldRaw;
        }
        Page page = null;
        try {
            page = pageCache.getPage(pageNum);
        } catch (Exception e){
            Panic.panic(e);
        }
        try {
            PageX.recoverUpdate(page, raw, offset);
        } finally {
            page.release();
        }
    }

    // [LogType] [XID] [PageNum] [Offset] [Raw]
    private static final int OF_INSERT_PAGE_NUM = OF_XID + 8;
    private static final int OF_INSERT_OFFSET = OF_INSERT_PAGE_NUM + 4;
    private static final int OF_INSERT_RAW = OF_INSERT_OFFSET + 2;

    public static byte[] insertLog(long xid, Page page, byte[] raw){
        byte[] logTypeRaw = {LOG_TYPE_INSERT};
        byte[] xidRaw = Parser.long2Byte(xid);
        byte[] pageNumRaw = Parser.int2Byte(page.getPageNumber());
        byte[] offsetRaw = Parser.short2Byte(PageX.getFSO(page));
        return Bytes.concat(logTypeRaw, xidRaw, pageNumRaw, offsetRaw, raw);
    }

    private static InsertLogInfo parseInsertLog(byte[] log) {
        InsertLogInfo insertLogInfo = new InsertLogInfo();
        insertLogInfo.xid = Parser.parseLong(Arrays.copyOfRange(log, OF_XID, OF_INSERT_PAGE_NUM));
        insertLogInfo.pageNum = Parser.parseInt(Arrays.copyOfRange(log, OF_INSERT_PAGE_NUM, OF_INSERT_OFFSET));
        insertLogInfo.offset = Parser.parseShort(Arrays.copyOfRange(log, OF_INSERT_OFFSET, OF_INSERT_RAW));
        insertLogInfo.raw = Arrays.copyOfRange(log, OF_INSERT_RAW, log.length);
        return insertLogInfo;
    }

    private static boolean isInsertLog(byte[] log) {
        return log[0] == LOG_TYPE_INSERT;
    }

    private static void doInsertLog(PageCache pageCache, byte[] log, int flag) {
        InsertLogInfo insertLogInfo = parseInsertLog(log);
        Page page = null;
        try {
            page = pageCache.getPage(insertLogInfo.pageNum);
        } catch (Exception e){
            Panic.panic(e);
        }
        try {
            if(flag == UNDO){
                DataItem.setDataItemRawInvalid(insertLogInfo.raw);
            }
            PageX.recoverInsert(page, insertLogInfo.raw, insertLogInfo.offset);
        } finally {
            page.release();
        }
    }
}
