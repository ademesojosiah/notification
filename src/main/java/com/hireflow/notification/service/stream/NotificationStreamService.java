package com.hireflow.notification.service.stream;

import com.hireflow.notification.event.EmailNotificationEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface NotificationStreamService {

    SseEmitter subscribe(String applicantId);

    void broadcastApplicationStageUpdate(EmailNotificationEvent event);
}
