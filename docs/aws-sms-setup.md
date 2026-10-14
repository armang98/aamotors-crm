# AWS End User Messaging SMS — Setup Guide

Status: 🔑 **Implemented, awaiting credentials and sender registration.**
The adapter (`AwsSmsProvider`) is complete and unit-tested with a mocked SDK
client. Nothing is sent until the steps below are done **and** `SMS_PROVIDER=AWS`,
`SMS_ENABLED=true`.

The implementation uses AWS SDK for Java v2, service client
`PinpointSmsVoiceV2Client`, operation **`SendTextMessage`** with
`MessageType=TRANSACTIONAL` (the vehicle-ready notice is a service message, not
marketing). Reference: AWS End User Messaging SMS User Guide and the
`pinpoint-sms-voice-v2` API reference.

## 1. Account prerequisites

| Item | What to do | Why |
|---|---|---|
| **Sandbox exit** | New accounts start in the SMS sandbox: only verified destination numbers receive messages. Request production access in the console ("Account settings → Move out of sandbox"). | Customers' numbers are not pre-verified. |
| **Spending limit** | Default monthly spend limit is **1 USD**. Request an increase through AWS Support (Service limit increase → Pinpoint SMS). | A&A Motors sends short Armenian messages (2 segments each, see §5); size the limit from expected volume. |
| **Region** | Pick one region and keep `AWS_REGION` in sync. `eu-central-1` (Frankfurt) is the default here. | Origination identities are regional. |

## 2. Origination identity for Armenia (+374)

**Do not assume an unregistered sender ID or a random long code can reach Armenian handsets.** Armenia is a sender-ID country in AWS's supported-countries table; alphanumeric sender IDs are supported but may require **pre-registration** with carriers, and 10DLC/toll-free numbers do not apply outside the US.

1. In the console open *End User Messaging SMS → Sender IDs* and **request a sender ID** for country **AM** (e.g. `AAMOTORS`, max 11 alphanumeric characters). AWS files the registration; approval can take days to weeks.
2. Until approved, test in the sandbox with verified numbers only.
3. Put the resulting ARN (or the plain sender ID string, both are accepted by `OriginationIdentity`) in `AWS_SMS_ORIGINATION_IDENTITY`.
4. Optionally create a **phone pool** and use the pool ARN as origination identity for failover.

Carrier behaviour in Armenia can change; verify with a real test message to each major carrier (Viva-MTS, Team/Beeline, Ucom) before go-live and record the result in `known-limitations.md`.

## 3. IAM

Credentials come from the **default provider chain** (`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` env vars locally, an instance/task role in production). The role needs at minimum:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": ["sms-voice:SendTextMessage"], "Resource": "*" }
  ]
}
```
Add `sms-voice:DescribeSenderIds`, `sms-voice:DescribeConfigurationSets` if you want the settings page to validate configuration (not required).

## 4. Delivery tracking (event destination)

1. Create a **configuration set** (e.g. `crm-sms-events`) and set `AWS_SMS_CONFIGURATION_SET`.
2. Add an **event destination** of type **SNS** for the `TEXT_*` event types (at least `TEXT_DELIVERED`, `TEXT_SUCCESSFUL`, all `TEXT_*` failure types).
3. Subscribe the SNS topic over **HTTPS** to `https://<your-domain>/api/webhooks/aws-sms-events`.
   The endpoint verifies every SNS signature (certificate must come from `sns.<region>.amazonaws.com`), confirms the subscription automatically, and maps:
   * `TEXT_DELIVERED`, `TEXT_SUCCESSFUL` → `DELIVERED`
   * `TEXT_BLOCKED`, `TEXT_CARRIER_BLOCKED`, `TEXT_CARRIER_UNREACHABLE`, `TEXT_INVALID`, `TEXT_INVALID_MESSAGE`, `TEXT_SPAM`, `TEXT_TTL_EXPIRED`, `TEXT_UNREACHABLE` → `FAILED`
   * other `TEXT_*` → stays `SENT`
4. Set `AWS_SMS_SNS_TOPIC_ARN` (informational; used in docs/alerts).

Without an event destination, messages stay in **SENT** ("accepted by provider"), never **DELIVERED**. The UI labels these differently on purpose.

## 5. Costs

* Armenia outbound SMS price is per segment; check the current AWS SMS pricing page for AM.
* The Armenian template renders to ~120–140 characters in UCS-2 → **2 segments** per message; the English template is 1 segment. The preview endpoint shows the exact count.
* Budget = segments × messages × unit price; set the spending limit above that with headroom.

## 6. Environment variables

```
SMS_PROVIDER=AWS
SMS_ENABLED=true
AWS_REGION=eu-central-1
AWS_SMS_ORIGINATION_IDENTITY=AAMOTORS          # approved sender ID or ARN
AWS_SMS_CONFIGURATION_SET=crm-sms-events
AWS_SMS_SNS_TOPIC_ARN=arn:aws:sns:eu-central-1:<acct>:crm-sms-events
SMS_MAX_ATTEMPTS=5  SMS_BASE_BACKOFF=10s  SMS_MAX_BACKOFF=10m  SMS_RATE_PER_SECOND=5
```

## 7. Behaviour summary

* Idempotency: one automatic notification per order (partial unique index) and a per-attempt idempotency key passed in the AWS `Context` map.
* Retries: `ThrottlingException`, `ServiceQuotaExceededException`, `InternalServerException`, HTTP 5xx → exponential back-off via the RabbitMQ TTL retry queue, up to `SMS_MAX_ATTEMPTS`.
* Permanent: `ValidationException`, `ResourceNotFoundException`, `AccessDeniedException`, `ConflictException`, HTTP 4xx → `FAILED`.
* Timeouts / client errors → `UNCERTAIN`, surfaced for manual reconciliation; never auto-resent.
* Logs mask phone numbers (`+374*****881`) and never include message bodies.
