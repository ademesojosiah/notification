package com.hireflow.notification.kafka;

import com.hireflow.notification.event.EmailNotificationEvent;
import com.hireflow.notification.service.email.EmailService;
import com.hireflow.notification.service.stream.NotificationStreamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class EmailNotificationConsumerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationStreamService notificationStreamService;

    @InjectMocks
    private EmailNotificationConsumer consumer;

    @Test
    @DisplayName("Should dispatch OTP verification event to email service")
    void consumeShouldDispatchOtpVerificationEvent() {
        EmailNotificationEvent event = new EmailNotificationEvent();
        event.setType(EmailNotificationEvent.OTP_VERIFICATION);
        event.setTo("candidate@hireflow.test");
        event.setOtp("739241");

        consumer.consume(event);

        verify(emailService).sendOtp("candidate@hireflow.test", "739241");
        verifyNoMoreInteractions(emailService);
        verifyNoInteractions(notificationStreamService);
    }

    @Test
    @DisplayName("Should dispatch company welcome event to email service")
    void consumeShouldDispatchCompanyWelcomeEvent() {
        EmailNotificationEvent event = new EmailNotificationEvent();
        event.setType(EmailNotificationEvent.COMPANY_WELCOME);
        event.setTo("owner@acme.test");
        event.setFirstName("Ada");
        event.setCompanyName("Acme Labs");

        consumer.consume(event);

        verify(emailService).sendCompanyWelcome("owner@acme.test", "Ada", "Acme Labs");
        verifyNoMoreInteractions(emailService);
        verifyNoInteractions(notificationStreamService);
    }

    @Test
    @DisplayName("Should send email and broadcast SSE for application stage update event")
    void consumeShouldSendEmailAndBroadcastStageUpdateEvent() {
        EmailNotificationEvent event = new EmailNotificationEvent();
        event.setType(EmailNotificationEvent.APPLICATION_STAGE_UPDATED);
        event.setApplicantId("applicant-1");
        event.setApplicationId("application-1");
        event.setCurrentStage("SCREENING");

        consumer.consume(event);

        verify(emailService).sendApplicationStageUpdate(event);
        verify(notificationStreamService).broadcastApplicationStageUpdate(event);
        verifyNoMoreInteractions(emailService, notificationStreamService);
    }

    @Test
    @DisplayName("Should send HR manager invite email for HMANAGER_INVITE event")
    void consumeShouldSendHManagerInviteEmail() {
        EmailNotificationEvent event = new EmailNotificationEvent();
        event.setType(EmailNotificationEvent.HMANAGER_INVITE);
        event.setTo("newmanager@acme.test");
        event.setInviteLink("http://localhost:5173/accept-invite?token=abc-123");

        consumer.consume(event);

        verify(emailService).sendHManagerInvite("newmanager@acme.test", "http://localhost:5173/accept-invite?token=abc-123");
        verifyNoMoreInteractions(emailService);
        verifyNoInteractions(notificationStreamService);
    }

    @Test
    @DisplayName("Should reject unsupported email notification event type")
    void consumeShouldRejectUnsupportedEventType() {
        EmailNotificationEvent event = new EmailNotificationEvent();
        event.setType("PASSWORD_RESET");

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported email notification event type: PASSWORD_RESET");

        verifyNoInteractions(emailService);
        verifyNoInteractions(notificationStreamService);
    }
}
