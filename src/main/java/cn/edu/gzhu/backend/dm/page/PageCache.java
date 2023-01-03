package cn.edu.gzhu.backend.dm.page;

public interface PageCache {
    public static final int PAGE_SIZE = 1 << 13;

    int newPage(byte[] initData);
    Page getPage(int pageNum) throws Exception;
    void close();
    void release(Page page);

    void truncateByPageNum(int maxPageNum);
    int getPageNumber();
    void flushPage(Page page);


}
