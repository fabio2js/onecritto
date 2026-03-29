package com.onecritto.observer;


public interface ProgressObserver {
    void onProgress(double value);     // 0.0 – 1.0
    void onMessage(String message);
}