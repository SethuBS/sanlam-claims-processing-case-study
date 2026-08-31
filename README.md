# End-to-End Claims Processing Solution

A Java 17 and AWS-oriented implementation of the Sanlam Senior Java Developer case study.

The solution is intentionally small enough to run locally, but it demonstrates the engineering decisions I would carry into a production design: clear ownership, asynchronous processing, idempotency, transactional outbox, safe retries, payment reconciliation, auditability and SLA-aware processing.

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

- Java 17 and Spring Boot 3.x.
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

The repository includes a Gradle wrapper. Java 17 is required.

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

## Test scenarios

The mocks support these behaviours through configuration:

- `SUCCESS`
- `BUSINESS_REJECTION`
- `TIMEOUT`
- `TEMPORARY_FAILURE`
- `DUPLICATE_CALLBACK`
- `OUT_OF_ORDER_CALLBACK`
- `AMBIGUOUS_PAYMENT_RESPONSE`

The important tests are not only happy-path HTTP tests. They include duplicate submissions, concurrent updates, payment timeouts, reconciliation and downstream outages.

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

See [`docs/claims-processing-solution.pdf`](docs/claims-processing-solution.pdf) for the full architecture and design case study.

## AI use

AI was used as a review and drafting assistant to challenge failure scenarios, compare design options and improve the structure of the solution. The final technical decisions, code and explanation were reviewed and remain the responsibility of the author.
