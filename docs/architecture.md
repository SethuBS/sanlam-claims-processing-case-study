# Architecture Notes

## Core flow

The Claims Platform owns the claim lifecycle. Client Registry owns client data, Policy Manager owns policy and benefit decisions, and the Payment System owns settlement.

The intake operation is asynchronous from the channel perspective. The claim is stored before downstream processing starts.

## Reliability

The key controls are:

1. Idempotency key at claim intake.
2. Unique external reference at the database boundary.
3. Claim and outbox event committed in one transaction.
4. Explicit business states.
5. Optimistic locking for concurrent updates.
6. Payment idempotency and reconciliation.
7. Dead-letter queues and operational runbooks in the AWS deployment.

## Production mapping

The case-study implementation is a modular Spring Boot service. The production target maps the orchestration and queues to AWS Step Functions, SQS and EventBridge while keeping the business domain independent of those services.

## Senior-level judgement

The number of services is not the goal. The goal is clear ownership and predictable failure behaviour. The first implementation is intentionally modular so that it remains simple to build and test, while interfaces leave room to extract workers or integrations later when there is a real scaling or ownership reason.
