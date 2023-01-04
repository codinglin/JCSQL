package cn.edu.gzhu.backend.dm.logger;

import cn.edu.gzhu.backend.dm.logger.impl.LoggerImpl;
import cn.edu.gzhu.backend.utils.Panic;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;

import cn.edu.gzhu.backend.utils.Parser;
import cn.edu.gzhu.common.Error;

public interface Logger {
    void log(byte[] data);
    void truncate(long x) throws Exception;
    byte[] next();
    void rewind();
    void close();

    public static Logger create(String path){
        File file = new File(path + LoggerImpl.LOG_SUFFIX);
        try{
            if(!file.createNewFile()){
                Panic.panic(Error.FileExistsException);
            }
        } catch (Exception e){
            Panic.panic(e);
        }
        if(!file.canRead() || !file.canWrite()){
            Panic.panic(Error.FileCannotRWException);
        }
        FileChannel fc = null;
        RandomAccessFile raf = null;
        try{
            raf = new RandomAccessFile(file, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e){
            Panic.panic(e);
        }
        ByteBuffer buf = ByteBuffer.wrap(Parser.int2Byte(0));
        try {
            fc.position(0);
            fc.write(buf);
            fc.force(false);
        } catch (IOException e){
            Panic.panic(e);
        }
        return new LoggerImpl(raf, fc, 0);
    }

    public static Logger open(String path){
        File file = new File(path + LoggerImpl.LOG_SUFFIX);
        if(!file.exists()){
            Panic.panic(Error.FileNotExistsException);
        }
        if(!file.canRead() || !file.canWrite()){
            Panic.panic(Error.FileCannotRWException);
        }
        FileChannel fc = null;
        RandomAccessFile raf = null;
        try{
            raf = new RandomAccessFile(file, "rw");
            fc = raf.getChannel();
        } catch (FileNotFoundException e){
            Panic.panic(e);
        }
        LoggerImpl lg = new LoggerImpl(raf, fc);
        lg.init();
        return lg;
    }
}
