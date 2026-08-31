create table claim (
    id uuid primary key,
    external_reference varchar(255) not null unique,
    client_id varchar(255) not null,
    policy_number varchar(255) not null,
    claim_type varchar(50) not null,
    status varchar(50) not null,
    priority varchar(50) not null,
    incident_date date not null,
    amount numeric(19,2) not null,
    currency varchar(3) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    payment_reference varchar(255),
    version bigint not null
);

create table idempotency_record (
    id uuid primary key,
    idempotency_key varchar(255) not null unique,
    request_hash varchar(255) not null,
    claim_id uuid not null,
    created_at timestamp with time zone not null
);

create table outbox_event (
    id uuid primary key,
    event_type varchar(255) not null,
    aggregate_id uuid not null,
    payload text not null,
    occurred_at timestamp with time zone not null,
    published_at timestamp with time zone
);
