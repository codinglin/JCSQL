package cn.edu.gzhu.backend.dm;

import cn.edu.gzhu.backend.common.AbstractCache;
import cn.edu.gzhu.backend.dm.dataItem.DataItem;
import cn.edu.gzhu.backend.dm.dataItem.impl.DataItemImpl;
import cn.edu.gzhu.backend.dm.logger.Logger;
import cn.edu.gzhu.backend.dm.page.Page;
import cn.edu.gzhu.backend.dm.page.PageCache;
import cn.edu.gzhu.backend.dm.page.PageOne;
import cn.edu.gzhu.backend.dm.page.PageX;
import cn.edu.gzhu.backend.dm.pageIndex.PageIndex;
import cn.edu.gzhu.backend.dm.pageIndex.PageInfo;
import cn.edu.gzhu.backend.tm.TransactionManager;
import cn.edu.gzhu.backend.utils.Panic;
import cn.edu.gzhu.backend.utils.Types;
import cn.edu.gzhu.common.Error;

public class DataManagerImpl extends AbstractCache<DataItem> implements DataManager{
    TransactionManager tm;
    PageCache pc;
    Logger logger;
    PageIndex pageIndex;
    Page pageOne;

    public DataManagerImpl(PageCache pc, Logger logger, TransactionManager tm) {
        super(0);
        this.pc = pc;
        this.logger = logger;
        this.tm = tm;
        this.pageIndex = new PageIndex();
    }

    @Override
    public DataItem read(long uid) throws Exception {
        DataItemImpl dataItem = (DataItemImpl) super.get(uid);
        if(!dataItem.isValid()){
            dataItem.release();
            return null;
        }
        return dataItem;
    }

    @Override
    public long insert(long xid, byte[] data) throws Exception {
        byte[] raw = DataItem.wrapDataItemRaw(data);
        if(raw.length > PageX.MAX_FREE_SPACE) {
            throw Error.DataTooLargeException;
        }
        PageInfo pageInfo = null;
        for (int i = 0; i < 5; i++) {
            pageInfo = pageIndex.select(raw.length);
            if(pageInfo != null){
                break;
            } else {
                 int newPageNum = pc.newPage(PageX.initRaw());
                 pageIndex.add(newPageNum, PageX.MAX_FREE_SPACE);
            }
        }
        if(pageInfo == null){
            throw Error.DatabaseBusyException;
        }
        Page page = null;
        int freeSpace = 0;
        try {
            page = pc.getPage(pageInfo.pageNum);
            byte[] log = Recover.insertLog(xid, page, raw);
            logger.log(log);

            short offset = PageX.insert(page, raw);

            page.release();
            return Types.addressToUid(pageInfo.pageNum, offset);
        } finally {
            // 将取出的 page 重新插入 pageIndex
            if(page != null){
                pageIndex.add(pageInfo.pageNum, PageX.getFreeSpace(page));
            } else {
                pageIndex.add(pageInfo.pageNum, freeSpace);
            }
        }
    }

    @Override
    public void close() {
        super.close();
        logger.close();

        PageOne.setVcClose(pageOne);
        pageOne.release();
        pc.close();
    }

    // 为 xid 生成 update 日志
    public void logDataItem(long xid, DataItem dataItem) {
        byte[] log = Recover.updateLog(xid, dataItem);
        logger.log(log);
    }

    public void releaseDataItem(DataItem dataItem){
        super.release(dataItem.getUid());
    }

    @Override
    protected DataItem getForCache(long uid) throws Exception {
        short offset = (short) (uid & ((1L << 16) - 1));
        uid >>>= 32;
        int pageNum = (int) (uid & ((1L << 32) - 1));
        Page page = pc.getPage(pageNum);
        return DataItem.parserDataItem(page, offset, this);
    }

    @Override
    protected void releaseForCache(DataItem dataItem) {
        dataItem.page().release();
    }

    // 在创建文件时初始化 PageOne
    void initPageOne() {
        int pageNum = pc.newPage(PageOne.initRaw());
        assert pageNum == 1;
        try {
            pageOne = pc.getPage(pageNum);
        } catch (Exception e) {
            Panic.panic(e);
        }
        pc.flushPage(pageOne);
    }

    // 在打开已有文件时读入 pageOne, 并验证正确性
    boolean loadCheckPageOne(){
        try {
            pageOne = pc.getPage(1);
        } catch (Exception e){
            Panic.panic(e);
        }
        return PageOne.checkVc(pageOne);
    }

    // 初始化 pageIndex
    void fillPageIndex(){
        int pageNumber = pc.getPageNumber();
        for (int i = 2; i <= pageNumber; i++) {
            Page page = null;
            try {
                page = pc.getPage(i);
            } catch (Exception e){
                Panic.panic(e);
            }
            pageIndex.add(page.getPageNumber(), PageX.getFreeSpace(page));
            page.release();
        }
    }
}
