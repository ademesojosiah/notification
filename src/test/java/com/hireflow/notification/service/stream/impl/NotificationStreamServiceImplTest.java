package com.hireflow.notification.service.stream.impl;

import com.hireflow.notification.event.EmailNotificationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NotificationStreamServiceImplTest {

    private NotificationStreamServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationStreamServiceImpl();
    }

    // --- subscribe() ---

    @Test
    @DisplayName("subscribe should return a non-null SseEmitter")
    void subscribeShouldReturnNonNullEmitter() {
        SseEmitter emitter = service.subscribe("applicant-1");

        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("subscribe should register the emitter so it can receive broadcasts")
    void subscribeShouldAddEmitterToRegistry() {
        service.subscribe("applicant-1");

        assertThat(emitterMap()).containsKey("applicant-1");
        assertThat(emitterMap().get("applicant-1")).hasSize(1);
    }

    @Test
    @DisplayName("subscribe should support multiple concurrent connections for the same applicant")
    void subscribeShouldAccumulateEmittersForSameApplicant() {
        service.subscribe("applicant-1");
        service.subscribe("applicant-1");

        assertThat(emitterMap().get("applicant-1")).hasSize(2);
    }

    @Test
    @DisplayName("subscribe should track emitters for different applicants independently")
    void subscribeShouldTrackDifferentApplicantsSeparately() {
        service.subscribe("applicant-1");
        service.subscribe("applicant-2");

        assertThat(emitterMap()).containsKey("applicant-1");
        assertThat(emitterMap()).containsKey("applicant-2");
        assertThat(emitterMap().get("applicant-1")).hasSize(1);
        assertThat(emitterMap().get("applicant-2")).hasSize(1);
    }

    // --- broadcastApplicationStageUpdate() ---

    @Test
    @DisplayName("broadcast should skip silently when applicantId is null")
    void broadcastShouldSkipWhenApplicantIdIsNull() {
        EmailNotificationEvent event = new EmailNotificationEvent();
        event.setApplicationId("app-1");

        assertThatNoException().isThrownBy(() -> service.broadcastApplicationStageUpdate(event));
    }

    @Test
    @DisplayName("broadcast should be a no-op when applicant has no active subscribers")
    void broadcastShouldBeNoOpWhenNoSubscribers() {
        assertThatNoException().isThrownBy(
                () -> service.broadcastApplicationStageUpdate(stageEvent("applicant-with-no-emitters")));
    }

    @Test
    @DisplayName("broadcast should send the stage update event to all registered emitters for the applicant")
    void broadcastShouldSendToAllRegisteredEmitters() throws IOException {
        SseEmitter emitter1 = mock(SseEmitter.class);
        SseEmitter emitter2 = mock(SseEmitter.class);
        injectEmitters("applicant-1", emitter1, emitter2);

        service.broadcastApplicationStageUpdate(stageEvent("applicant-1"));

        verify(emitter1).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter2).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("broadcast should not send to emitters registered for a different applicant")
    void broadcastShouldNotSendToOtherApplicantEmitters() throws IOException {
        SseEmitter unrelatedEmitter = mock(SseEmitter.class);
        injectEmitters("applicant-other", unrelatedEmitter);

        service.broadcastApplicationStageUpdate(stageEvent("applicant-1"));

        verify(unrelatedEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("broadcast should remove the emitter entry when it throws IOException on send")
    void broadcastShouldRemoveDeadEmitterOnSendFailure() throws IOException {
        SseEmitter deadEmitter = mock(SseEmitter.class);
        doThrow(new IOException("connection closed")).when(deadEmitter).send(any(SseEmitter.SseEventBuilder.class));
        injectEmitters("applicant-1", deadEmitter);

        service.broadcastApplicationStageUpdate(stageEvent("applicant-1"));

        assertThat(emitterMap()).doesNotContainKey("applicant-1");
    }

    @Test
    @DisplayName("broadcast should remove only the failing emitter, keeping healthy ones active")
    void broadcastShouldRemoveOnlyFailingEmitter() throws IOException {
        SseEmitter deadEmitter = mock(SseEmitter.class);
        SseEmitter liveEmitter = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(deadEmitter).send(any(SseEmitter.SseEventBuilder.class));
        injectEmitters("applicant-1", deadEmitter, liveEmitter);

        service.broadcastApplicationStageUpdate(stageEvent("applicant-1"));

        assertThat(emitterMap().get("applicant-1")).containsExactly(liveEmitter);
    }

    // --- helpers ---

    @SuppressWarnings("unchecked")
    private Map<String, List<SseEmitter>> emitterMap() {
        return (Map<String, List<SseEmitter>>) ReflectionTestUtils.getField(service, "emittersByApplicantId");
    }

    private void injectEmitters(String applicantId, SseEmitter... emitters) {
        emitterMap().put(applicantId, new CopyOnWriteArrayList<>(Arrays.asList(emitters)));
    }

    private static EmailNotificationEvent stageEvent(String applicantId) {
        EmailNotificationEvent event = new EmailNotificationEvent();
        event.setApplicantId(applicantId);
        event.setApplicationId("app-1");
        event.setCurrentStage("SCREENING");
        return event;
    }
}
