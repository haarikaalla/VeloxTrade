-- VeloxTrade baseline schema.
create table if not exists accounts (
    id            uuid           primary key,
    email         varchar(320)   not null unique,
    password_hash varchar(120)   not null,
    display_name  varchar(80)    not null,
    cash_balance  numeric(19, 2) not null,
    created_at    timestamptz    not null
);

create table if not exists positions (
    id            uuid           primary key,
    account_id    uuid           not null references accounts (id) on delete cascade,
    symbol        varchar(12)    not null,
    quantity      bigint         not null,
    average_price numeric(19, 4) not null,
    version       bigint         not null default 0,
    constraint uq_positions_account_symbol unique (account_id, symbol)
);

create table if not exists orders (
    id                  uuid           primary key,
    account_id          uuid           not null references accounts (id) on delete cascade,
    symbol              varchar(12)    not null,
    side                varchar(8)     not null,
    status              varchar(20)    not null,
    quantity            bigint         not null,
    filled_quantity     bigint         not null,
    limit_price         numeric(19, 4) not null,
    average_fill_price  numeric(19, 4),
    engine_order_id     bigint,
    match_latency_nanos bigint,
    created_at          timestamptz    not null
);

create index if not exists idx_orders_account_created on orders (account_id, created_at desc);

-- The tick table is keyed on (id, observed_at) so TimescaleDB can partition it.
create table if not exists market_ticks (
    id          uuid           not null,
    symbol      varchar(12)    not null,
    price       numeric(19, 4) not null,
    bid         numeric(19, 4),
    ask         numeric(19, 4),
    observed_at timestamptz    not null,
    primary key (id, observed_at)
);

create index if not exists idx_market_ticks_symbol_time on market_ticks (symbol, observed_at desc);

-- Promote the tick table to a hypertable when TimescaleDB is present; plain
-- PostgreSQL deployments keep the regular table and still work.
do $$
begin
    if exists (select 1 from pg_available_extensions where name = 'timescaledb') then
        create extension if not exists timescaledb cascade;
        perform create_hypertable('market_ticks', 'observed_at',
                                  chunk_time_interval => interval '1 day',
                                  migrate_data => true,
                                  if_not_exists => true);
    end if;
exception
    when others then
        raise notice 'TimescaleDB hypertable not created: %', sqlerrm;
end
$$;
