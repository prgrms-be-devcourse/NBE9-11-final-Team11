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

-- ── 환전 거래 ─────────────────────────────────────────────────
INSERT INTO exchange_transactions (id, transaction_id, user_id, from_wallet_id, to_wallet_id, from_currency_code, to_currency_code, from_amount, to_amount, base_rate, spread_rate, final_rate, fee_amount, idempotency_key, created_at, updated_at)
VALUES
    (1,  'EX-0000000001', 1,  1,  2,  'KRW', 'USD', 1350000, 1000.00, 1350.00, 0.01, 1363.50, 0, 'IDEM-EX-1',  now() - interval '25 days', now() - interval '25 days'),
    (2,  'EX-0000000002', 1,  1,  2,  'KRW', 'USD',  675000,  500.00, 1350.00, 0.01, 1363.50, 0, 'IDEM-EX-2',  now() - interval '20 days', now() - interval '20 days'),
    (3,  'EX-0000000003', 2,  3,  4,  'KRW', 'USD',  270000,  200.00, 1350.00, 0.01, 1363.50, 0, 'IDEM-EX-3',  now() - interval '18 days', now() - interval '18 days'),
    (4,  'EX-0000000004', 1,  2,  1,  'USD', 'KRW',  500.00, 675000,  1350.00, 0.01, 1336.50, 0, 'IDEM-EX-4',  now() - interval '15 days', now() - interval '15 days'),
    (5,  'EX-0000000005', 4,  7,  8,  'KRW', 'USD', 2000000, 1481.00, 1350.00, 0.01, 1363.50, 0, 'IDEM-EX-5',  now() - interval '12 days', now() - interval '12 days'),
    (6,  'EX-0000000006', 3,  5,  6,  'KRW', 'USD',  405000,  300.00, 1350.00, 0.01, 1363.50, 0, 'IDEM-EX-6',  now() - interval '10 days', now() - interval '10 days'),
    (7,  'EX-0000000007', 5,  9,  10, 'KRW', 'USD',  540000,  400.00, 1350.00, 0.01, 1363.50, 0, 'IDEM-EX-7',  now() - interval '7 days',  now() - interval '7 days'),
    (8,  'EX-0000000008', 6,  11, 12, 'KRW', 'USD',  810000,  600.00, 1350.00, 0.01, 1363.50, 0, 'IDEM-EX-8',  now() - interval '5 days',  now() - interval '5 days'),
    (9,  'EX-0000000009', 7,  13, 14, 'KRW', 'USD', 1350000, 1000.00, 1350.00, 0.01, 1363.50, 0, 'IDEM-EX-9',  now() - interval '3 days',  now() - interval '3 days'),
    (10, 'EX-0000000010', 1,  1,  2,  'KRW', 'USD',  270000,  200.00, 1350.00, 0.01, 1363.50, 0, 'IDEM-EX-10', now() - interval '1 day',   now() - interval '1 day')
ON CONFLICT DO NOTHING;

SELECT setval('exchange_transactions_id_seq', (SELECT COALESCE(MAX(id), 0) FROM exchange_transactions));

-- ── P2P 이체 ──────────────────────────────────────────────────
INSERT INTO p2p_transfers (id, transfer_id, from_wallet_id, to_wallet_id, currency_code, amount, memo, created_at, updated_at)
VALUES
    (1, 'TXN-0000000001', 1,  21, 'KRW', 500000, '생활비',       now() - interval '22 days', now() - interval '22 days'),
    (2, 'TXN-0000000002', 2,  22, 'USD',    200, 'USD transfer', now() - interval '17 days', now() - interval '17 days'),
    (3, 'TXN-0000000003', 3,  1,  'KRW', 200000, '더치페이',      now() - interval '13 days', now() - interval '13 days'),
    (4, 'TXN-0000000004', 7,  1,  'KRW', 300000, '송금',         now() - interval '9 days',  now() - interval '9 days'),
    (5, 'TXN-0000000005', 2,  22, 'USD',    100, 'test memo',    now() - interval '4 days',  now() - interval '4 days'),
    (6, 'TXN-0000000006', 1,  21, 'KRW', 100000, '테스트',        now() - interval '2 days',  now() - interval '2 days')
ON CONFLICT DO NOTHING;

SELECT setval('p2p_transfers_id_seq', (SELECT COALESCE(MAX(id), 0) FROM p2p_transfers));

