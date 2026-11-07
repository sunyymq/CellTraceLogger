package com.celltrace.logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 进程内状态与采样分发（Service → Activity） */
public final class AppState {

    public interface Listener {
        void onSnapshot(Snapshot s);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    public static volatile Snapshot latest;
    public static volatile boolean recording;

    private AppState() {
    }

    public static void addListener(Listener l) {
        if (l == null) return;
        if (!LISTENERS.contains(l)) LISTENERS.add(l);
        Snapshot s = latest;
        if (s != null) l.onSnapshot(s);
    }

    public static void removeListener(Listener l) {
        if (l != null) LISTENERS.remove(l);
    }

    public static void publish(Snapshot s) {
        latest = s;
        recording = s.recording;
        for (Listener l : LISTENERS) {
            try {
                l.onSnapshot(s);
            } catch (Throwable ignored) {
            }
        }
    }
}
