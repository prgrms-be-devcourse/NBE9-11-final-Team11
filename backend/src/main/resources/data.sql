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