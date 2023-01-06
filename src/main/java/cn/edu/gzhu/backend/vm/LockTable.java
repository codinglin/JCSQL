package cn.edu.gzhu.backend.vm;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import cn.edu.gzhu.common.Error;

/**
 * 检测这个图中是否存在环（构成死锁）
 * 维护了一个依赖等待图，以进行死锁检测
 */
public class LockTable {
    private Map<Long, List<Long>> x2u; // 某个 XID 已经获得的资源的 UID 列表
    private Map<Long, Long> u2x; // UID 被某个 XID 持有
    private Map<Long, List<Long>> wait; // 正在等待 UID 的 XID 列表
    private Map<Long, Lock> waitLock; // 正在等待资源的 XID 的锁
    private Map<Long, Long> waitU; // XID 正在等待的 UID
    private Lock lock;

    public LockTable() {
        x2u = new HashMap<>();
        u2x = new HashMap<>();
        wait = new HashMap<>();
        waitLock = new HashMap<>();
        waitU = new HashMap<>();
        lock = new ReentrantLock();
    }

    // 不需要等待则返回 null,否则返回锁对象
    // 会造成死锁则抛出异常
    // 在每次出现等待的情况时，就尝试向图中增加一条边，并进行死锁检测。如果检测到死锁，就撤销这条边，不允许添加，并撤销该事务。
    public Lock add(long xid, long uid) throws Exception {
        lock.lock();
        try{
            // uid 是否在 xid 已经获得的资源的 uid 列表里, 如果已经有该资源则不需要等待，直接返回null
            if(isInList(x2u, xid, uid)){
                return null;
            }
            // 如果 uid 不被某个 xid 持有，则返回null
            if(!u2x.containsKey(uid)){
                u2x.put(uid, xid);
                putIntoList(x2u, xid, uid);
                return null;
            }
            waitU.put(xid, uid);
            putIntoList(wait, xid, uid);
            if(hasDeadLock()){
                waitU.remove(xid);
                removeFromList(wait, uid, xid);
                throw Error.DeadlockException;
            }
            Lock l = new ReentrantLock();
            l.lock();
            waitLock.put(xid, l);
            return l;
        } finally {
            lock.unlock();
        }
    }

    public void remove(long xid) {
        lock.lock();
        try {
            List<Long> list = x2u.get(xid);
            if(list != null){
                while (list.size() > 0){
                    Long uid = list.remove(0);
                    selectNewXID(uid);
                }
            }
            waitU.remove(xid);
            x2u.remove(xid);
            waitLock.remove(xid);
        } finally {
            lock.unlock();
        }
    }

    // 从等待队列中选择一个 xid 来占用 uid
    private void selectNewXID(Long uid) {
        u2x.remove(uid);
        List<Long> list = wait.get(uid);
        if(list == null) return;
        assert list.size() > 0;
        while (list.size() > 0){
            long xid = list.remove(0);
            if(!waitLock.containsKey(xid)){
                continue;
            } else{
                u2x.put(uid, xid);
                Lock l = waitLock.remove(xid);
                waitU.remove(xid);
                l.unlock();
                break;
            }
        }
        if(list.size() == 0) wait.remove(uid);
    }

    private void removeFromList(Map<Long, List<Long>> wait, long uid, long xid) {
        List<Long> list = wait.get(uid);
        if(list == null) return;
        Iterator<Long> iterator = list.iterator();
        while(iterator.hasNext()) {
            long e = iterator.next();
            if(e == xid) {
                iterator.remove();
                break;
            }
        }
        if(list.size() == 0) {
            wait.remove(uid);
        }
    }

    private Map<Long, Integer> xidStamp;
    private int stamp;

    /**
     * 查找图中是否有环，通过深搜的方式，需要注意这个图不一定是连通图。
     * 思路：为每个节点设置一个访问戳，都初始化为 -1, 随后遍历所有节点，以每个非 -1 的节点作为根进行深搜，
     *      并将深搜该连通图中遇到的所有节点都设置为同一个数字，不同的连通图数字不同。
     *      这样，如果在遍历某个图时，遇到了之前遍历过的节点，说明出现了环。
     * @return
     */
    private boolean hasDeadLock(){
        xidStamp = new HashMap<>();
        stamp = 1;
        for (Long xid : x2u.keySet()) {
            Integer s = xidStamp.get(xid);
            if(s != null && s > 0){
                continue;
            }
            stamp ++;
            if(dfs(xid)) {
                return true;
            }
        }
        return false;
    }

    private boolean dfs(Long xid) {
        Integer stp = xidStamp.get(xid);
        if(stp != null && stp == stamp) {
            return true;
        }
        if(stp != null && stp < stamp) {
            return false;
        }
        xidStamp.put(xid, stamp);
        Long uid = waitU.get(xid);
        if(uid == null) return false;
        Long x = u2x.get(uid);
        assert x != null;
        return dfs(x);
    }

    private void putIntoList(Map<Long, List<Long>> listMap, long xid, long uid) {
        if(!listMap.containsKey(xid)){
            listMap.put(xid, new ArrayList<>());
        }
        listMap.get(xid).add(0, uid);
    }

    private boolean isInList(Map<Long, List<Long>> x2u, long xid, long uid){
        // 某个 XID 已经获得的资源的 UID 列表
        List<Long> list = x2u.get(xid);
        if(list == null) return false;
        for (long e : list) {
            if (e == uid) {
                return true;
            }
        }
        return false;
    }
}
