# Testing Guide

## Backend

```bash
cd backend
mvn verify            # unit tests (surefire, *Test) + integration tests (failsafe, *IT)
mvn test              # unit tests only
mvn verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=AuthIT   # one IT class
```

Integration tests use **Testcontainers** (`postgres:16-alpine`). Docker must be
running. One container is started per JVM (singleton pattern in
`AbstractIntegrationTest`) and the real Flyway migrations run against it, so the
tests exercise the production schema. The test profile
(`src/test/resources/application-test.yml`) provides bootstrap admin
credentials and a 3-failure login lock so the rate limiter can be tested quickly.

### Phase 1 coverage

| Spec scenario | Test | Status |
|---|---|---|
| 1 ADMIN login | `AuthIT.loginSucceeds`, `csrfCookieIssued`, `csrfIsEnforced`, `badCredentialsThenRateLimited`, `logout`, `unauthenticatedIsRejected` | ✅ |
| Admin bootstrap from env | `AuthIT.adminBootstrappedFromEnvironment` | ✅ |
| Language preference | `AuthIT.languagePreferenceStored` | ✅ |
| Password change | `AuthIT.changePassword` | ✅ |
| Business settings seeded (A&A Motors) | `SettingsIT.seededSettings` | ✅ |
| Settings update + E.164 + audit masking | `SettingsIT.updateSettings` | ✅ |
| Settings validation | `SettingsIT.validation` | ✅ |
| Logo upload / serve / reject / remove | `SettingsIT.logoLifecycle` | ✅ |
| Health endpoint public | `SettingsIT.health` | ✅ |
| 3 Phone validation (E.164, +374) | `PhoneNumberNormalizerTest`, `VehicleIT.validation` | ✅ |
| Login rate limiter | `LoginRateLimiterTest` | ✅ |

### Phase 2 coverage

| Spec scenario | Test | Status |
|---|---|---|
| 2 Vehicle creation (with new customer, VIN/plate normalisation) | `VehicleIT.createVehicleWithCustomerAndInitialService` | ✅ |
| 3 Phone and VIN validation (invalid VIN, I/O/Q, duplicate VIN, invalid phone, negative mileage) | `VehicleIT.validation` | ✅ |
| 4 Service record creation | `VehicleIT.serviceHistory` | ✅ |
| 5 All six filter fields persisted and returned | `VehicleIT.createVehicleWithCustomerAndInitialService` | ✅ |
| 6 Service history retrieval (newest first, edit audited, delete audited) | `VehicleIT.serviceHistory` | ✅ |
| 7 Latest service record selection | `VehicleIT.serviceHistory` | ✅ |
| Vehicle search by phone / plate / VIN / brand / model | `VehicleIT.search` | ✅ |
| Maintenance reminders (by km, by date, never without data) | `VehicleIT.reminders` | ✅ |
| Vehicle delete cascades history; customer with vehicles protected | `VehicleIT.deleteVehicle` | ✅ |
| Customer create / duplicate phone / consents / search / delete | `CustomerIT.lifecycle` | ✅ |

### Phase 3 coverage

| Spec scenario | Test | Status |
|---|---|---|
| 8 Referral creation | `ReferralIT.lifecycle` | ✅ |
| 9 Fixed 10 % discount enforcement (constant + `ck_referrals_discount_fixed` + `ck_referral_rewards_discount_fixed`) | `ReferralIT.tenPercentIsFixed`, `ReferralIT.lifecycle` | ✅ |
| 10 Duplicate referral prevention (self-referral, same invited customer twice, DB check `ck_referrals_no_self`, unique `uq_referrals_invited`) | `ReferralIT.fraudPrevention`, `ReferralIT.tenPercentIsFixed` | ✅ |
| 11 One-time reward redemption (second redeem → 409, confirm twice → 409) | `ReferralIT.lifecycle` | ✅ |
| Cancel voids an unused reward | `ReferralIT.cancelVoidsReward` | ✅ |
| Summary + monthly analytics | `ReferralIT.lifecycle` | ✅ |

