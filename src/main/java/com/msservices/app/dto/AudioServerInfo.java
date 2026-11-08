package com.msservices.app.dto;

public class AudioServerInfo {

    private String id;
    private String displayName;
    private boolean supportsUserCookies;
    private boolean defaultServer;

    public AudioServerInfo() {
    }

    public AudioServerInfo(String id, String displayName, boolean supportsUserCookies,
                           boolean defaultServer) {
        this.id = id;
        this.displayName = displayName;
        this.supportsUserCookies = supportsUserCookies;
        this.defaultServer = defaultServer;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isSupportsUserCookies() {
        return supportsUserCookies;
    }

    public void setSupportsUserCookies(boolean supportsUserCookies) {
        this.supportsUserCookies = supportsUserCookies;
    }

    public boolean isDefaultServer() {
        return defaultServer;
    }

    public void setDefaultServer(boolean defaultServer) {
        this.defaultServer = defaultServer;
    }
}
