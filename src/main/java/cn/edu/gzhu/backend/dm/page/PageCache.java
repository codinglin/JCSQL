package cn.edu.gzhu.backend.dm.page;

import cn.edu.gzhu.backend.dm.page.impl.PageCacheImpl;
import cn.edu.gzhu.backend.utils.Panic;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import cn.edu.gzhu.common.Error;

public interface PageCache {
    public static final int PAGE_SIZE = 1 << 13;

    int newPage(byte[] initData);
    Page getPage(int pageNum) throws Exception;
    void close();
    void release(Page page);

    void truncateByPageNum(int maxPageNum);
    int getPageNumber();
    void flushPage(Page page);

    public static PageCacheImpl create(String path, long memory){
        File file = new File(path + PageCacheImpl.DB_SUFFIX);
        try {
            if(!file.createNewFile()) {
                Panic.panic(Error.FileExistsException);
            }
        } catch (Exception e) {
            Panic.panic(e);
        }
        if(!file.canRead() || !file.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }
        return new PageCacheImpl(raf, fc, (int)memory/PAGE_SIZE);
    }

    public static PageCacheImpl open(String path, long memory) {
        File file = new File(path+PageCacheImpl.DB_SUFFIX);
        if(!file.exists()) {
            Panic.panic(Error.FileNotExistsException);
        }
        if(!file.canRead() || !file.canWrite()) {
            Panic.panic(Error.FileCannotRWException);
        }

        FileChannel fc = null;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e) {
            Panic.panic(e);
        }
        return new PageCacheImpl(raf, fc, (int)memory/PAGE_SIZE);
    }
}
