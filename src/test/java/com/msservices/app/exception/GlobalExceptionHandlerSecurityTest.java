package com.msservices.app.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.msservices.app.dto.AudioExtractionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerSecurityTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Input validation failures return 400 with a client message")
    void invalidSearchReturnsBadRequest() {
        ResponseEntity<AudioExtractionResponse> response =
                handler.handleInvalidSearch(new InvalidVideoSearchException("The search is too short."));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("The search is too short.", response.getBody().getMessage());
    }

    @Test
    @DisplayName("A bot-check block returns 503 instead of a 200 with an empty body")
    void accessBlockedReturnsServiceUnavailable() {
        ResponseEntity<AudioExtractionResponse> response = handler.handleAccessBlocked(
                new YoutubeAccessBlockedException("YouTube is blocking this request from our server."));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertNotNull(response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().isBlank());
    }

    @Test
    @DisplayName("Failure responses never carry audio data")
    void failureResponseHasNoAudioPayload() {
        AudioExtractionResponse body = AudioExtractionResponse.failure("boom");

        assertFalse(body.isSuccess());
        assertEquals("boom", body.getMessage());
        org.junit.jupiter.api.Assertions.assertNull(body.getAudioBase64());
        org.junit.jupiter.api.Assertions.assertNull(body.getFileName());
        org.junit.jupiter.api.Assertions.assertNull(body.getContentType());
        org.junit.jupiter.api.Assertions.assertNull(body.getVideoTitle());
    }

    @Test
    @DisplayName("Unexpected errors return a generic message with no stack trace or internals")
    void unexpectedErrorIsGeneric() {
        ResponseEntity<AudioExtractionResponse> response = handler.handleUnexpected(
                new IllegalStateException("jdbc:postgres://user:secret@db:5432/prod failed"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        String message = response.getBody().getMessage();
        assertFalse(message.contains("jdbc"));
        assertFalse(message.contains("secret"));
        assertFalse(message.contains("IllegalStateException"));
        assertFalse(message.contains("db:5432"));
    }

    @Test
    @DisplayName("Tool unavailability returns 503 and does not echo the secret path")
    void toolUnavailableDoesNotLeakCookiePath() {
        ResponseEntity<AudioExtractionResponse> response = handler.handleToolUnavailable(
                new YoutubeToolUnavailableException("The YouTube session cookies are not available on the server."));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        String message = response.getBody().getMessage();
        assertFalse(message.contains("/app/cookies.txt"));
        assertFalse(message.contains("cookies.txt"));
    }

    @Test
    @DisplayName("Malformed bodies and bad params return 400 without echoing the payload")
    void malformedRequestsAreRejected() {
        ResponseEntity<AudioExtractionResponse> unreadable = handler.handleUnreadableMessage(
                new org.springframework.http.converter.HttpMessageNotReadableException("bad body", null));
        assertEquals(HttpStatus.BAD_REQUEST, unreadable.getStatusCode());
        assertEquals("Malformed request body.", unreadable.getBody().getMessage());

        ResponseEntity<AudioExtractionResponse> binding = handler.handleBinding(
                new org.springframework.web.bind.ServletRequestBindingException("missing param"));
        assertEquals(HttpStatus.BAD_REQUEST, binding.getStatusCode());
        assertEquals("Invalid request parameters.", binding.getBody().getMessage());
    }

    @Test
    @DisplayName("Client aborts and timeouts do not surface a 200")
    void abortAndTimeoutAreErrors() {
        ResponseEntity<AudioExtractionResponse> abort = handler.handleClientAbort(
                new org.apache.catalina.connector.ClientAbortException("closed"));
        assertEquals(HttpStatus.BAD_REQUEST, abort.getStatusCode());

        ResponseEntity<AudioExtractionResponse> timeout = handler.handleAsyncTimeout(
                new org.springframework.web.context.request.async.AsyncRequestTimeoutException());
        assertEquals(HttpStatus.REQUEST_TIMEOUT, timeout.getStatusCode());
    }

    @Test
    @DisplayName("A blocked response is not an empty success payload")
    void blockedResponseIsNeverEmptySuccess() {
        ResponseEntity<AudioExtractionResponse> response = handler.handleAccessBlocked(
                new YoutubeAccessBlockedException("blocked"));

        assertTrue(response.getStatusCode().isError());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }
}
