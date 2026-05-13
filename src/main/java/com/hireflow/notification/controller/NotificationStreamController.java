package com.hireflow.notification.controller;

import com.hireflow.notification.service.stream.NotificationStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationStreamController {

    private final NotificationStreamService notificationStreamService;

    @GetMapping(value = "/stream/{applicantId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String applicantId) {
        return notificationStreamService.subscribe(applicantId);
    }
}
