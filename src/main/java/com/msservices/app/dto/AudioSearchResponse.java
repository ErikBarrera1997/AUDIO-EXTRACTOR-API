package com.msservices.app.dto;

import java.util.List;

public class AudioSearchResponse {

    private boolean success;
    private String message;
    private List<AudioSearchResultDto> results;

    public static AudioSearchResponse success(String message, List<AudioSearchResultDto> results) {
        AudioSearchResponse response = new AudioSearchResponse();
        response.setSuccess(true);
        response.setMessage(message);
        response.setResults(results);
        return response;
    }

    public static AudioSearchResponse failure(String message) {
        AudioSearchResponse response = new AudioSearchResponse();
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

    public List<AudioSearchResultDto> getResults() {
        return results;
    }

    public void setResults(List<AudioSearchResultDto> results) {
        this.results = results;
    }
}
