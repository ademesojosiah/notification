package com.hireflow.notification.service.email.impl;

import com.hireflow.notification.event.EmailNotificationEvent;
import com.hireflow.notification.exception.EmailDeliveryException;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    private static final String FROM = "noreply@hireflow.test";

    @Mock
    private JavaMailSender mailSender;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(mailSender);
        ReflectionTestUtils.setField(emailService, "from", FROM);
    }

    @Test
    @DisplayName("Should send OTP email with recipient, subject and verification code")
    void sendOtpShouldBuildAndSendVerificationEmail() throws Exception {
        MimeMessage message = newMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.sendOtp("candidate@hireflow.test", "739241");

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).isEqualTo("Your HireFlow Verification Code");
        assertThat(addresses(sent.getFrom())).containsExactly(FROM);
        assertThat(addresses(sent.getRecipients(Message.RecipientType.TO))).containsExactly("candidate@hireflow.test");
        assertThat(body(sent))
                .contains("HireFlow Email Verification")
                .contains("739241")
                .contains("10 minutes");
    }

    @Test
    @DisplayName("Should send company welcome email with recipient, subject and company details")
    void sendCompanyWelcomeShouldBuildAndSendWelcomeEmail() throws Exception {
        MimeMessage message = newMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.sendCompanyWelcome("owner@acme.test", "Ada", "Acme Labs");

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).isEqualTo("Welcome to HireFlow, Acme Labs!");
        assertThat(addresses(sent.getFrom())).containsExactly(FROM);
        assertThat(addresses(sent.getRecipients(Message.RecipientType.TO))).containsExactly("owner@acme.test");
        assertThat(body(sent))
                .contains("Welcome to HireFlow, Ada!")
                .contains("Acme Labs")
                .contains("AI-powered screening");
    }

    @Test
    @DisplayName("Should send APPLIED stage email with correct subject and applicant details")
    void sendApplicationStageUpdateApplied() throws Exception {
        MimeMessage message = newMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.sendApplicationStageUpdate(stageEvent("APPLIED"));

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).isEqualTo("Application Received – Backend Engineer at Acme Labs");
        assertThat(addresses(sent.getFrom())).containsExactly(FROM);
        assertThat(addresses(sent.getRecipients(Message.RecipientType.TO))).containsExactly("candidate@hireflow.test");
        assertThat(body(sent))
                .contains("Application Received")
                .contains("Ada")
                .contains("Backend Engineer")
                .contains("Acme Labs");
    }

    @Test
    @DisplayName("Should send INTERVIEW_SCHEDULED stage email with custom message")
    void sendApplicationStageUpdateInterviewScheduled() throws Exception {
        MimeMessage message = newMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        EmailNotificationEvent event = stageEvent("INTERVIEW_SCHEDULED");
        event.setMessage("Your interview details are below.");
        event.setMeetingLink("https://meet.google.com/abc-defg-hij");
        event.setInterviewStartTime(Instant.parse("2026-05-20T14:00:00Z"));
        event.setInterviewEndTime(Instant.parse("2026-05-20T15:00:00Z"));
        event.setInterviewTimezone("America/Los_Angeles");
        event.setInterviewerEmail("maya@acme.test");

        emailService.sendApplicationStageUpdate(event);

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).isEqualTo("Interview Scheduled – Backend Engineer at Acme Labs");
        assertThat(body(sent))
                .contains("Interview Scheduled")
                .contains("Your interview details are below.")
                .contains("Wed, May 20, 2026 7:00 AM PDT")
                .contains("Wed, May 20, 2026 8:00 AM PDT")
                .contains("America/Los_Angeles")
                .contains("maya@acme.test")
                .contains("https://meet.google.com/abc-defg-hij")
                .contains("Join Interview");
    }

    @Test
    @DisplayName("Should send REJECTED stage email including reason when provided")
    void sendApplicationStageUpdateRejected() throws Exception {
        MimeMessage message = newMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        EmailNotificationEvent event = stageEvent("REJECTED");
        event.setReason("We are looking for candidates with more distributed systems experience.");

        emailService.sendApplicationStageUpdate(event);

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).isEqualTo("Application Update – Backend Engineer at Acme Labs");
        assertThat(body(sent))
                .contains("Application Update")
                .contains("distributed systems experience");
    }

    @Test
    @DisplayName("Should send HIRED stage email with welcome message")
    void sendApplicationStageUpdateHired() throws Exception {
        MimeMessage message = newMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.sendApplicationStageUpdate(stageEvent("HIRED"));

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).isEqualTo("Welcome to Acme Labs!");
        assertThat(body(sent))
                .contains("Welcome to Acme Labs")
                .contains("Ada")
                .contains("Backend Engineer");
    }

    @Test
    @DisplayName("Should send HR manager invite email with registration link")
    void sendHManagerInviteShouldBuildAndSendInviteEmail() throws Exception {
        MimeMessage message = newMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.sendHManagerInvite("newmanager@acme.test", "http://localhost:5173/accept-invite?token=abc-123");

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).isEqualTo("You've been invited to join HireFlow as a Hiring Manager");
        assertThat(addresses(sent.getFrom())).containsExactly(FROM);
        assertThat(addresses(sent.getRecipients(Message.RecipientType.TO))).containsExactly("newmanager@acme.test");
        assertThat(body(sent))
                .contains("Hiring Manager")
                .contains("72 hours")
                .contains("http://localhost:5173/accept-invite?token=abc-123");
    }

    @Test
    @DisplayName("Should send SCREENING stage email with correct subject and body")
    void sendApplicationStageUpdateScreening() throws Exception {
        MimeMessage message = newMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.sendApplicationStageUpdate(stageEvent("SCREENING"));

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).isEqualTo("Your Application Is Under Review – Backend Engineer at Acme Labs");
        assertThat(addresses(sent.getFrom())).containsExactly(FROM);
        assertThat(body(sent))
                .contains("Application Under Review")
                .contains("Ada")
                .contains("screening stage");
    }

    @Test
    @DisplayName("Should send OFFER_SENT stage email with correct subject and body")
    void sendApplicationStageUpdateOfferSent() throws Exception {
        MimeMessage message = newMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        EmailNotificationEvent event = stageEvent("OFFER_SENT");
        event.setMessage("Please review the attached offer letter.");

        emailService.sendApplicationStageUpdate(event);

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).isEqualTo("You Have Received an Offer – Backend Engineer at Acme Labs");
        assertThat(body(sent))
                .contains("You Have Received an Offer")
                .contains("Ada")
                .contains("Please review the attached offer letter.");
    }

    @Test
    @DisplayName("Should send OFFER_SENT email with no extra message block when message is blank")
    void sendApplicationStageUpdateOfferSentNoMessage() throws Exception {
        MimeMessage message = newMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.sendApplicationStageUpdate(stageEvent("OFFER_SENT"));

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).isEqualTo("You Have Received an Offer – Backend Engineer at Acme Labs");
        assertThat(body(sent)).contains("respond at your earliest convenience");
    }

    @Test
    @DisplayName("Should send generic update email for an unrecognised stage value")
    void sendApplicationStageUpdateFallsBackToGenericBodyForUnknownStage() throws Exception {
        MimeMessage message = newMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        EmailNotificationEvent event = stageEvent("UNKNOWN_STAGE");
        event.setMessage("Something changed on your application.");

        emailService.sendApplicationStageUpdate(event);

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getSubject()).isEqualTo("Application Update from Acme Labs");
        assertThat(body(sent))
                .contains("Application Update")
                .contains("Something changed on your application.");
    }

    @Test
    @DisplayName("Should skip stage update email when recipient address is absent")
    void sendApplicationStageUpdateSkipsWhenToIsNull() {
        EmailNotificationEvent event = stageEvent("SCREENING");
        event.setTo(null);

        emailService.sendApplicationStageUpdate(event);

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    @DisplayName("Should wrap messaging failures when sending stage update email")
    void sendApplicationStageUpdateShouldWrapMessagingFailure() {
        when(mailSender.createMimeMessage()).thenReturn(new FailingFromMimeMessage());

        assertThatThrownBy(() -> emailService.sendApplicationStageUpdate(stageEvent("APPLIED")))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("Failed to send stage update email")
                .hasCauseInstanceOf(MessagingException.class);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Should skip HR manager invite email when recipient is null")
    void sendHManagerInviteShouldSkipWhenToIsNull() {
        emailService.sendHManagerInvite(null, "http://localhost:5173/accept-invite?token=abc");

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    @DisplayName("Should skip HR manager invite email when invite link is null")
    void sendHManagerInviteShouldSkipWhenInviteLinkIsNull() {
        emailService.sendHManagerInvite("manager@acme.test", null);

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    @DisplayName("Should wrap messaging failures when sending welcome email")
    void sendCompanyWelcomeShouldWrapMessagingFailure() {
        when(mailSender.createMimeMessage()).thenReturn(new FailingFromMimeMessage());

        assertThatThrownBy(() -> emailService.sendCompanyWelcome("owner@acme.test", "Ada", "Acme Labs"))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("Failed to send welcome email")
                .hasCauseInstanceOf(MessagingException.class);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Should wrap messaging failures when sending invite email")
    void sendHManagerInviteShouldWrapMessagingFailure() {
        when(mailSender.createMimeMessage()).thenReturn(new FailingFromMimeMessage());

        assertThatThrownBy(() -> emailService.sendHManagerInvite("manager@acme.test", "http://localhost:5173/accept-invite?token=abc"))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("Failed to send invite email")
                .hasCauseInstanceOf(MessagingException.class);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Should wrap messaging failures when sending OTP email")
    void sendOtpShouldWrapMessagingFailure() {
        when(mailSender.createMimeMessage()).thenReturn(new FailingFromMimeMessage());

        assertThatThrownBy(() -> emailService.sendOtp("candidate@hireflow.test", "739241"))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("Failed to send verification email")
                .hasCauseInstanceOf(MessagingException.class);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private MimeMessage captureSentMessage() {
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        return messageCaptor.getValue();
    }

    private static MimeMessage newMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    private static String[] addresses(Address[] addresses) {
        return Arrays.stream(addresses)
                .map(Address::toString)
                .toArray(String[]::new);
    }

    private static String body(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < multipart.getCount(); index++) {
                builder.append(body(multipart.getBodyPart(index)));
            }
            return builder.toString();
        }
        return "";
    }

    private static EmailNotificationEvent stageEvent(String stage) {
        EmailNotificationEvent event = new EmailNotificationEvent();
        event.setTo("candidate@hireflow.test");
        event.setFirstName("Ada");
        event.setCompanyName("Acme Labs");
        event.setJobTitle("Backend Engineer");
        event.setApplicantId("applicant-1");
        event.setApplicationId("application-1");
        event.setCurrentStage(stage);
        return event;
    }

    private static final class FailingFromMimeMessage extends MimeMessage {
        private FailingFromMimeMessage() {
            super(Session.getInstance(new Properties()));
        }

        @Override
        public void setFrom(Address address) throws MessagingException {
            throw new MessagingException("sender address rejected");
        }
    }
}
