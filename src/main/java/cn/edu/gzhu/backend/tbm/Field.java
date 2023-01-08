package cn.edu.gzhu.backend.tbm;

import cn.edu.gzhu.backend.im.BPlusTree;

/**
 * field 表示字段信息
 * 二进制格式为：
 * [FieldName][TypeName][IndexUid]  管理表和字段的数据结构：表名、表字段信息和字段索引等。
 * 如果field无索引，IndexUid为0
 */
public class Field {
    private long uid;
    private Table table;
    private String filedName;
    private String filedType;
    private long index;
    private BPlusTree bTree;

    public static Field loadField(Table table, long uid) {
        byte[] raw = null;
        return null;
    }
}
