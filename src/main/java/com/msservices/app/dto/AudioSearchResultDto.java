package com.msservices.app.dto;

public class AudioSearchResultDto {

    private String videoId;
    private String title;
    private String author;
    private Long durationSeconds;
    private Long viewCount;

    public AudioSearchResultDto() {
    }

    public AudioSearchResultDto(String videoId, String title, String author, Long durationSeconds, Long viewCount) {
        this.videoId = videoId;
        this.title = title;
        this.author = author;
        this.durationSeconds = durationSeconds;
        this.viewCount = viewCount;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }
}
