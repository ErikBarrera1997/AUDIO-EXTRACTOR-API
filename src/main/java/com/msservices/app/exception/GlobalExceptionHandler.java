package com.msservices.app.exception;

import com.msservices.app.dto.AudioExtractionResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidVideoSearchException.class)
    public ResponseEntity<AudioExtractionResponse> handleInvalidSearch(InvalidVideoSearchException exception) {
        return ResponseEntity
                .badRequest()
                .body(AudioExtractionResponse.failure(exception.getMessage()));
    }

    @ExceptionHandler(YoutubeAccessBlockedException.class)
    public ResponseEntity<AudioExtractionResponse> handleAccessBlocked(YoutubeAccessBlockedException exception) {
        log.warn("YouTube access blocked: {}", exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<AudioExtractionResponse> handleNoResourceFound(NoResourceFoundException exception) {
        log.warn("No resource found for request '{}'", exception.getResourcePath());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(AudioExtractionResponse.failure("Resource not found."));
    }

    @ExceptionHandler(ClientAbortException.class)
    public ResponseEntity<AudioExtractionResponse> handleClientAbort(ClientAbortException exception) {
        log.warn("Client aborted connection: {}", exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(AudioExtractionResponse.failure("Connection interrupted by the client."));
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<AudioExtractionResponse> handleAsyncTimeout(AsyncRequestTimeoutException exception) {
        log.warn("Asynchronous request timed out");
        return ResponseEntity
                .status(HttpStatus.REQUEST_TIMEOUT)
                .body(AudioExtractionResponse.failure("Request timed out."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AudioExtractionResponse> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        log.warn("Malformed request body: {}", exception.getMessage());
        return ResponseEntity
                .badRequest()
                .body(AudioExtractionResponse.failure("Malformed request body."));
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<AudioExtractionResponse> handleBinding(ServletRequestBindingException exception) {
        log.warn("Request binding failed: {}", exception.getMessage());
        return ResponseEntity
                .badRequest()
                .body(AudioExtractionResponse.failure("Invalid request parameters."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AudioExtractionResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected error processing request", exception);
        return ResponseEntity
                .internalServerError()
                .body(AudioExtractionResponse.failure("We could not process your request right now. Try again later."));
    }
}
