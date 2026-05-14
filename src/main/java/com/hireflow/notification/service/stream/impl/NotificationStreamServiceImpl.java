package com.hireflow.notification.service.stream.impl;

import com.hireflow.notification.event.EmailNotificationEvent;
import com.hireflow.notification.service.stream.NotificationStreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class NotificationStreamServiceImpl implements NotificationStreamService {

    private static final long STREAM_TIMEOUT_MS = 30L * 60L * 1000L;

    private final Map<String, List<SseEmitter>> emittersByApplicantId = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String applicantId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emittersByApplicantId.computeIfAbsent(applicantId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(applicantId, emitter));
        emitter.onTimeout(() -> closeEmitter(applicantId, emitter));
        emitter.onError(error -> removeEmitter(applicantId, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("applicantId", applicantId)));
        } catch (IOException ex) {
            removeEmitter(applicantId, emitter);
        }

        return emitter;
    }

    @Override
    public void broadcastApplicationStageUpdate(EmailNotificationEvent event) {
        if (event.getApplicantId() == null) {
            log.warn("Skipping stage update broadcast without applicant id for application {}", event.getApplicationId());
            return;
        }

        List<SseEmitter> emitters = emittersByApplicantId.getOrDefault(event.getApplicantId(), List.of());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("application-stage-updated")
                        .data(event));
            } catch (IOException ex) {
                removeEmitter(event.getApplicantId(), emitter);
            }
        }

        log.info("Broadcast application stage update {} to {} SSE clients", event.getApplicationId(), emitters.size());
    }

    private void closeEmitter(String applicantId, SseEmitter emitter) {
        removeEmitter(applicantId, emitter);
        emitter.complete();
    }

    private void removeEmitter(String applicantId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByApplicantId.get(applicantId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByApplicantId.remove(applicantId);
        }
    }
}
