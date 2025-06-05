package com.example.model;

public class LockInfo {
    private final String className;
    private final String identity;

    public LockInfo(String className, String identity) {
        this.className = className;
        this.identity = identity;
    }

    public String getClassName() {
        return className;
    }

    public String getIdentity() {
        return identity;
    }
}
