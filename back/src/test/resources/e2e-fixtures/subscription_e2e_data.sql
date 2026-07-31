SET REFERENTIAL_INTEGRITY FALSE;
TRUNCATE TABLE subscriptions;
TRUNCATE TABLE users;
SET REFERENTIAL_INTEGRITY TRUE;

-- Users for testing
INSERT INTO users (id, created_at, updated_at, email, password, nickname, role) 
VALUES 
(1, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'sub@test.com', 'test', 'E2E_Subscriber', 'USER'),
(2, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'creator@test.com', 'test', 'E2E_Creator', 'USER'),
(3, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'followed@test.com', 'test', 'E2E_Followed', 'USER'),
(4, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 'membership@test.com', 'test', 'E2E_Membership', 'USER');

-- Subscriptions for edge cases
-- User 1 follows User 3
INSERT INTO subscriptions (id, created_at, user_id, creator_id, subscription_tier, started_at) 
VALUES (1, CURRENT_TIMESTAMP(), 1, 3, 'FOLLOW', CURRENT_DATE());

-- User 1 has membership with User 4
INSERT INTO subscriptions (id, created_at, user_id, creator_id, subscription_tier, started_at) 
VALUES (2, CURRENT_TIMESTAMP(), 1, 4, 'MEMBERSHIP', CURRENT_DATE());
