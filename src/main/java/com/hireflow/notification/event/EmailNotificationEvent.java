package com.hireflow.notification.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationEvent {

    public static final String OTP_VERIFICATION = "OTP_VERIFICATION";
    public static final String COMPANY_WELCOME = "COMPANY_WELCOME";
    public static final String APPLICATION_STAGE_UPDATED = "APPLICATION_STAGE_UPDATED";
    public static final String HMANAGER_INVITE = "HMANAGER_INVITE";

    private String type;
    private String to;
    private String otp;
    private String firstName;
    private String companyName;
    private String applicationId;
    private String applicantId;
    private String jobListingId;
    private String jobTitle;
    private String companyId;
    private String previousStage;
    private String currentStage;
    private String reason;
    private String actor;
    private String message;
    private String inviteLink;
    private String meetingLink;
    private Instant interviewStartTime;
    private Instant interviewEndTime;
    private String interviewTimezone;
    private String interviewerEmail;

    public EmailNotificationEvent(String type, String to, String otp, String firstName, String companyName) {
        this.type = type;
        this.to = to;
        this.otp = otp;
        this.firstName = firstName;
        this.companyName = companyName;
    }
}
