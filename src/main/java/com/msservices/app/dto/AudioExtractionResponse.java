package com.msservices.app.dto;

public class AudioExtractionResponse {

    private boolean success;
    private String message;
    private String videoTitle;
    private String fileName;
    private String contentType;
    private String audioBase64;

    public static AudioExtractionResponse success(String message, ExtractedAudioDto audio) {
        AudioExtractionResponse response = new AudioExtractionResponse();
        response.setSuccess(true);
        response.setMessage(message);
        response.setVideoTitle(audio.getVideoTitle());
        response.setFileName(audio.getFileName());
        response.setContentType(audio.getContentType());
        response.setAudioBase64(audio.getAudioBase64());
        return response;
    }

    public static AudioExtractionResponse failure(String message) {
        AudioExtractionResponse response = new AudioExtractionResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getVideoTitle() {
        return videoTitle;
    }

    public void setVideoTitle(String videoTitle) {
        this.videoTitle = videoTitle;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getAudioBase64() {
        return audioBase64;
    }

    public void setAudioBase64(String audioBase64) {
        this.audioBase64 = audioBase64;
    }
}
