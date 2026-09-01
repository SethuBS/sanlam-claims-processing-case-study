# End-to-End Claims Processing Solution

[![CI](https://github.com/SethuBS/sanlam-claims-processing-case-study/actions/workflows/ci.yml/badge.svg?branch=development)](https://github.com/SethuBS/sanlam-claims-processing-case-study/actions/workflows/ci.yml?query=branch%3Adevelopment)

A Java 21 and AWS-oriented implementation of the Sanlam Senior Java Developer case study.

The solution is intentionally small enough to run locally, but it demonstrates the engineering decisions I would carry into a production design: clear ownership, asynchronous processing, idempotency, transactional outbox, safe retries, payment reconciliation, audit foundations and SLA-aware processing.

## Implementation status

The repository is an executable prototype of the critical workflow and reliability decisions. The AWS services described in the design document are the intended production architecture, not resources deployed by this repository.

| Capability | Status |
|---|---|
| Claim intake, validation, idempotency and outbox | Implemented |
| Client, policy and payment stubs | Implemented |
| Signed callbacks, replay protection and payment reconciliation | Implemented |
| AWS Step Functions, SQS, EventBridge and Cognito | Proposed production architecture |
| Complete enterprise audit timeline | Future work |
| Claims UI | Outside prototype scope |

## Problem

A claimant submits a claim through a channel system. The claim must be checked against the Client Registry and Policy Manager, reviewed by an analyst when required, and only then sent to the Payment System.

The key challenge is reliability: once the Claims Platform accepts a claim, a downstream failure must not lose the claim or create a duplicate payment.

## Proposed flow

```text
Channel
   |
   v
Claims API ----> PostgreSQL
   |
   v
Outbox
   |
   v
Workflow / application orchestration
   |
   +----> Client Registry
   |
   +----> Policy Manager
   |
   +----> Analyst review when required
   |
   +----> Payment System
                |
                v
        Payment status callback
```

## Key decisions

- Java 21 and Spring Boot 3.x.
- REST at system boundaries with versioned APIs.
- `202 Accepted` for claim intake so the web channel does not wait for downstream systems.
- Idempotency keys for claim submission and payment creation.
- PostgreSQL as the transactional source for claim workflow state.
- Transactional outbox so a committed claim cannot lose its follow-up event.
- Explicit claim state transitions with optimistic locking.
- External systems own their own data and decisions.
- Payment timeout is treated as an unknown result, not an automatic failure; reconcile before retrying.
- Urgent claims can be routed to a priority queue in the AWS deployment.

## Repository structure

```text
sanlam-claims-processing-case-study/
├── claims-service/
├── mock-client-registry/
├── mock-policy-manager/
├── mock-payment-system/
├── docs/
├── examples/
├── .github/workflows/
├── build.gradle
├── settings.gradle
└── docker-compose.yml
```

## Run locally

Java 21 is required. The repository includes the Gradle wrapper configuration; if your environment does not have the wrapper script yet, run `gradle wrapper --gradle-version 8.14.3` once, then use `./gradlew`.

```bash
./gradlew clean test
```

Start PostgreSQL and the mock systems:

```bash
docker compose up -d postgres mock-client-registry mock-policy-manager mock-payment-system
```

Run the Claims service:

```bash
./gradlew :claims-service:bootRun
```

Submit a claim:

```bash
curl -X POST http://localhost:8080/api/v1/claims \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-claim-001' \
  --data @examples/claim-request.json
```

Then query the returned claim ID:

```bash
curl http://localhost:8080/api/v1/claims/{claimId}
```

## Example API

### Submit claim

`POST /api/v1/claims`

Required header:

```text
Idempotency-Key: demo-claim-001
```

Response:

```json
{
  "claimId": "...",
  "status": "RECEIVED",
  "priority": "CRITICAL"
}
```

### Get claim

`GET /api/v1/claims/{claimId}`

### Submit analyst decision

`POST /api/v1/claims/{claimId}/decisions`

### Payment status callback

`POST /internal/v1/payment-status-events`

The callback requires `X-Callback-Timestamp` and `X-Callback-Signature` headers. The signature is `v1=` followed by the hexadecimal HMAC-SHA256 of `<timestamp>.<raw-json-body>`, using `PAYMENT_CALLBACK_SECRET`. Timestamps older than five minutes are rejected and processed event IDs are stored to prevent replay.

## Test scenarios

Run the complete automated suite (Docker is required for the Testcontainers layers):

```bash
./gradlew clean test build
```

On Windows:

```powershell
.\gradlew.bat clean test build
```

Java source uses four-space indentation and next-line (Allman) braces. Spotless applies the shared Eclipse formatter profile to every module, and `check` fails when Java source is not formatted.

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck
```

Run the Compose-based end-to-end scenarios:

```powershell
.\scripts\e2e.ps1
```

Use `-KeepRunning` to leave the services available on ports 8080-8083 and PostgreSQL on 5432 after the checks.

The mocks support these behaviours through configuration:

- `SUCCESS`
- `BUSINESS_REJECTION`
- `TIMEOUT`
- `TEMPORARY_FAILURE`
- `DUPLICATE_CALLBACK`
- `OUT_OF_ORDER_CALLBACK`
- `AMBIGUOUS_PAYMENT_RESPONSE`

The important tests are not only happy-path HTTP tests. They include duplicate submissions, concurrent updates, payment timeouts, reconciliation and downstream outages.

Validate the published API contracts:

```bash
./gradlew :claims-service:test --tests '*OpenApiContractTest'
```

Run the blocking dependency vulnerability scan:

```bash
./gradlew dependencyCheckAggregate
```

The scan fails for vulnerabilities with CVSS 7.0 or higher. Supplying an `NVD_API_KEY` is recommended to avoid public API rate limits.

## Production AWS mapping

| Concern | AWS target |
|---|---|
| Public API | API Gateway + WAF |
| Application | ECS Fargate |
| Workflow | Step Functions Standard |
| Work queues | SQS + DLQs |
| Event routing | EventBridge |
| Database | Aurora PostgreSQL |
| Secrets | Secrets Manager |
| Encryption | KMS |
| Observability | CloudWatch + OpenTelemetry |

The local application deliberately keeps AWS concerns behind interfaces. This makes the code easier to test and avoids pretending that a case-study repository is a full production environment.

## Documentation

See [`docs/claims-processing-solution.pdf`](docs/claims-processing-solution.pdf) for the full architecture and design case study. This is the canonical submission document; use the same file when sharing the case study outside GitHub.

### Regenerating the case-study PDF

The PDF is generated from the versioned layout and vector artwork sources in `docs/source`. From the repository root, run:

```powershell
python docs/source/generate_pdf.py docs/source/claims-processing-layout.json docs/source/claims-processing-artwork.bin docs/claims-processing-solution.pdf
```

The generator rebuilds one searchable text layer, adds the implementation-status table and renders the page-22 samples as illustrative production-oriented code.

The executable Claims API contract is published at [`docs/openapi/claims-api.yaml`](docs/openapi/claims-api.yaml).

## AI use

AI was used as a review and drafting assistant to challenge failure scenarios, compare design options and improve the structure of the solution. The final technical decisions, code and explanation were reviewed and remain the responsibility of the author.
