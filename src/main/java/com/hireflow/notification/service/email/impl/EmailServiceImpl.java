package com.hireflow.notification.service.email.impl;

import com.hireflow.notification.event.EmailNotificationEvent;
import com.hireflow.notification.exception.EmailDeliveryException;
import com.hireflow.notification.service.email.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final DateTimeFormatter INTERVIEW_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, MMM d, yyyy h:mm a z");

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Override
    public void sendOtp(String to, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("Your HireFlow Verification Code");
            helper.setText(buildOtpEmailBody(otp), true);
            mailSender.send(message);
            log.info("OTP email sent to {}", to);
        } catch (MessagingException ex) {
            log.error("Failed to send OTP email to {}: {}", to, ex.getMessage());
            throw new EmailDeliveryException("Failed to send verification email", ex);
        }
    }

    @Override
    public void sendCompanyWelcome(String to, String firstName, String companyName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("Welcome to HireFlow, " + companyName + "!");
            helper.setText(buildWelcomeEmailBody(firstName, companyName), true);
            mailSender.send(message);
            log.info("Welcome email sent to {}", to);
        } catch (MessagingException ex) {
            log.error("Failed to send welcome email to {}: {}", to, ex.getMessage());
            throw new EmailDeliveryException("Failed to send welcome email", ex);
        }
    }

    @Override
    public void sendApplicationStageUpdate(EmailNotificationEvent event) {
        if (event.getTo() == null || event.getCurrentStage() == null) {
            log.warn("Skipping stage update email — missing recipient or stage for application {}", event.getApplicationId());
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(event.getTo());
            helper.setSubject(stageSubject(event));
            helper.setText(stageEmailBody(event), true);
            mailSender.send(message);
            log.info("Stage update email sent to {} for stage {}", event.getTo(), event.getCurrentStage());
        } catch (MessagingException ex) {
            log.error("Failed to send stage update email to {}: {}", event.getTo(), ex.getMessage());
            throw new EmailDeliveryException("Failed to send stage update email", ex);
        }
    }

    private String stageSubject(EmailNotificationEvent event) {
        String job = event.getJobTitle() != null ? event.getJobTitle() : "the position";
        String company = event.getCompanyName() != null ? event.getCompanyName() : "the company";
        return switch (event.getCurrentStage()) {
            case "APPLIED" -> "Application Received – " + job + " at " + company;
            case "SCREENING" -> "Your Application Is Under Review – " + job + " at " + company;
            case "INTERVIEW_SCHEDULED" -> "Interview Scheduled – " + job + " at " + company;
            case "OFFER_SENT" -> "You Have Received an Offer – " + job + " at " + company;
            case "HIRED" -> "Welcome to " + company + "!";
            case "REJECTED" -> "Application Update – " + job + " at " + company;
            default -> "Application Update from " + company;
        };
    }

    private String stageEmailBody(EmailNotificationEvent event) {
        String name = event.getFirstName() != null ? event.getFirstName() : "Applicant";
        String job = event.getJobTitle() != null ? event.getJobTitle() : "the position";
        String company = event.getCompanyName() != null ? event.getCompanyName() : "the company";
        return switch (event.getCurrentStage()) {
            case "APPLIED" -> buildAppliedBody(name, job, company);
            case "SCREENING" -> buildScreeningBody(name, job, company);
            case "INTERVIEW_SCHEDULED" -> buildInterviewScheduledBody(name, job, company, event);
            case "OFFER_SENT" -> buildOfferSentBody(name, job, company, event.getMessage());
            case "HIRED" -> buildHiredBody(name, job, company);
            case "REJECTED" -> buildRejectedBody(name, job, company, event.getReason());
            default -> buildGenericUpdateBody(name, company, event.getMessage());
        };
    }

    private String buildAppliedBody(String name, String job, String company) {
        return """
                <html>
                  <body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px; color: #111827;">
                    <h2 style="color: #2563EB;">Application Received</h2>
                    <p>Hi %s,</p>
                    <p>We have received your application for <strong>%s</strong> at <strong>%s</strong>.</p>
                    <p>Our team will review your profile and get back to you shortly. You can track your application status through HireFlow at any time.</p>
                    <p style="color: #6B7280; font-size: 12px;">This is an automated notification from HireFlow. Please do not reply to this email.</p>
                  </body>
                </html>
                """.formatted(name, job, company);
    }

    private String buildScreeningBody(String name, String job, String company) {
        return """
                <html>
                  <body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px; color: #111827;">
                    <h2 style="color: #2563EB;">Application Under Review</h2>
                    <p>Hi %s,</p>
                    <p>Your application for <strong>%s</strong> at <strong>%s</strong> has moved to the screening stage.</p>
                    <p>Our team is carefully reviewing your profile. You will be notified as soon as a decision is made.</p>
                    <p style="color: #6B7280; font-size: 12px;">This is an automated notification from HireFlow. Please do not reply to this email.</p>
                  </body>
                </html>
                """.formatted(name, job, company);
    }

    private String buildInterviewScheduledBody(String name, String job, String company, EmailNotificationEvent event) {
        String message = event.getMessage();
        String extra = message != null && !message.isBlank()
                ? "<p>" + message + "</p>"
                : "<p>The hiring team will be in touch with further details on timing and format.</p>";
        String details = interviewDetails(event);
        return """
                <html>
                  <body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px; color: #111827;">
                    <h2 style="color: #16A34A;">Interview Scheduled</h2>
                    <p>Hi %s,</p>
                    <p>Congratulations! You have been selected for an interview for <strong>%s</strong> at <strong>%s</strong>.</p>
                    %s
                    %s
                    <p style="color: #6B7280; font-size: 12px;">This is an automated notification from HireFlow. Please do not reply to this email.</p>
                  </body>
                </html>
                """.formatted(name, job, company, extra, details);
    }

    private String interviewDetails(EmailNotificationEvent event) {
        StringBuilder builder = new StringBuilder();
        if (event.getInterviewStartTime() != null) {
            builder.append("<p><strong>When:</strong> ")
                    .append(formatInterviewTime(event.getInterviewStartTime(), event.getInterviewTimezone()));
            if (event.getInterviewEndTime() != null) {
                builder.append(" - ").append(formatInterviewTime(event.getInterviewEndTime(), event.getInterviewTimezone()));
            }
            builder.append("</p>");
        }
        if (event.getInterviewTimezone() != null && !event.getInterviewTimezone().isBlank()) {
            builder.append("<p><strong>Timezone:</strong> ").append(event.getInterviewTimezone()).append("</p>");
        }
        if (event.getInterviewerEmail() != null && !event.getInterviewerEmail().isBlank()) {
            builder.append("<p><strong>Interviewer:</strong> ").append(event.getInterviewerEmail()).append("</p>");
        }
        if (event.getMeetingLink() != null && !event.getMeetingLink().isBlank()) {
            builder.append("<p><a href=\"").append(event.getMeetingLink()).append("\" ")
                    .append("style=\"background-color: #16A34A; color: #ffffff; padding: 10px 18px; text-decoration: none; border-radius: 6px; font-weight: bold;\">")
                    .append("Join Interview</a></p>")
                    .append("<p style=\"word-break: break-all; color: #2563EB;\">")
                    .append(event.getMeetingLink())
                    .append("</p>");
        }
        return builder.toString();
    }

    private String formatInterviewTime(Instant instant, String timezone) {
        ZoneId zone = ZoneId.of("UTC");
        if (timezone != null && !timezone.isBlank()) {
            try {
                zone = ZoneId.of(timezone);
            } catch (DateTimeException ex) {
                log.warn("Invalid interview timezone '{}', falling back to UTC", timezone);
            }
        }
        return INTERVIEW_TIME_FORMATTER.withZone(zone).format(instant);
    }

    private String buildOfferSentBody(String name, String job, String company, String message) {
        String extra = message != null && !message.isBlank()
                ? "<p>" + message + "</p>"
                : "";
        return """
                <html>
                  <body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px; color: #111827;">
                    <h2 style="color: #16A34A;">You Have Received an Offer</h2>
                    <p>Hi %s,</p>
                    <p>We are delighted to inform you that <strong>%s</strong> has extended an offer for the <strong>%s</strong> position.</p>
                    %s
                    <p>Please review the offer details and respond at your earliest convenience.</p>
                    <p style="color: #6B7280; font-size: 12px;">This is an automated notification from HireFlow. Please do not reply to this email.</p>
                  </body>
                </html>
                """.formatted(name, company, job, extra);
    }

    private String buildHiredBody(String name, String job, String company) {
        return """
                <html>
                  <body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px; color: #111827;">
                    <h2 style="color: #16A34A;">Welcome to %s!</h2>
                    <p>Hi %s,</p>
                    <p>We are thrilled to confirm that you have been hired as <strong>%s</strong> at <strong>%s</strong>.</p>
                    <p>The team is excited to have you on board. You will receive further onboarding details shortly.</p>
                    <p style="color: #6B7280; font-size: 12px;">This is an automated notification from HireFlow. Please do not reply to this email.</p>
                  </body>
                </html>
                """.formatted(company, name, job, company);
    }

    private String buildRejectedBody(String name, String job, String company, String reason) {
        String feedback = reason != null && !reason.isBlank()
                ? "<p><strong>Feedback:</strong> " + reason + "</p>"
                : "";
        return """
                <html>
                  <body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px; color: #111827;">
                    <h2 style="color: #6B7280;">Application Update</h2>
                    <p>Hi %s,</p>
                    <p>Thank you for your interest in the <strong>%s</strong> role at <strong>%s</strong> and for the time you invested in your application.</p>
                    <p>After careful consideration, we have decided to move forward with other candidates at this time.</p>
                    %s
                    <p>We encourage you to apply for future opportunities that match your profile and wish you the best in your search.</p>
                    <p style="color: #6B7280; font-size: 12px;">This is an automated notification from HireFlow. Please do not reply to this email.</p>
                  </body>
                </html>
                """.formatted(name, job, company, feedback);
    }

    private String buildGenericUpdateBody(String name, String company, String message) {
        String detail = message != null && !message.isBlank() ? "<p>" + message + "</p>" : "";
        return """
                <html>
                  <body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px; color: #111827;">
                    <h2 style="color: #2563EB;">Application Update</h2>
                    <p>Hi %s,</p>
                    <p>There has been an update to your application at <strong>%s</strong>.</p>
                    %s
                    <p style="color: #6B7280; font-size: 12px;">This is an automated notification from HireFlow. Please do not reply to this email.</p>
                  </body>
                </html>
                """.formatted(name, company, detail);
    }

    @Override
    public void sendHManagerInvite(String to, String inviteLink) {
        if (to == null || inviteLink == null) {
            log.warn("Skipping HR manager invite email — missing recipient or invite link");
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("You've been invited to join HireFlow as a Hiring Manager");
            helper.setText(buildHManagerInviteBody(to, inviteLink), true);
            mailSender.send(message);
            log.info("HR manager invite email sent to {}", to);
        } catch (MessagingException ex) {
            log.error("Failed to send HR manager invite email to {}: {}", to, ex.getMessage());
            throw new EmailDeliveryException("Failed to send invite email", ex);
        }
    }

    private String buildHManagerInviteBody(String to, String inviteLink) {
        return """
                <html>
                  <body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px; color: #111827;">
                    <h2 style="color: #2563EB;">You've Been Invited to HireFlow</h2>
                    <p>Hi,</p>
                    <p>You have been invited to join <strong>HireFlow</strong> as a <strong>Hiring Manager</strong>.</p>
                    <p>Click the button below to set up your account. This link expires in <strong>72 hours</strong>.</p>
                    <div style="margin: 32px 0;">
                      <a href="%s"
                         style="background-color: #2563EB; color: #ffffff; padding: 12px 24px;
                                text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 15px;">
                        Accept Invitation
                      </a>
                    </div>
                    <p>Or copy and paste this link into your browser:</p>
                    <p style="word-break: break-all; color: #2563EB;">%s</p>
                    <p style="color: #6B7280; font-size: 12px;">
                      If you did not expect this invitation, you can safely ignore this email.
                    </p>
                  </body>
                </html>
                """.formatted(inviteLink, inviteLink);
    }

    private String buildWelcomeEmailBody(String firstName, String companyName) {
        return """
                <html>
                  <body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px;">
                    <h2 style="color: #2563EB;">Welcome to HireFlow, %s!</h2>
                    <p>Your company <strong>%s</strong> has been successfully registered on HireFlow.</p>
                    <p>You can now create job listings, manage candidates, and run AI-powered screening.</p>
                    <p style="color: #6B7280; font-size: 12px;">
                      Need help? Reply to this email and our team will assist you.
                    </p>
                  </body>
                </html>
                """.formatted(firstName, companyName);
    }

    private String buildOtpEmailBody(String otp) {
        return """
                <html>
                  <body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px;">
                    <h2 style="color: #2563EB;">HireFlow Email Verification</h2>
                    <p>Your one-time verification code is:</p>
                    <div style="font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #1E40AF; padding: 16px 0;">
                      %s
                    </div>
                    <p>This code expires in <strong>10 minutes</strong>.</p>
                    <p style="color: #6B7280; font-size: 12px;">
                      If you did not create a HireFlow account, you can safely ignore this email.
                    </p>
                  </body>
                </html>
                """.formatted(otp);
    }
}
