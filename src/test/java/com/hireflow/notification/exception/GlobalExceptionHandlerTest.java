package com.hireflow.notification.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("GET", "/api/v1/notifications/stream/applicant-1");
    }

    @Test
    @DisplayName("EmailDeliveryException should return 503 with error detail")
    void handleEmailDeliveryShouldReturn503() {
        EmailDeliveryException ex = new EmailDeliveryException("SMTP server unavailable", new RuntimeException("timeout"));

        ResponseEntity<Map<String, String>> response = handler.handleEmailDelivery(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("error", "Email delivery failed");
        assertThat(response.getBody()).containsEntry("message", "SMTP server unavailable");
    }

    @Test
    @DisplayName("IllegalArgumentException should return 400 with bad request detail")
    void handleIllegalArgumentShouldReturn400() {
        IllegalArgumentException ex = new IllegalArgumentException("Unsupported event type: UNKNOWN");

        ResponseEntity<Map<String, String>> response = handler.handleIllegalArgument(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Bad request");
        assertThat(response.getBody()).containsEntry("message", "Unsupported event type: UNKNOWN");
    }

    @Test
    @DisplayName("Unhandled Exception should return 500 with generic message")
    void handleGeneralShouldReturn500() {
        Exception ex = new RuntimeException("unexpected failure");

        ResponseEntity<Map<String, String>> response = handler.handleGeneral(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "An unexpected error occurred");
    }

    @Test
    @DisplayName("AsyncRequestTimeoutException should return 204 with no body")
    void handleAsyncRequestTimeoutShouldReturnNoContent() {
        ResponseEntity<Void> response = handler.handleAsyncRequestTimeout(new AsyncRequestTimeoutException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("NoResourceFoundException should return 404 instead of generic 500")
    void handleNoResourceFoundShouldReturn404() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/assets/js/auth.js", "assets/js/auth.js");

        ResponseEntity<Map<String, String>> response = handler.handleNoResourceFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Not found");
        assertThat(response.getBody()).containsEntry("message", "No resource exists for this path");
    }

    @Test
    @DisplayName("EmailDeliveryException body should not contain null values")
    void handleEmailDeliveryShouldNotReturnNullBody() {
        EmailDeliveryException ex = new EmailDeliveryException("connection refused", new RuntimeException());

        ResponseEntity<Map<String, String>> response = handler.handleEmailDelivery(ex, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).doesNotContainValue(null);
    }
}
