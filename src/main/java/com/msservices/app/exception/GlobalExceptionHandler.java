package com.msservices.app.exception;

import com.msservices.app.dto.AudioExtractionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidVideoSearchException.class)
    public ResponseEntity<AudioExtractionResponse> handleInvalidSearch(InvalidVideoSearchException exception) {
        return ResponseEntity
                .badRequest()
                .body(AudioExtractionResponse.failure(exception.getMessage()));
    }

    @ExceptionHandler(YoutubeToolUnavailableException.class)
    public ResponseEntity<AudioExtractionResponse> handleToolUnavailable(YoutubeToolUnavailableException exception) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(AudioExtractionResponse.failure(exception.getMessage()));
    }

    @ExceptionHandler(AudioExtractionException.class)
    public ResponseEntity<AudioExtractionResponse> handleAudioExtraction(AudioExtractionException exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(AudioExtractionResponse.failure(exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AudioExtractionResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected error processing request", exception);
        return ResponseEntity
                .internalServerError()
                .body(AudioExtractionResponse.failure("We could not process your request right now. Try again later."));
    }
}
