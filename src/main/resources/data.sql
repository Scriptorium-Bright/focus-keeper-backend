-- ===========================================
-- Test Data Seed Script
-- Used for development and stress testing
-- ===========================================

-- ===========================================
-- 1. Test Users
-- ===========================================
INSERT INTO users (id, email, nickname, provider, role, created_at, modified_at)
VALUES (1, 'test@test.com', 'TestUser', 'LOCAL', 'USER', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, email, nickname, provider, role, created_at, modified_at)
VALUES (2, 'stress@test.com', 'StressTestUser', 'LOCAL', 'USER', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ===========================================
-- 2. Wallets (with initial balance and points)
-- ===========================================
INSERT INTO wallet (id, user_id, balance, point, created_at, modified_at)
VALUES (1, 1, 100000, 50000, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO wallet (id, user_id, balance, point, created_at, modified_at)
VALUES (2, 2, 100000, 50000, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ===========================================
-- 3. Items (Shop items for purchase)
-- ===========================================
INSERT INTO item (id, name, description, item_type, price, active, created_at)
VALUES (1, '면제권', '챌린지 실패 시 예치금을 보호합니다', 'PASS_TICKET', 500, true, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO item (id, name, description, item_type, price, active, created_at)
VALUES (2, '더블 포인트', '챌린지 성공 시 포인트 2배를 획득합니다', 'DOUBLE_POINT', 1000, true, NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO item (id, name, description, item_type, price, active, created_at)
VALUES (3, '마감 연장권', '챌린지 마감을 연장합니다', 'EXTEND_DEADLINE', 800, true, NOW())
ON CONFLICT (id) DO NOTHING;

-- ===========================================
-- 4. User Items (Initial inventory)
-- ===========================================
INSERT INTO user_item (id, user_id, item_id, quantity, created_at)
VALUES (1, 1, 1, 10, NOW())
ON CONFLICT (id) DO NOTHING;
