-- ===========================================
-- FocusKeeper Dummy Data Seed Script
-- Used for development and stress testing
-- ===========================================

-- ===========================================
-- 1. Users
-- ===========================================
INSERT INTO users (id, email, nickname, provider, role, created_at, updated_at)
VALUES (1, 'dev@focuskeeper.io', 'DevUser', 'GOOGLE', 'USER', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, nickname, provider, role, created_at, updated_at)
VALUES (2, 'test@focuskeeper.io', 'TestUser', 'KAKAO', 'USER', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, nickname, provider, role, created_at, updated_at)
VALUES (3, 'admin@focuskeeper.io', 'AdminUser', 'GOOGLE', 'ADMIN', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ===========================================
-- 2. Wallets (Cash Balance + Points)
-- ===========================================
INSERT INTO wallet (id, user_id, balance, point, created_at, updated_at)
VALUES (1, 1, 100000, 50000, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO wallet (id, user_id, balance, point, created_at, updated_at)
VALUES (2, 2, 50000, 10000, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO wallet (id, user_id, balance, point, created_at, updated_at)
VALUES (3, 3, 999999, 999999, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ===========================================
-- 2-1. Stress Test Users & Wallets (ID 100-149)
-- For wallet concurrency testing
-- ===========================================
DO $$
BEGIN
    FOR i IN 100..149 LOOP
        INSERT INTO users (id, email, nickname, provider, role, created_at, updated_at)
        VALUES (i, 'stress' || i || '@test.io', 'StressUser' || i, 'GOOGLE', 'USER', NOW(), NOW())
        ON CONFLICT (id) DO NOTHING;
        
        INSERT INTO wallet (id, user_id, balance, point, created_at, updated_at)
        VALUES (i, i, 10000, 1000, NOW(), NOW())
        ON CONFLICT (id) DO NOTHING;
    END LOOP;
END $$;

-- Update sequences for stress test users
SELECT setval('users_id_seq', GREATEST((SELECT MAX(id) FROM users), 149), true);
SELECT setval('wallet_id_seq', GREATEST((SELECT MAX(id) FROM wallet), 149), true);

-- ===========================================
-- 3. Items (Shop Items)
-- ===========================================
INSERT INTO item (id, name, description, item_type, price, active, created_at)
VALUES (1, '면제권', '챌린지 실패 시 예치금을 보호합니다', 'PASS_TICKET', 500, true, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO item (id, name, description, item_type, price, active, created_at)
VALUES (2, '더블 포인트', '챌린지 성공 시 포인트 2배를 획득합니다', 'DOUBLE_POINT', 1000, true, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO item (id, name, description, item_type, price, active, created_at)
VALUES (3, '마감 연장권', '챌린지 마감을 24시간 연장합니다', 'EXTEND_DEADLINE', 800, true, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO item (id, name, description, item_type, price, active, created_at)
VALUES (4, '스트릭 동결권', '하루 동안 스트릭을 보호합니다 (Duolingo Style)', 'PASS_TICKET', 300, true, NOW())
ON CONFLICT (id) DO NOTHING;

-- ===========================================
-- 4. User Items (Inventory)
-- ===========================================
INSERT INTO user_item (id, user_id, item_id, quantity, created_at)
VALUES (1, 1, 1, 5, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_item (id, user_id, item_id, quantity, created_at)
VALUES (2, 1, 2, 3, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_item (id, user_id, item_id, quantity, created_at)
VALUES (3, 2, 1, 2, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_item (id, user_id, item_id, quantity, created_at)
VALUES (4, 3, 1, 99, NOW())
ON CONFLICT (id) DO NOTHING;

-- ===========================================
-- 5. Challenges
-- ===========================================
-- 완료된 챌린지
INSERT INTO challenge (id, user_id, title, description, challenge_type, status, deadline, target_value, created_at, updated_at)
VALUES (1, 1, '미라클 모닝 도전', '06:00 이전에 일어나기', 'TIME', 'COMPLETED', NOW() - INTERVAL '1 day', '06:00', NOW() - INTERVAL '2 days', NOW())
ON CONFLICT (id) DO NOTHING;

-- 진행 중인 챌린지
INSERT INTO challenge (id, user_id, title, description, challenge_type, status, deadline, target_value, created_at, updated_at)
VALUES (2, 1, 'GitHub 1커밋', '오늘 GitHub에 1개 이상 커밋하기', 'GITHUB', 'IN_PROGRESS', NOW() + INTERVAL '1 day', 'ImJeongBright', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 대기 중인 챌린지
INSERT INTO challenge (id, user_id, title, description, challenge_type, status, deadline, target_value, created_at, updated_at)
VALUES (3, 2, '책 10페이지 읽기', '하루 10페이지 독서 챌린지', 'MANUAL', 'PENDING', NOW() + INTERVAL '2 days', NULL, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 실패한 챌린지
INSERT INTO challenge (id, user_id, title, description, challenge_type, status, deadline, target_value, created_at, updated_at)
VALUES (4, 2, '새벽 기상 실패', '04:00에 일어나기 도전 실패', 'TIME', 'FAILED', NOW() - INTERVAL '3 days', '04:00', NOW() - INTERVAL '4 days', NOW())
ON CONFLICT (id) DO NOTHING;

-- 검증 대기 중
INSERT INTO challenge (id, user_id, title, description, challenge_type, status, deadline, target_value, created_at, updated_at)
VALUES (5, 1, 'PR 머지 챌린지', 'GitHub에 PR 머지하기', 'GITHUB', 'PENDING_VERIFICATION', NOW() + INTERVAL '1 hour', 'ImJeongBright', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ===========================================
-- 6. Credit Logs (거래 내역)
-- ===========================================
INSERT INTO credit_log (id, wallet_id, amount, reason, created_at, updated_at)
VALUES (1, 1, 10000, 'CHARGE', NOW() - INTERVAL '10 days', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO credit_log (id, wallet_id, amount, reason, created_at, updated_at)
VALUES (2, 1, -5000, 'DEPOSIT', NOW() - INTERVAL '5 days', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO credit_log (id, wallet_id, amount, reason, created_at, updated_at)
VALUES (3, 1, 5000, 'REFUND', NOW() - INTERVAL '4 days', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO credit_log (id, wallet_id, amount, reason, created_at, updated_at)
VALUES (4, 1, 500, 'REWARD', NOW() - INTERVAL '4 days', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO credit_log (id, wallet_id, amount, reason, created_at, updated_at)
VALUES (5, 2, -500, 'ITEM_PURCHASE', NOW() - INTERVAL '2 days', NOW())
ON CONFLICT (id) DO NOTHING;

-- ===========================================
-- 7. Action Logs (사용자 행동 기록)
-- ===========================================
INSERT INTO action_log (id, user_id, challenge_id, action_type, device_context, created_at, updated_at)
VALUES (1, 1, 1, 'CREATE', '{"device": "iPhone 15", "os": "iOS 17.3", "app_version": "1.0.0"}', NOW() - INTERVAL '2 days', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO action_log (id, user_id, challenge_id, action_type, device_context, created_at, updated_at)
VALUES (2, 1, 1, 'COMPLETE', '{"device": "iPhone 15", "os": "iOS 17.3", "app_version": "1.0.0"}', NOW() - INTERVAL '1 day', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO action_log (id, user_id, challenge_id, action_type, device_context, created_at, updated_at)
VALUES (3, 1, 2, 'CREATE', '{"device": "MacBook Pro", "os": "macOS 14.3", "app_version": "1.0.0"}', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO action_log (id, user_id, challenge_id, action_type, device_context, created_at, updated_at)
VALUES (4, 2, 4, 'FAIL', '{"device": "Galaxy S24", "os": "Android 14", "app_version": "1.0.0"}', NOW() - INTERVAL '3 days', NOW())
ON CONFLICT (id) DO NOTHING;

-- ===========================================
-- 8. Clinical Reports (임상 리포트)
-- ===========================================
INSERT INTO clinical_report (id, user_id, report_type, s3_url, summary_json, created_at, updated_at)
VALUES (1, 1, 'WEEKLY', 'https://s3.amazonaws.com/focuskeeper/reports/user1_week1.pdf', 
        '{"total_challenges": 7, "completed": 5, "failed": 2, "success_rate": 71.4, "total_points_earned": 2500}', 
        NOW() - INTERVAL '7 days', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO clinical_report (id, user_id, report_type, s3_url, summary_json, created_at, updated_at)
VALUES (2, 1, 'MONTHLY', 'https://s3.amazonaws.com/focuskeeper/reports/user1_month1.pdf', 
        '{"total_challenges": 30, "completed": 22, "failed": 8, "success_rate": 73.3, "total_points_earned": 11000, "streak_max": 12}', 
        NOW() - INTERVAL '30 days', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO clinical_report (id, user_id, report_type, s3_url, summary_json, created_at, updated_at)
VALUES (3, 2, 'WEEKLY', 'https://s3.amazonaws.com/focuskeeper/reports/user2_week1.pdf', 
        '{"total_challenges": 5, "completed": 2, "failed": 3, "success_rate": 40.0, "total_points_earned": 1000}', 
        NOW() - INTERVAL '7 days', NOW())
ON CONFLICT (id) DO NOTHING;

-- ===========================================
-- Reset Sequences (Optional, for clean ID generation)
-- ===========================================
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users), true);
SELECT setval('wallet_id_seq', (SELECT MAX(id) FROM wallet), true);
SELECT setval('item_id_seq', (SELECT MAX(id) FROM item), true);
SELECT setval('user_item_id_seq', (SELECT MAX(id) FROM user_item), true);
SELECT setval('challenge_id_seq', (SELECT MAX(id) FROM challenge), true);
SELECT setval('credit_log_id_seq', (SELECT MAX(id) FROM credit_log), true);
SELECT setval('action_log_id_seq', (SELECT MAX(id) FROM action_log), true);
SELECT setval('clinical_report_id_seq', (SELECT MAX(id) FROM clinical_report), true);
