package cn.edu.gzhu.backend.dm.logger;

public interface Logger {
    void log(byte[] data);
    void truncate(long x) throws Exception;
    byte[] next();
    void rewind();
    void close();


}
