package com.msservices.app.dto;

public class AudioExtractionRequest {

    private String videoName;
    private String videoId;

    // Optional Netscape cookies.txt content. It is a credential, so it must never be logged.
    private String cookies;

    // null = the client has not shown the notice yet, false = the user declined, true = accepted.
    private Boolean dataUsageConsent;

    // Optional backend id. Blank or absent means the default server (YouTube).
    private String serverId;

    public String getVideoName() {
        return videoName;
    }

    public void setVideoName(String videoName) {
        this.videoName = videoName;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getCookies() {
        return cookies;
    }

    public void setCookies(String cookies) {
        this.cookies = cookies;
    }

    public Boolean getDataUsageConsent() {
        return dataUsageConsent;
    }

    public void setDataUsageConsent(Boolean dataUsageConsent) {
        this.dataUsageConsent = dataUsageConsent;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
}
