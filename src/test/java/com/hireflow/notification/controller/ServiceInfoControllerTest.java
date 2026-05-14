package com.hireflow.notification.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceInfoControllerTest {

    private final ServiceInfoController controller = new ServiceInfoController();

    @Test
    @DisplayName("Root endpoint should describe the notification service")
    void rootShouldReturnServiceInfo() {
        Map<String, String> response = controller.root();

        assertThat(response).containsEntry("service", "notification");
        assertThat(response).containsEntry("status", "running");
        assertThat(response).containsEntry("streamEndpoint", "/api/v1/notifications/stream/{applicantId}");
    }
}
