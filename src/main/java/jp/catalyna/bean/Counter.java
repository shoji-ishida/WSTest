package jp.catalyna.bean;

/**
 * Created by ishida on 2017/03/23.
 */
public class Counter implements CounterMBean {
    int session, push, display, click;
    public Counter() {
        session = 0;
        push = 0;
        display = 0;
        click = 0;
    }

    @Override
    public void putActiveSession(int i) {
        session = i;
    }

    @Override
    public int getActiveSession() {
        return session;
    }

    @Override
    public void putPushCount(int i) {
        push = i;
    }

    @Override
    public int getPushCount() {
        return push;
    }

    @Override
    public void putDisplayCount(int i) {
        display = i;
    }

    @Override
    public int getDisplayCount() {
        return display;
    }

    @Override
    public void putClickCount(int i) {
        click = i;
    }

    @Override
    public int getClickCount() {
        return click;
    }
}
