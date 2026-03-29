package com.onecritto.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProgressObservable {

    private final List<ProgressObserver> observers = new CopyOnWriteArrayList<>();

    public void addObserver(ProgressObserver o) {
        observers.add(o);
    }

    public void removeObserver(ProgressObserver o) {
        observers.remove(o);
    }

    protected void notifyProgress(double p) {
        for (ProgressObserver o : observers) {
            o.onProgress(p);
        }
    }

    protected void notifyMessage(String msg) {
        for (ProgressObserver o : observers) {
            o.onMessage(msg);
        }
    }
}
