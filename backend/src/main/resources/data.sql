-- ── 자금 풀 초기 데이터 ───────────────────────────────────────
-- KRW: target 100억, safeFloor 80억(80%), floor 60억(60%), ceiling 120억(120%)
-- USD: target 650만, safeFloor 520만(80%), floor 390만(60%), ceiling 780만(120%)
INSERT INTO company_pools (currency_code, balance, target_balance, floor_balance, safe_floor_balance, ceiling_balance, created_at, updated_at)
VALUES
    ('KRW', 10000000000.00, 10000000000.00, 6000000000.00, 8000000000.00, 12000000000.00, now(), now()),
    ('USD',     6500000.00,     6500000.00,    3900000.00,    5200000.00,     7800000.00, now(), now())
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

    --  모의계좌 입출금: 한도 사실상 비활성화 (큰 값). 추후 한도를 적용 하고 싶을 시 교체.
    ('PER_DEPOSIT',       'STANDARD', 'KRW', 9999999999.00000000, true, now(), now()), -- 원래값 2000000
    ('PER_DEPOSIT',       'ENHANCED', 'KRW', 9999999999.00000000, true, now(), now()), -- 원래값 3000000
    ('DAILY_DEPOSIT',     'STANDARD', 'KRW', 9999999999.00000000, true, now(), now()), -- 원래값 2000000
    ('DAILY_DEPOSIT',     'ENHANCED', 'KRW', 9999999999.00000000, true, now(), now()), -- 원래값 3000000
    ('PER_WITHDRAWAL',    'STANDARD', 'KRW', 9999999999.00000000, true, now(), now()), -- 원래값 2000000
    ('PER_WITHDRAWAL',    'ENHANCED', 'KRW', 9999999999.00000000, true, now(), now()), -- 원래값 3000000
    ('DAILY_WITHDRAWAL',  'STANDARD', 'KRW', 9999999999.00000000, true, now(), now()), -- 원래값 2000000
    ('DAILY_WITHDRAWAL',  'ENHANCED', 'KRW', 9999999999.00000000, true, now(), now()), -- 원래값 3000000


    -- 환전 한도.
    ('PER_EXCHANGE',      'STANDARD', 'KRW', 2000000.00000000, true, now(), now()),
    ('PER_EXCHANGE',      'ENHANCED', 'KRW', 3000000.00000000, true, now(), now()),
    ('DAILY_EXCHANGE',    'STANDARD', 'KRW', 2000000.00000000, true, now(), now()),
    ('DAILY_EXCHANGE',    'ENHANCED', 'KRW', 3000000.00000000, true, now(), now()),
    ('ANNUAL_EXCHANGE',   'STANDARD', 'USD', 100000.00000000,  true, now(), now())

    ON CONFLICT ON CONSTRAINT uk_limit_type_tier_currency DO NOTHING;

