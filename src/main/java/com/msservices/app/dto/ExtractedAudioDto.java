package com.msservices.app.dto;

public class ExtractedAudioDto {

    private final String videoTitle;
    private final String fileName;
    private final String contentType;
    private final String audioBase64;

    public ExtractedAudioDto(String videoTitle, String fileName, String contentType, String audioBase64) {
        this.videoTitle = videoTitle;
        this.fileName = fileName;
        this.contentType = contentType;
        this.audioBase64 = audioBase64;
    }

    public String getVideoTitle() {
        return videoTitle;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public String getAudioBase64() {
        return audioBase64;
    }
}
