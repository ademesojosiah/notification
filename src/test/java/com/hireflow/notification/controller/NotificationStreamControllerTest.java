package com.hireflow.notification.controller;

import com.hireflow.notification.service.stream.NotificationStreamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationStreamControllerTest {

    @Mock
    private NotificationStreamService notificationStreamService;

    @InjectMocks
    private NotificationStreamController controller;

    @Test
    @DisplayName("Should delegate to stream service and return the emitter it provides")
    void streamShouldReturnEmitterFromService() {
        SseEmitter expected = new SseEmitter();
        when(notificationStreamService.subscribe("applicant-1")).thenReturn(expected);

        SseEmitter result = controller.stream("applicant-1");

        assertThat(result).isSameAs(expected);
        verify(notificationStreamService).subscribe("applicant-1");
    }

    @Test
    @DisplayName("Should pass applicant ID to stream service unchanged")
    void streamShouldForwardApplicantIdAsIs() {
        String applicantId = "some-uuid-value";
        when(notificationStreamService.subscribe(applicantId)).thenReturn(new SseEmitter());

        controller.stream(applicantId);

        verify(notificationStreamService).subscribe(applicantId);
    }
}
