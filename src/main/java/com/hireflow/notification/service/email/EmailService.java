package com.hireflow.notification.service.email;

import com.hireflow.notification.event.EmailNotificationEvent;

public interface EmailService {
    void sendOtp(String to, String otp);

    void sendCompanyWelcome(String to, String firstName, String companyName);

    void sendApplicationStageUpdate(EmailNotificationEvent event);

    void sendHManagerInvite(String to, String inviteLink);
}