-- ── 해외송금 거래 ─────────────────────────────────────────────
INSERT INTO remittance_transactions (
    id, user_id, journal_id, recipient_id,
    recipient_name, recipient_country_code, recipient_currency_code, recipient_bank_name, recipient_account_number,
    target_user_id, method, source_mock_account_id, target_mock_account_id,
    send_currency, send_amount, receive_currency, receive_amount,
    applied_rate, fee_amount, amount_krw, amount_usd,
    reason, reason_detail, status, idempotency_key, failure_reason, created_at, updated_at
)
VALUES
    (1, 1, 'JNL-REM-0000000001', 1, 'Recipient', 'US', 'USD', 'Bank of America', '10000011', 11, 'BANK_TRANSFER', 1,  13, 'KRW', 1350000, 'USD', 1000.00, 1350.00, 13500.00, 1350000, 1000.00, 'LIVING_EXPENSES', '테스트 송금 1', 'COMPLETED', 'REM-IDEM-1', NULL,      now() - interval '21 days', now() - interval '21 days'),
    (2, 1, 'JNL-REM-0000000002', 1, 'Recipient', 'US', 'USD', 'Bank of America', '10000011', 11, 'BANK_TRANSFER', 1,  13, 'KRW',  675000, 'USD',  500.00, 1350.00,  6750.00,  675000,  500.00, 'TUITION',         '테스트 송금 2', 'COMPLETED', 'REM-IDEM-2', NULL,      now() - interval '14 days', now() - interval '14 days'),
    (3, 3, 'JNL-REM-0000000003', 4, 'Recipient', 'US', 'USD', 'Bank of America', '10000011', 11, 'BANK_TRANSFER', 3,  13, 'KRW', 2000000, 'USD', 1481.00, 1350.00, 20000.00, 2000000, 1481.00, 'FAMILY_SUPPORT',  '테스트 송금 3', 'COMPLETED', 'REM-IDEM-3', NULL,      now() - interval '8 days',  now() - interval '8 days'),
    (4, 1, 'JNL-REM-0000000004', 2, 'Test2',     'US', 'USD', 'Chase',           '10000002', 2,  'BANK_TRANSFER', 1,  13, 'KRW',  405000, 'USD',  300.00, 1350.00,  4050.00,  405000,  300.00, 'LIVING_EXPENSES', '테스트 송금 4', 'PENDING',   'REM-IDEM-4', NULL,      now() - interval '1 day',   now() - interval '1 day'),
    (5, 2, 'JNL-REM-0000000005', 3, 'Test1',     'US', 'USD', 'Bank of America', '10000001', 1,  'BANK_TRANSFER', 2,  12, 'KRW',  270000, 'USD',  200.00, 1350.00,  2700.00,  270000,  200.00, 'ETC',             '테스트 송금 5', 'FAILED',    'REM-IDEM-5', '잔액 부족', now() - interval '3 days',  now() - interval '3 days')
ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('remittance_transactions', 'id'), COALESCE(MAX(id), 1), true) FROM remittance_transactions;

-- ── 가상계좌 ──────────────────────────────────────────────────
INSERT INTO virtual_accounts (id, user_id, remittance_transaction_id, bank_name, account_number, expected_amount, status, ref_type, ref_id, issued_at, expired_at, paid_at, created_at, updated_at)
VALUES
    (1, 1,  1, '최강은행', '90000000001', 1363500.00, 'PAID',    'REMITTANCE', '1', now() - interval '21 days', now() - interval '21 days' + interval '30 minutes', now() - interval '21 days' + interval '5 minutes',  now() - interval '21 days', now() - interval '21 days'),
    (2, 1,  2, '최강은행', '90000000002',  681750.00, 'PAID',    'REMITTANCE', '2', now() - interval '14 days', now() - interval '14 days' + interval '30 minutes', now() - interval '14 days' + interval '3 minutes',  now() - interval '14 days', now() - interval '14 days'),
    (3, 3,  3, '최강은행', '90000000003', 2020000.00, 'PAID',    'REMITTANCE', '3', now() - interval '8 days',  now() - interval '8 days'  + interval '30 minutes', now() - interval '8 days'  + interval '10 minutes', now() - interval '8 days',  now() - interval '8 days'),
    (4, 1,  4, '최강은행', '90000000004',  409050.00, 'ISSUED',  'REMITTANCE', '4', now() - interval '1 day',  now() - interval '1 day'   + interval '30 minutes', NULL, now() - interval '1 day',  now() - interval '1 day'),
    (5, 2,  5, '최강은행', '90000000005',  272700.00, 'EXPIRED', 'REMITTANCE', '5', now() - interval '3 days', now() - interval '3 days'  + interval '30 minutes', NULL, now() - interval '3 days', now() - interval '3 days')
ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('virtual_accounts', 'id'), COALESCE(MAX(id), 1), true) FROM virtual_accounts;

