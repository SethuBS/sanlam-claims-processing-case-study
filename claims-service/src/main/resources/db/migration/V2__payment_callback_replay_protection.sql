create table processed_payment_event (
    event_id uuid primary key,
    claim_id uuid not null references claim(id),
    processed_at timestamp with time zone not null
);

create index idx_claim_reconciliation
    on claim(status, updated_at);
