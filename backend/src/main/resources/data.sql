-- ── 자금 풀 초기 데이터 ───────────────────────────────────────
-- KRW: target 100억, floor 80억(80%), ceiling 120억(120%)
-- USD: target 650만, floor 520만(80%), ceiling 780만(120%)
INSERT INTO company_pools (currency_code, balance, target_balance, floor_balance, ceiling_balance, created_at, updated_at)
VALUES
    ('KRW', 10000000000.00, 10000000000.00,  8000000000.00, 12000000000.00, now(), now()),
    ('USD',     6500000.00,     6500000.00,     5200000.00,     7800000.00, now(), now())
ON CONFLICT (currency_code) DO NOTHING;

-- ── 통화 ──────────────────────────────────────────────────
INSERT INTO currencies (currency_code, currency_name, symbol, decimal_places, created_at)
VALUES
    ('KRW', '원화', '₩', 0, now()),
    ('USD', '미국 달러', '$', 2, now())
ON CONFLICT (currency_code) DO NOTHING;

-- ── 한도 정책 초기 데이터 ──────────────────────────────────
INSERT INTO transaction_limits (limit_type, tier, currency_code, limit_amount, is_active, created_at, updated_at)
VALUES
    ('PER_REMITTANCE',    'STANDARD', 'USD', 5000.00000000,    true, now(), now()),
    ('ANNUAL_REMITTANCE', 'STANDARD', 'USD', 100000.00000000,  true, now(), now()),
    ('PER_DEPOSIT',       'STANDARD', 'KRW', 2000000.00000000, true, now(), now()),
    ('PER_DEPOSIT',       'ENHANCED', 'KRW', 3000000.00000000, true, now(), now()),
    ('DAILY_DEPOSIT',     'STANDARD', 'KRW', 2000000.00000000, true, now(), now()),
    ('DAILY_DEPOSIT',     'ENHANCED', 'KRW', 3000000.00000000, true, now(), now()),
    ('PER_WITHDRAWAL',    'STANDARD', 'KRW', 2000000.00000000, true, now(), now()),
    ('PER_WITHDRAWAL',    'ENHANCED', 'KRW', 3000000.00000000, true, now(), now()),
    ('DAILY_WITHDRAWAL',  'STANDARD', 'KRW', 2000000.00000000, true, now(), now()),
    ('DAILY_WITHDRAWAL',  'ENHANCED', 'KRW', 3000000.00000000, true, now(), now())
    ON CONFLICT ON CONSTRAINT uk_limit_type_tier_currency DO NOTHING;