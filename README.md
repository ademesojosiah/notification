# HireFlow — Notification Service

> Stateless notification worker for the HireFlow platform. Consumes Kafka events and delivers email and real-time SSE updates to applicants.

---

## Related Services

HireFlow is split into three services. This repo is the **Notification Service**.

| Service | Repository | Role |
|---|---|---|
| Application Service (hireflow) | https://github.com/ademesojosiah/hireflow | Owns the database; emits all domain events |
| AI Screening Service | https://github.com/ademesojosiah/ai-Screening | Stateless worker; consumes `ApplicationSubmitted`, publishes screening results |
| **Notification Service** *(this repo)* | https://github.com/ademesojosiah/notification | Stateless worker; consumes `EmailNotificationEvent`, sends email + SSE |

All three services connect to the same Confluent Cloud Kafka cluster. The application service is the only one with a database.

---

## Table of Contents

1. [Tech Stack](#tech-stack)
2. [Responsibility](#responsibility)
3. [Architecture](#architecture)
4. [Kafka Events Consumed](#kafka-events-consumed)
5. [Email Types](#email-types)
6. [SSE Streaming](#sse-streaming)
7. [Project Structure](#project-structure)
8. [Getting Started](#getting-started)
9. [Configuration](#configuration)
10. [Running the Service](#running-the-service)
11. [Testing](#testing)

---

## Tech Stack

| Layer       | Technology                      |
|-------------|---------------------------------|
| Language    | Java 21                         |
| Framework   | Spring Boot 4.0.6               |
| Messaging   | Spring Kafka against Confluent Cloud (SASL_SSL + PLAIN) |
| Email       | Spring Mail — Gmail SMTP        |
| Streaming   | Spring MVC `SseEmitter`         |
| Build       | Maven                           |
| Container   | Multi-stage Docker (Temurin 21 JRE runtime, non-root) |
| Testing     | JUnit 5, Mockito, AssertJ       |

---

## Responsibility

This service is a **stateless processing worker**. It owns no database and holds no persistent state. Its only job is:

1. Consume `EmailNotificationEvent` messages from Kafka.
2. Dispatch the correct action based on event type — send an email, push an SSE event, or both.

If the service goes down, Kafka holds the unconsumed messages. When it restarts, processing resumes from the last committed offset — no messages are lost.

---

## Architecture

```
hireflow (Application Service)
         │
         │  publishes EmailNotificationEvent to Kafka
         ▼
[hireflow.notification.email.v1]
         │
         ▼
EmailNotificationConsumer
  switch event.type
         │
    ┌────┴──────────────────────┐
    ▼                           ▼
EmailServiceImpl        NotificationStreamServiceImpl
(sends SMTP email)      (pushes SSE to connected clients)
```

The notification service is intentionally thin. No business logic lives here — the hireflow service decides what event to fire and when. This service only delivers it.

---

## Kafka Events Consumed

**Topic:** `hireflow.notification.email.v1`  
**Group:** configured via `NOTIFICATION_KAFKA_GROUP_ID`

| Event Type                 | Action                                      |
|----------------------------|---------------------------------------------|
| `OTP_VERIFICATION`         | Sends OTP verification email               |
| `COMPANY_WELCOME`          | Sends welcome email to new company owner   |
| `APPLICATION_STAGE_UPDATED`| Sends stage-change email **and** SSE push  |
| `HMANAGER_INVITE`          | Sends HR manager invitation email          |

---

## Email Types

### OTP Verification
Triggered when a new user registers or requests a new OTP. Contains a 6-digit code with a 10-minute expiry notice.

### Company Welcome
Triggered when a company is first registered. Greets the company owner and explains the platform.

### Application Stage Update
Triggered on every application stage transition. Six stage-specific HTML templates:

| Stage                  | Subject line                                     |
|------------------------|--------------------------------------------------|
| `APPLIED`              | Application Received – {job} at {company}        |
| `SCREENING`            | Your Application Is Under Review – {job}...      |
| `INTERVIEW_SCHEDULED`  | Interview Scheduled – {job} at {company}         |
| `OFFER_SENT`           | You Have Received an Offer – {job} at {company}  |
| `HIRED`                | Welcome to {company}!                            |
| `REJECTED`             | Application Update – {job} at {company}          |

Unknown stages fall back to a generic update template.

#### Interview-Scheduled extra fields

When `currentStage = INTERVIEW_SCHEDULED`, the inbound `EmailNotificationEvent` carries five additional fields that the `INTERVIEW_SCHEDULED` template renders:

| Field | Purpose |
|---|---|
| `meetingLink` | Meeting URL the applicant clicks to join — currently a stub (Meet-shaped random URL); production will plug in a real conferencing provider |
| `interviewStartTime` | UTC `Instant`; format for display in `interviewTimezone` |
| `interviewEndTime` | UTC `Instant`; format for display in `interviewTimezone` |
| `interviewTimezone` | IANA zone the applicant is expected to read the time in (e.g. `America/Los_Angeles`) |
| `interviewerEmail` | Who the applicant will meet with |

The same event fires on reschedule with the updated time details. The SSE payload includes these fields too so the applicant's UI can refresh in place.

### HR Manager Invitation
Triggered when an admin invites a hiring manager. Contains a branded "Accept Invitation" CTA button linking to `{frontendBaseUrl}/accept-invite?token={token}` and a plain-text link fallback.

---

## SSE Streaming

Applicants can subscribe to a persistent Server-Sent Events stream to receive real-time application updates in the browser without polling.

### Subscribe

```
GET /api/v1/notifications/stream/{applicantId}
```

- Returns a `text/event-stream` response.
- On connection, sends an initial `connected` event with the applicant ID.
- The connection is kept open for up to **30 minutes**.

### Events pushed over SSE

| Event name                    | Payload                    | Trigger                          |
|-------------------------------|----------------------------|----------------------------------|
| `connected`                   | `{ applicantId }`          | On subscription                  |
| `application-stage-updated`   | Full `EmailNotificationEvent` | Every `APPLICATION_STAGE_UPDATED` Kafka message |

### How it works internally

```
Subscribe:
  NotificationStreamController
    → NotificationStreamService.subscribe(applicantId)
    → Creates SseEmitter (30 min timeout)
    → Stores in ConcurrentHashMap<applicantId, List<SseEmitter>>
    → Sends "connected" event

Broadcast (when Kafka event arrives):
  EmailNotificationConsumer
    → NotificationStreamService.broadcastApplicationStageUpdate(event)
    → Looks up all emitters for event.applicantId
    → emitter.send("application-stage-updated", event)
    → Removes any emitter that throws IOException (dead connection)
```

Multiple browser tabs for the same applicant are supported — all registered emitters receive the broadcast.

---

## Project Structure

```
src/main/java/com/hireflow/notification/
├── config/
│   └── AsyncConfig.java               — @EnableAsync, thread pool executor
├── controller/
│   └── NotificationStreamController.java — GET /api/v1/notifications/stream/{id}
├── event/
│   └── EmailNotificationEvent.java    — shared event model (mirrors hireflow)
├── exception/
│   ├── EmailDeliveryException.java    — thrown on SMTP failure
│   └── GlobalExceptionHandler.java   — @RestControllerAdvice error responses
├── kafka/
│   └── EmailNotificationConsumer.java — single Kafka listener, dispatches by type
└── service/
    ├── email/
    │   ├── EmailService.java          — interface
    │   └── impl/
    │       └── EmailServiceImpl.java  — SMTP via JavaMailSender
    └── stream/
        ├── NotificationStreamService.java  — interface
        └── impl/
            └── NotificationStreamServiceImpl.java — SseEmitter registry + broadcast
```

---

## Getting Started

### Prerequisites

- Java 21
- Maven 3.9+
- Kafka broker running (default: `localhost:9092`)
- Gmail SMTP credentials
- hireflow Application Service running and publishing events

### Clone

```bash
git clone <repo-url>
cd notification
```

---

## Configuration

Copy `env.properties` from the example and fill in your values:

```properties
NOTIFICATION_SERVER_PORT=8083
KAFKA_BOOTSTRAP_SERVERS=pkc-xxxxx.region.aws.confluent.cloud:9092
KAFKA_API_KEY=your-confluent-api-key
KAFKA_API_SECRET=your-confluent-api-secret
NOTIFICATION_KAFKA_GROUP_ID=notification-service
NOTIFICATION_EMAIL_TOPIC=hireflow.notification.email.v1
GOOGLE_HOST=smtp.gmail.com
GOOGLE_PORT=587
GOOGLE_USERNAME=noreply@balancee.app
GOOGLE_PASSWORD=your-google-app-password
MAIL_FROM=noreply@balancee.app
```

Kafka connects to Confluent Cloud via SASL_SSL + PLAIN. The SMTP relay is fixed to **Gmail** (`smtp.gmail.com:587`) with STARTTLS. `GOOGLE_PASSWORD` must be a Google app password, not the normal account password. `MAIL_FROM` should normally match `GOOGLE_USERNAME` unless the Gmail account is allowed to send as another verified address.

## Docker

A multi-stage `Dockerfile` is included (Temurin 21 JDK builder → JRE runtime, non-root `spring` user). Build and run:

```bash
docker build -t hireflow-notification .
docker run --env-file env.properties -p 8083:8083 hireflow-notification
```

---

## Running the Service

```bash
mvn spring-boot:run
```

The service starts on `server.port` (default `8083`). It exposes one HTTP endpoint (`/api/v1/notifications/stream/{applicantId}`) and a Kafka consumer. No other REST endpoints exist — all work is event-driven.

---

## Testing

```bash
mvn test
```

### Test coverage

| Class | Tests |
|---|---|
| `EmailNotificationConsumerTest` | All 4 event types dispatched correctly; unknown type rejected |
| `EmailServiceImplTest` | All 6 stage emails; null-guard skips; SMTP failure wrapping for all methods |
| `NotificationStreamServiceImplTest` | Subscribe registers emitter; multiple subscribers; broadcast sends to all; dead emitter removed on IOException; unrelated applicants not touched |
| `NotificationStreamControllerTest` | Delegates to service; applicant ID passed unchanged |
| `GlobalExceptionHandlerTest` | 503 for `EmailDeliveryException`; 400 for `IllegalArgumentException`; 500 for generic errors |
