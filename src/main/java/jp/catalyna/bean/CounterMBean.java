package jp.catalyna.bean;

/**
 * Created by ishida on 2017/03/23.
 */
public interface CounterMBean {
    public void putActiveSession(int i);
    public int getActiveSession();

    public void putPushCount(int i);
    public int getPushCount();

    public void putDisplayCount(int i);
    public int getDisplayCount();

    public void putClickCount(int i);
    public int getClickCount();
}
