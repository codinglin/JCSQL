package cn.edu.gzhu.backend.dm.pageIndex;

public class PageInfo {
    public int pageNum;
    public int freeSpace;

    public PageInfo(int pageNum, int freeSpace) {
        this.pageNum = pageNum;
        this.freeSpace = freeSpace;
    }
}
