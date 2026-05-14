package com.hireflow.notification.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ServiceInfoController {

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of(
                "service", "notification",
                "status", "running",
                "streamEndpoint", "/api/v1/notifications/stream/{applicantId}"
        );
    }
}
