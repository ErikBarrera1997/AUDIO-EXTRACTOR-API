package com.msservices.app.exception;

public class YoutubeAccessBlockedException extends AudioExtractionException {

    public YoutubeAccessBlockedException(String message) {
        super(message);
    }

    public YoutubeAccessBlockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
