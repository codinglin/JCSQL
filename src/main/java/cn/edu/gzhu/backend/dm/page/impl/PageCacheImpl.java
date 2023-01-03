package cn.edu.gzhu.backend.dm.page.impl;

import cn.edu.gzhu.backend.common.AbstractCache;
import cn.edu.gzhu.backend.dm.page.Page;
import cn.edu.gzhu.backend.dm.page.PageCache;
import cn.edu.gzhu.backend.utils.Panic;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import cn.edu.gzhu.common.Error;

public class PageCacheImpl extends AbstractCache<Page> implements PageCache {
    private static final int MEM_MIN_LIM = 10;
    public static final String DB_SUFFIX = ".db";

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock fileLock;

    private AtomicInteger pageNumbers;

    public PageCacheImpl(RandomAccessFile file, FileChannel fileChannel, int maxResource) {
        super(maxResource);
        if(maxResource < MEM_MIN_LIM){
            Panic.panic(Error.MemTooSmallException);
        }
        long length = 0;
        try {
            length = file.length();
        } catch (IOException e) {
            Panic.panic(e);
        }
        this.file = file;
        this.fc = fileChannel;
        this.fileLock = new ReentrantLock();
        this.pageNumbers = new AtomicInteger((int) length / PAGE_SIZE);
    }

    /**
     * 根据 pageNumber 从数据库文件中读取页数据，并包裹成 Page
     * @param key
     * @return
     * @throws Exception
     */
    @Override
    protected Page getForCache(long key) throws Exception {
        int pageNum = (int)key;
        long offset = pageOffset(pageNum);

        ByteBuffer buf = ByteBuffer.allocate(PAGE_SIZE);
        fileLock.lock();
        try{
            fc.position(offset);
            fc.read(buf);
        } catch (IOException e){
            Panic.panic(e);
        }
        fileLock.unlock();
        return new PageImpl(pageNum, buf.array(), this);
    }

    private static long pageOffset(int pageNum) {
        return (long) (pageNum - 1) * PAGE_SIZE;
    }

    @Override
    protected void releaseForCache(Page page) {
        if(page.isDirty()){
            flush(page);
            page.setDirty(false);
        }
    }

    @Override
    public int newPage(byte[] initData) {
        int pageNum = pageNumbers.incrementAndGet();
        Page page = new PageImpl(pageNum, initData, null);
        flush(page);
        return pageNum;
    }

    private void flush(Page page) {
        int pageNum = page.getPageNumber();
        long offset = pageOffset(pageNum);
        fileLock.lock();
        try{
            ByteBuffer buf = ByteBuffer.wrap(page.getData());
            fc.position(offset);
            fc.write(buf);
            fc.force(false);
        } catch (IOException e){
            Panic.panic(e);
        } finally {
            fileLock.unlock();
        }
    }

    @Override
    public Page getPage(int pageNum) throws Exception {
        return get((long) pageNum);
    }

    @Override
    public void close() {
        super.close();
        try {
            fc.close();
            file.close();
        } catch (IOException e){
            Panic.panic(e);
        }
    }

    @Override
    public void release(Page page) {
        release((long) page.getPageNumber());
    }

    @Override
    public void truncateByPageNum(int maxPageNum) {
        long size = pageOffset(maxPageNum + 1);
        try {
            file.setLength(size);
        } catch (IOException e){
            Panic.panic(e);
        }
        pageNumbers.set(maxPageNum);
    }

    @Override
    public int getPageNumber() {
        return pageNumbers.intValue();
    }

    @Override
    public void flushPage(Page page) {
        flush(page);
    }
}