### Phase 4 coverage

Integration tests now also start a **RabbitMQ Testcontainer**; the vehicle-ready flow is exercised end to end (status change → outbox row in the same transaction → relay → queue → consumer → mock provider → simulated delivery receipt).

| Spec scenario | Test | Status |
|---|---|---|
| 12 Service order creation + status machine | `VehicleReadySmsIT.workflow` | ✅ |
| 13 Vehicle ready transition (outbox row created in the same transaction) | `VehicleReadySmsIT.markReadySendsSingleSms` | ✅ |
| 14 Automatic SMS notification (hy template, SENT → DELIVERED, exactly one automatic SMS even when status bounces) | `VehicleReadySmsIT.markReadySendsSingleSms` | ✅ |
| 15 Manual SMS resend (confirmation required, separate MANUAL notification) | `VehicleReadySmsIT.markReadySendsSingleSms` | ✅ |
| 16 AWS SMS adapter with mocked AWS responses (request shape, retry/permanent/uncertain mapping, disabled guard) | `AwsSmsProviderTest` | ✅ |
| Retry with back-off, permanent failure, uncertain outcome never auto-retried | `VehicleReadySmsIT.failureModes` | ✅ |
| SMS opt-out → SKIPPED, manual resend refused | `VehicleReadySmsIT.optOutSkipped` | ✅ |
| Template rendering + segment estimate (UCS-2 for Armenian) | `SmsTemplateRendererTest` | ✅ |
| SNS signature verification rejects foreign certificate hosts; canonical string | `SnsMessageVerifierTest` | ✅ |
| Referral ↔ paid service order linkage (unpaid / wrong customer refused) | `ReferralIT.lifecycle` | ✅ |

### Phase 5 coverage

| Spec scenario | Test | Status |
|---|---|---|
| 17 Marketing consent validation (no consent → no channel; global opt-out → OPTED_OUT; send refused) | `AdvertisingIT.consentValidation` | ✅ |
| 18 WhatsApp mock delivery (image + latest service history with six filter fields, omitted when empty) | `AdvertisingIT.whatsappDeliveryWithHistory` | ✅ |
| 19/20 Viber Business fallback (NOT_CONFIGURED → skipped, chain continues) | `AdvertisingIT.telegramFallback` | ✅ |
| 21 Telegram eligibility (consent + verified chat link via link code / QR / mock start) | `AdvertisingIT.telegramFallback` | ✅ |
| 22 Viber Bot subscriber eligibility (subscribed + linked user id, never phone; unsubscribe revokes) | `AdvertisingIT.viberBotEligibility` | ✅ |
| 23 Duplicate delivery prevention (single attempt per channel, redelivery ignored) | `AdvertisingIT.whatsappDeliveryWithHistory` | ✅ |
| Uncertain outcome stops fallback; manual reconciliation | `AdvertisingIT.uncertainStopsFallback` | ✅ |
| Vehicle selection required for multi-vehicle customers | `AdvertisingIT.vehicleSelectionRequired` | ✅ |
| QR SVG generation for Telegram onboarding | `QrSvgTest` | ✅ |

### Phase 6 coverage

| Spec scenario | Test | Status |
|---|---|---|
| Campaign creation, segment preview (brand + oil type filters), edit, duplicate, delete draft | `CampaignIT.creationAndSegment` | ✅ |
| Schedule (past refused, recipients materialised with eligibility reasons), cancel, not-editable guards | `CampaignIT.creationAndSegment` | ✅ |
| 24 Scheduled campaign execution (scheduler starts at the scheduled instant, batches dispatch through the fallback orchestrator, stats, results, actual cost null when unknown) | `CampaignIT.scheduledExecution` | ✅ |
| Start now, pause, resume, cancel transitions | `CampaignIT.pauseResumeCancel` | ✅ |