-- ── 성능 인덱스 ───────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_ledger_entries_created_at ON ledger_entries(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rebalancing_orders_status_created_at ON rebalancing_orders(status, created_at DESC);

-- ═══════════════════════════════════════════════════════════════
-- 테스트 유저 (password: Password1!)
-- test1~test10 + recipient
-- ═══════════════════════════════════════════════════════════════
INSERT INTO users (id, email, name, password_hash, role, status, kyc_status, limit_tier, wallet_limit_krw, created_at, updated_at)
VALUES
    (1,  'test1@example.com',     'Test1',     '$2a$10$reqiGLwnF.Zyzqr/T9evfeGP/9nWl5eIKh2DP9nGTL0sNse8IBQzy', 'USER', 'ACTIVE', 'COMPLETED', 'STANDARD', 20000000, now(), now()),
    (2,  'test2@example.com',     'Test2',     '$2a$10$reqiGLwnF.Zyzqr/T9evfeGP/9nWl5eIKh2DP9nGTL0sNse8IBQzy', 'USER', 'ACTIVE', 'COMPLETED', 'STANDARD', 20000000, now(), now()),
    (3,  'test3@example.com',     'Test3',     '$2a$10$reqiGLwnF.Zyzqr/T9evfeGP/9nWl5eIKh2DP9nGTL0sNse8IBQzy', 'USER', 'ACTIVE', 'COMPLETED', 'STANDARD', 20000000, now(), now()),
    (4,  'test4@example.com',     'Test4',     '$2a$10$reqiGLwnF.Zyzqr/T9evfeGP/9nWl5eIKh2DP9nGTL0sNse8IBQzy', 'USER', 'ACTIVE', 'COMPLETED', 'STANDARD', 20000000, now(), now()),
    (5,  'test5@example.com',     'Test5',     '$2a$10$reqiGLwnF.Zyzqr/T9evfeGP/9nWl5eIKh2DP9nGTL0sNse8IBQzy', 'USER', 'ACTIVE', 'COMPLETED', 'STANDARD', 20000000, now(), now()),
    (6,  'test6@example.com',     'Test6',     '$2a$10$reqiGLwnF.Zyzqr/T9evfeGP/9nWl5eIKh2DP9nGTL0sNse8IBQzy', 'USER', 'ACTIVE', 'COMPLETED', 'STANDARD', 20000000, now(), now()),
    (7,  'test7@example.com',     'Test7',     '$2a$10$reqiGLwnF.Zyzqr/T9evfeGP/9nWl5eIKh2DP9nGTL0sNse8IBQzy', 'USER', 'ACTIVE', 'COMPLETED', 'STANDARD', 20000000, now(), now()),
    (8,  'test8@example.com',     'Test8',     '$2a$10$reqiGLwnF.Zyzqr/T9evfeGP/9nWl5eIKh2DP9nGTL0sNse8IBQzy', 'USER', 'ACTIVE', 'COMPLETED', 'STANDARD', 20000000, now(), now()),
    (9,  'test9@example.com',     'Test9',     '$2a$10$reqiGLwnF.Zyzqr/T9evfeGP/9nWl5eIKh2DP9nGTL0sNse8IBQzy', 'USER', 'ACTIVE', 'COMPLETED', 'STANDARD', 20000000, now(), now()),
    (10, 'test10@example.com',    'Test10',    '$2a$10$reqiGLwnF.Zyzqr/T9evfeGP/9nWl5eIKh2DP9nGTL0sNse8IBQzy', 'USER', 'ACTIVE', 'COMPLETED', 'STANDARD', 20000000, now(), now()),
    (11, 'recipient@example.com', 'Recipient', '$2a$10$reqiGLwnF.Zyzqr/T9evfeGP/9nWl5eIKh2DP9nGTL0sNse8IBQzy', 'USER', 'ACTIVE', 'COMPLETED', 'STANDARD', 20000000, now(), now())
ON CONFLICT (email) DO NOTHING;

SELECT setval('users_id_seq', (SELECT COALESCE(MAX(id), 0) FROM users));

-- ── 지갑 (KRW + USD per user, wallet_id = user_id*2-1/user_id*2) ──
INSERT INTO wallets (id, user_id, currency_code, balance, version, deleted_at, created_at, updated_at)
VALUES
    (1,  1,  'KRW', 5000000.00, 0, NULL, now(), now()),
    (2,  1,  'USD',    3000.00, 0, NULL, now(), now()),
    (3,  2,  'KRW', 3000000.00, 0, NULL, now(), now()),
    (4,  2,  'USD',    1500.00, 0, NULL, now(), now()),
    (5,  3,  'KRW', 2000000.00, 0, NULL, now(), now()),
    (6,  3,  'USD',     800.00, 0, NULL, now(), now()),
    (7,  4,  'KRW', 8000000.00, 0, NULL, now(), now()),
    (8,  4,  'USD',    5000.00, 0, NULL, now(), now()),
    (9,  5,  'KRW', 1000000.00, 0, NULL, now(), now()),
    (10, 5,  'USD',     500.00, 0, NULL, now(), now()),
    (11, 6,  'KRW', 4000000.00, 0, NULL, now(), now()),
    (12, 6,  'USD',    2000.00, 0, NULL, now(), now()),
    (13, 7,  'KRW', 6000000.00, 0, NULL, now(), now()),
    (14, 7,  'USD',    4000.00, 0, NULL, now(), now()),
    (15, 8,  'KRW', 2500000.00, 0, NULL, now(), now()),
    (16, 8,  'USD',    1200.00, 0, NULL, now(), now()),
    (17, 9,  'KRW', 3500000.00, 0, NULL, now(), now()),
    (18, 9,  'USD',    1800.00, 0, NULL, now(), now()),
    (19, 10, 'KRW', 7000000.00, 0, NULL, now(), now()),
    (20, 10, 'USD',    3500.00, 0, NULL, now(), now()),
    (21, 11, 'KRW', 2000000.00, 0, NULL, now(), now()),
    (22, 11, 'USD',    1000.00, 0, NULL, now(), now())
ON CONFLICT DO NOTHING;

SELECT setval('wallets_id_seq', (SELECT COALESCE(MAX(id), 0) FROM wallets));

-- ── 모의 은행 계좌 ────────────────────────────────────────────
INSERT INTO mock_bank_accounts (id, user_id, owner_type, currency_code, bank_name, account_number, account_holder_name, balance, version, deleted_at, created_at, updated_at)
VALUES
    (1,  1,  'USER',      'KRW', '국민은행',       '12345678901', 'Test1',     50000000.00, 0, NULL, now(), now()),
    (2,  2,  'USER',      'KRW', '신한은행',       '23456789012', 'Test2',     30000000.00, 0, NULL, now(), now()),
    (3,  3,  'USER',      'KRW', '우리은행',       '34567890123', 'Test3',     20000000.00, 0, NULL, now(), now()),
    (4,  4,  'USER',      'KRW', '하나은행',       '45678901234', 'Test4',     80000000.00, 0, NULL, now(), now()),
    (5,  5,  'USER',      'KRW', '국민은행',       '56789012345', 'Test5',     10000000.00, 0, NULL, now(), now()),
    (6,  6,  'USER',      'KRW', '신한은행',       '67890123456', 'Test6',     40000000.00, 0, NULL, now(), now()),
    (7,  7,  'USER',      'KRW', '우리은행',       '78901234567', 'Test7',     60000000.00, 0, NULL, now(), now()),
    (8,  8,  'USER',      'KRW', '하나은행',       '89012345678', 'Test8',     25000000.00, 0, NULL, now(), now()),
    (9,  9,  'USER',      'KRW', '국민은행',       '90123456789', 'Test9',     35000000.00, 0, NULL, now(), now()),
    (10, 10, 'USER',      'KRW', '신한은행',       '01234567890', 'Test10',    70000000.00, 0, NULL, now(), now()),
    (11, 11, 'USER',      'KRW', '우리은행',       '11234567890', 'Recipient', 20000000.00, 0, NULL, now(), now()),
    (12, 1,  'RECIPIENT', 'USD', 'Bank of America', '10000001',   'Test1',     10000.00,    0, NULL, now(), now()),
    (13, 11, 'RECIPIENT', 'USD', 'Bank of America', '10000011',   'Recipient', 5000.00,     0, NULL, now(), now())
ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('mock_bank_accounts', 'id'), COALESCE(MAX(id), 1), true) FROM mock_bank_accounts;

-- ── 수취인 (test1이 recipient를 수취인으로 등록, 그 외 몇 쌍 추가) ──
INSERT INTO recipients (id, user_id, target_user_id, name, country_code, currency_code, bank_name, account_number, deleted_at, created_at, updated_at)
VALUES
    (1, 1,  11, 'Recipient', 'US', 'USD', 'Bank of America', '10000011', NULL, now(), now()),
    (2, 1,  2,  'Test2',     'US', 'USD', 'Chase',           '10000002', NULL, now(), now()),
    (3, 2,  1,  'Test1',     'US', 'USD', 'Bank of America', '10000001', NULL, now(), now()),
    (4, 3,  11, 'Recipient', 'US', 'USD', 'Bank of America', '10000011', NULL, now(), now()),
    (5, 4,  1,  'Test1',     'US', 'USD', 'Bank of America', '10000001', NULL, now(), now())
ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('recipients', 'id'), COALESCE(MAX(id), 1), true) FROM recipients;