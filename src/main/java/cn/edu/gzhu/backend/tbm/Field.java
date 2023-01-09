package cn.edu.gzhu.backend.tbm;

import cn.edu.gzhu.backend.im.BPlusTree;
import cn.edu.gzhu.backend.parser.statement.SingleExpression;
import cn.edu.gzhu.backend.tbm.impl.TableManagerImpl;
import cn.edu.gzhu.backend.tm.impl.TransactionManagerImpl;
import cn.edu.gzhu.backend.utils.Panic;
import cn.edu.gzhu.backend.utils.ParseStringRes;
import cn.edu.gzhu.backend.utils.Parser;

import cn.edu.gzhu.common.Error;
import com.google.common.primitives.Bytes;

import java.util.Arrays;
import java.util.List;

/**
 * field 表示字段信息
 * 二进制格式为：
 * [FieldName][TypeName][IndexUid]  管理表和字段的数据结构：表名、表字段信息和字段索引等。
 * 如果field无索引，IndexUid为0
 */
public class Field {
    long uid;
    Table table;
    String fieldName;
    String fieldType;
    long index;
    BPlusTree bTree;

    public Field(long uid, Table table) {
        this.uid = uid;
        this.table = table;
    }

    public Field(Table table, String fieldName, String fieldType, long index) {
        this.table = table;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.index = index;
    }

    public static Field loadField(Table table, long uid) {
        byte[] raw = null;
        try {
            raw = ((TableManagerImpl) table.tableManager).vm.read(TransactionManagerImpl.SUPER_XID, uid);
        } catch (Exception e){
            Panic.panic(e);
        }
        assert raw != null;
        return new Field(uid, table).parseSelf(raw);
    }

    private Field parseSelf(byte[] raw) {
        int position = 0;
        ParseStringRes res = Parser.parseString(raw);
        fieldName = res.str;
        position += res.next;
        res = Parser.parseString(Arrays.copyOfRange(raw, position, raw.length));
        fieldType = res.str;
        position += res.next;
        this.index = Parser.parseLong(Arrays.copyOfRange(raw, position, position + 8));
        if(index != 0) {
            try {
                bTree = BPlusTree.load(index, ((TableManagerImpl)(table.tableManager)).dm);
            } catch (Exception e) {
                Panic.panic(e);
            }
        }
        return this;
    }

    public static Field createField(Table table, long xid, String filedName, String filedType, boolean indexed) throws Exception {
        typeCheck(filedType);
        Field f = new Field(table, filedName, filedType, 0);
        if(indexed) {
            long index = BPlusTree.create(((TableManagerImpl)table.tableManager).dm);
            BPlusTree bt = BPlusTree.load(index, ((TableManagerImpl)table.tableManager).dm);
            f.index = index;
            f.bTree = bt;
        }
        f.persistSelf(xid);
        return f;
    }

    private void persistSelf(long xid) throws Exception {
        byte[] nameRaw = Parser.string2Byte(fieldName);
        byte[] typeRaw = Parser.string2Byte(fieldType);
        byte[] indexRaw = Parser.long2Byte(index);
        this.uid = ((TableManagerImpl)table.tableManager).vm.insert(xid, Bytes.concat(nameRaw, typeRaw, indexRaw));
    }

    private static void typeCheck(String fieldType) throws Exception {
        if(!"int32".equals(fieldType) && !"int64".equals(fieldType) && !"string".equals(fieldType)) {
            throw Error.InvalidFieldException;
        }
    }

    public boolean isIndexed() {
        return index != 0;
    }

    public void insert(Object key, long uid) throws Exception {
        long uKey = value2Uid(key);
        bTree.insert(uKey, uid);
    }

    public List<Long> search(long left, long right) throws Exception {
        return bTree.searchRange(left, right);
    }

    public Object string2Value(String str) {
        switch (fieldType) {
            case "int32":
                return Integer.parseInt(str);
            case "int64":
                return Long.parseLong(str);
            case "string":
                return str;
        }
        return null;
    }

    public long value2Uid(Object key) {
        long uid = 0;
        switch(fieldType) {
            case "string":
                uid = Parser.str2Uid((String)key);
                break;
            case "int32":
                int uint = (int)key;
                return (long)uint;
            case "int64":
                uid = (long)key;
                break;
        }
        return uid;
    }

    public byte[] value2Raw(Object v) {
        byte[] raw = null;
        switch(fieldType) {
            case "int32":
                raw = Parser.int2Byte((int)v);
                break;
            case "int64":
                raw = Parser.long2Byte((long)v);
                break;
            case "string":
                raw = Parser.string2Byte((String)v);
                break;
        }
        return raw;
    }

    static class ParseValueRes {
        Object v;
        int shift;
    }

    public ParseValueRes parserValue(byte[] raw) {
        ParseValueRes res = new ParseValueRes();
        switch(fieldType) {
            case "int32":
                res.v = Parser.parseInt(Arrays.copyOf(raw, 4));
                res.shift = 4;
                break;
            case "int64":
                res.v = Parser.parseLong(Arrays.copyOf(raw, 8));
                res.shift = 8;
                break;
            case "string":
                ParseStringRes r = Parser.parseString(raw);
                res.v = r.str;
                res.shift = r.next;
                break;
        }
        return res;
    }

    public String printValue(Object v) {
        String str = null;
        switch(fieldType) {
            case "int32":
                str = String.valueOf((int)v);
                break;
            case "int64":
                str = String.valueOf((long)v);
                break;
            case "string":
                str = (String)v;
                break;
        }
        return str;
    }

    @Override
    public String toString() {
        return new StringBuilder("(")
                .append(fieldName)
                .append(", ")
                .append(fieldType)
                .append(index!=0?", Index":", NoIndex")
                .append(")")
                .toString();
    }

    public FieldCalRes calExp(SingleExpression exp) throws Exception {
        Object v = null;
        FieldCalRes res = new FieldCalRes();
        switch(exp.compareOp) {
            case "<":
                res.left = 0;
                v = string2Value(exp.value);
                res.right = value2Uid(v);
                if(res.right > 0) {
                    res.right --;
                }
                break;
            case "=":
                v = string2Value(exp.value);
                res.left = value2Uid(v);
                res.right = res.left;
                break;
            case ">":
                res.right = Long.MAX_VALUE;
                v = string2Value(exp.value);
                res.left = value2Uid(v) + 1;
                break;
        }
        return res;
    }
}
