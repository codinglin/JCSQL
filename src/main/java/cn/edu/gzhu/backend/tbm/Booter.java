package cn.edu.gzhu.backend.tbm;

import java.io.File;

// 记录第一个表的 uid
public class Booter {
    public static final String BOOTER_SUFFIX = ".bt";
    public static final String BOOTER_TMP_SUFFIX = ".bt_tmp";

    private String path;
    private File file;

    public Booter(String path, File file) {
        this.path = path;
        this.file = file;
    }

    public static Booter create(String path) {
        removeBadTmp(path);
        File f = new File(path + BOOTER_SUFFIX);
        return new Booter(path, f);
    }

    private static void removeBadTmp(String path) {
    }
}