### Phase 7 coverage

| Scenario | Test | Status |
|---|---|---|
| WhatsApp Cloud API request shape (template, language, header image, flattened body parameter, bearer token, phone without "+") | `RealChannelAdaptersTest.whatsAppSendsApprovedMediaTemplateWithFlattenedBodyParameter` | ✅ |
| WhatsApp eligibility (consent, template required), text template without public URL | `…whatsAppUsesTextTemplateWithoutImageAndRequiresTemplateAndConsent` | ✅ |
| WhatsApp error classes → RETRY / FAILED / UNCERTAIN | `…whatsAppMapsRateLimitToRetryRejectionToFailedAndTimeoutToUncertain` | ✅ |
| Telegram sendPhoto/sendMessage to verified chat, blocked-bot handling | `…telegramSendsPhotoWithCaptionToVerifiedChatAndMarksBlockedUsers` | ✅ |
| Telegram ineligible without verified chat | `…telegramIsIneligibleWithoutVerifiedChat` | ✅ |
| Viber Bot send_message to user id (never phone), auth header, status 0/6/12 mapping | `…viberBotSendsToSubscribedUserIdNeverToPhoneAndHandlesStatusCodes` | ✅ |
| Viber Bot ineligible without subscriber | `…viberBotIsIneligibleWithoutSubscriber` | ✅ |

Not covered by automated tests: live provider accounts (see `known-limitations.md`).

Last run (2026-10-12): unit 37/37, integration 38/38 (`AuthIT` 9, `SettingsIT` 5, `VehicleIT` 6, `CustomerIT` 1, `ReferralIT` 4, `VehicleReadySmsIT` 4, `AdvertisingIT` 6, `CampaignIT` 3), `BUILD SUCCESS`.

## Frontend

```bash
cd frontend
npm ci
npm test              # vitest + testing-library
npm run build         # tsc -b && vite build
```

| Spec scenario | Test | Status |
|---|---|---|
| 25 Armenian and English UI rendering | `frontend/src/test/login-page.test.tsx` renders the login page in `hy` (asserts `<html lang="hy-AM">`, Armenian heading, labels, button) and in `en` | ✅ 2 tests passing (Vitest 3, jsdom) |
| Campaigns page statuses (hy/en) and progress | `frontend/src/test/campaigns-page.test.tsx` | ✅ 2 tests |

Frontend last run (2026-10-12): 7 files, 21 tests passed; `tsc -b` clean; `vite build` OK.
| 2/5 Vehicle form: five sections, six filter fields (hy + en), VIN rejection | `frontend/src/test/vehicle-form.test.tsx` | ✅ 3 tests |
| 6 Service history timeline newest first, filters rendered, empty state | `frontend/src/test/service-history.test.tsx` | ✅ 2 tests |
| 8/9 Referrals page: summary with fixed 10 % card (hy + en), status/reward badges, self-referral blocked in the add form | `frontend/src/test/referrals-page.test.tsx` | ✅ 4 tests |
| 13/14/15 UI: orders table (statuses, SENT ≠ DELIVERED labels in hy/en), SMS preview dialog (text, recipient, segments), manual resend only after confirmation (`{confirm:true}`) | `frontend/src/test/vehicle-ready-page.test.tsx` | ✅ 4 tests passing |
| Quick advertising composer (hy/en, Send disabled before preview), channel eligibility order + first channel highlight, confirmation before `POST /send` with `confirm:true` | `frontend/src/test/quick-advertising.test.tsx` | ✅ 4 tests |

## CI

`.github/workflows/ci.yml` runs backend `mvn verify` (Docker is available on
`ubuntu-latest`, so Testcontainers work), frontend `npm test` + `npm run build`,
and builds both Docker images.

## Later phases
Scenarios 2, 4–24 from the specification are added phase by phase; this file is
updated with the mapping each time.