-- ── 원장 항목 ─────────────────────────────────────────────────
INSERT INTO ledger_entries (journal_id, entry_type, ledger_direction, wallet_id, mock_bank_account_id, company_pool_id, currency_code, amount, balance_before, balance_after, ref_type, ref_id, created_at, updated_at)
VALUES
    -- 환전 (KRW→USD, test1)
    ('JNL-EX-1-DEBIT',  'EXCHANGE', 'DEBIT',  1,  NULL, NULL, 'KRW', 1350000, 6350000, 5000000, 'EXCHANGE',     'EX-0000000001',  now() - interval '25 days', now() - interval '25 days'),
    ('JNL-EX-1-CREDIT', 'EXCHANGE', 'CREDIT', 2,  NULL, NULL, 'USD',    1000,    2000,    3000,  'EXCHANGE',     'EX-0000000001',  now() - interval '25 days', now() - interval '25 days'),
    ('JNL-EX-2-DEBIT',  'EXCHANGE', 'DEBIT',  1,  NULL, NULL, 'KRW',  675000, 5675000, 5000000, 'EXCHANGE',     'EX-0000000002',  now() - interval '20 days', now() - interval '20 days'),
    ('JNL-EX-2-CREDIT', 'EXCHANGE', 'CREDIT', 2,  NULL, NULL, 'USD',     500,    2500,    3000,  'EXCHANGE',     'EX-0000000002',  now() - interval '20 days', now() - interval '20 days'),
    -- 환전 (USD→KRW, test1)
    ('JNL-EX-4-DEBIT',  'EXCHANGE', 'DEBIT',  2,  NULL, NULL, 'USD',     500,    3500,    3000,  'EXCHANGE',     'EX-0000000004',  now() - interval '15 days', now() - interval '15 days'),
    ('JNL-EX-4-CREDIT', 'EXCHANGE', 'CREDIT', 1,  NULL, NULL, 'KRW',  675000, 4325000, 5000000, 'EXCHANGE',     'EX-0000000004',  now() - interval '15 days', now() - interval '15 days'),
    -- P2P 이체 (test1 → recipient)
    ('JNL-P2P-1-DEBIT',  'TRANSFER', 'DEBIT',  1,  NULL, NULL, 'KRW', 500000, 5500000, 5000000, 'P2P_TRANSFER', 'TXN-0000000001', now() - interval '22 days', now() - interval '22 days'),
    ('JNL-P2P-1-CREDIT', 'TRANSFER', 'CREDIT', 21, NULL, NULL, 'KRW', 500000, 1500000, 2000000, 'P2P_TRANSFER', 'TXN-0000000001', now() - interval '22 days', now() - interval '22 days'),
    -- P2P 이체 (test1 USD → recipient USD)
    ('JNL-P2P-2-DEBIT',  'TRANSFER', 'DEBIT',  2,  NULL, NULL, 'USD',  200, 3200, 3000, 'P2P_TRANSFER', 'TXN-0000000002', now() - interval '17 days', now() - interval '17 days'),
    ('JNL-P2P-2-CREDIT', 'TRANSFER', 'CREDIT', 22, NULL, NULL, 'USD',  200,  800, 1000, 'P2P_TRANSFER', 'TXN-0000000002', now() - interval '17 days', now() - interval '17 days'),
    -- 해외송금 (test1 → recipient)
    ('JNL-REM-0000000001', 'TRANSFER', 'DEBIT', NULL, 1, NULL, 'KRW', 1363500, 51363500, 50000000, 'REMITTANCE', 'JNL-REM-0000000001', now() - interval '21 days', now() - interval '21 days'),
    ('JNL-REM-0000000002', 'TRANSFER', 'DEBIT', NULL, 1, NULL, 'KRW',  681750, 50681750, 50000000, 'REMITTANCE', 'JNL-REM-0000000002', now() - interval '14 days', now() - interval '14 days'),
    -- 해외송금 (test3 → recipient)
    ('JNL-REM-0000000003', 'TRANSFER', 'DEBIT', NULL, 3, NULL, 'KRW', 2020000, 22020000, 20000000, 'REMITTANCE', 'JNL-REM-0000000003', now() - interval '8 days',  now() - interval '8 days')
ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('ledger_entries', 'id'), COALESCE(MAX(id), 1), true) FROM ledger_entries;