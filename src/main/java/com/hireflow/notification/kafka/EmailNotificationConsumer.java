package com.hireflow.notification.kafka;

import com.hireflow.notification.event.EmailNotificationEvent;
import com.hireflow.notification.service.email.EmailService;
import com.hireflow.notification.service.stream.NotificationStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationConsumer {

    private final EmailService emailService;
    private final NotificationStreamService notificationStreamService;

    @KafkaListener(
            topics = "${hireflow.kafka.topics.notification-email}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(EmailNotificationEvent event) {
        log.info("Received notification event {}", event.getType());

        switch (event.getType()) {
            case EmailNotificationEvent.OTP_VERIFICATION -> {
                String otp = event.getOtp();
                emailService.sendOtp(event.getTo(), otp);
            }
            case EmailNotificationEvent.COMPANY_WELCOME -> {
                String firstName = event.getFirstName();
                String companyName = event.getCompanyName();
                emailService.sendCompanyWelcome(event.getTo(), firstName, companyName);
            }
            case EmailNotificationEvent.APPLICATION_STAGE_UPDATED -> {
                emailService.sendApplicationStageUpdate(event);
                notificationStreamService.broadcastApplicationStageUpdate(event);
            }
            case EmailNotificationEvent.HMANAGER_INVITE ->
                    emailService.sendHManagerInvite(event.getTo(), event.getInviteLink());
            default -> throw new IllegalArgumentException("Unsupported email notification event type: " + event.getType());
        }

        log.info("Processed notification event {}", event.getType());
    }
}
