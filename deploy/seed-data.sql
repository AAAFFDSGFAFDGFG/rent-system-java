-- =====================================================================
-- 家·房源 | 种子数据脚本（审核门 2 产物）
-- 依据：database-design.md（门 1 定稿）+ schema.sql
-- 基准日：2026-11-14（与 prototype/index.html 的 MOCK_TODAY 对齐）
-- 内容：2 个小区 / 8 套房源（4 租 4 空）/ 4 份生效合同 / 3 位成员
--       / 1 份默认日报模板（5 个变量，无人物信息）/ 3 条全局配置
--       / 1 条「提前违约退租」历史合同样例（含没收押金，供财产明细验证）
-- ---------------------------------------------------------------------
-- 【前置】必须先执行 schema.sql 建库建表。
-- 【重复执行策略】所有 INSERT 均带 ON DUPLICATE KEY UPDATE ... 空操作，
--   重复执行不会产生重复行，也不会覆盖你在界面上修改过的数据。
--   若需要恢复初始数据，请先手工 DELETE 对应行再执行本脚本。
-- ---------------------------------------------------------------------
-- 【⚠️ 敏感值说明 —— 部署前必须替换】
--   1) member.send_key_cipher：当前是占位串。正式版由后端在录入成员时
--      用 AES-GCM 加密（密钥来自部署机环境变量，绝不入库）后写入。
--   2) member.send_key_hash：当前是占位串。正式版写入
--      HMAC-SHA256(服务端密钥, 明文 SendKey) 的 64 位十六进制值。
--   3) 明文 SendKey 任何情况下都不得写入本文件、日志或接口响应。
--   4) 手机号与租客电话均使用脱敏占位（如 138-****-0000），按需替换。
--   5) send_key_tail 为尾四位，非机密，与原型「SendKey ····0001」一致。
-- ---------------------------------------------------------------------
-- 【不预置的数据】digest_report / notification_record / reminder_record
--   均为系统运行后由定时任务产生（reminder_record 采用方案 B，
--   只记录已发送事项），刻意不预置，避免伪造历史流水。
-- =====================================================================

USE rent_system;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. house 房源（8 套：书香雅苑 5 套 / 翡翠湾 3 套）
--    注意：active_location_flag 是 STORED 生成列，禁止在 INSERT 中赋值。
-- ---------------------------------------------------------------------
INSERT INTO house
  (id, community, building, floor_no, room_no, layout, area, face, expected_rent, status, tags_json)
VALUES
  (1, '书香雅苑', '1栋', 3, 'A', '两室一厅', 68.00, '南', 3200.00, 'rented', JSON_ARRAY('近地铁', '精装修', '家电齐全')),
  (2, '书香雅苑', '1栋', 3, 'B', '一室一厅', 45.00, '东南', 2800.00, 'rented', JSON_ARRAY('小户型', '拎包入住')),
  (3, '书香雅苑', '1栋', 2, 'A', '两室一厅', 70.00, '南', 3500.00, 'vacant', JSON_ARRAY('朝南', '采光好', '新装修')),
  (4, '书香雅苑', '1栋', 2, 'B', '一室一厅', 42.00, '北', 2600.00, 'vacant', JSON_ARRAY('高性价比', '地铁口')),
  (5, '书香雅苑', '2栋', 5, 'A', '两室两厅', 82.00, '西', 3800.00, 'rented', JSON_ARRAY('高层', '视野好')),
  (6, '翡翠湾',   '1栋', 1, 'A', '三室两厅', 96.00, '南北通透', 4500.00, 'rented', JSON_ARRAY('全家租', '带车位', '首层')),
  (7, '翡翠湾',   '1栋', 1, 'B', '三室两厅', 110.00, '南北', 5200.00, 'vacant', JSON_ARRAY('大户型', '带花园', '首层')),
  (8, '翡翠湾',   '3栋', 2, 'A', '两室一厅', 65.00, '东', 3300.00, 'vacant', JSON_ARRAY('朝东', '安静'))
ON DUPLICATE KEY UPDATE id = id;

-- ---------------------------------------------------------------------
-- 2. lease_contract 生效合同（4 份，对应 4 套 rented 房源）
--    注意：active_house_id 是 STORED 生成列，禁止在 INSERT 中赋值。
--    electric_start / water_start 在原型中是字符串，此处统一为 DECIMAL(12,1)。
-- ---------------------------------------------------------------------
INSERT INTO lease_contract
  (id, house_id, actual_rent, payment_mode, deposit, start_date, end_date, pay_day,
   electric_start, water_start, tenant_name, tenant_phone, note, status)
VALUES
  (1, 1, 3200.00, '押一付三', 3200.00, '2026-05-01', '2027-04-30', 18,
   12580.0, 302.0, '王先生', '139-****-5678', '安静、爱干净', 'active'),
  (2, 2, 2800.00, '押一付一', 2800.00, '2026-08-15', '2026-11-20', 20,
   8432.5, 186.0, '李女士', '137-****-3333', '11/20 到期', 'active'),
  (3, 5, 3800.00, '押一付三', 3800.00, '2026-10-01', '2027-09-30', 10,
   21340.0, 415.5, '赵女士', '135-****-8888', '养猫，已同意', 'active'),
  (4, 6, 4500.00, '押二付一', 9000.00, '2026-03-01', '2027-02-28', 5,
   5620.5, 128.0, '张先生', '136-****-6666', '含一个车位', 'active')
ON DUPLICATE KEY UPDATE id = id;

-- 校验口径自检（全部应满足）：
--   actual_rent > 0 ✓  end_date > start_date ✓  pay_day BETWEEN 1 AND 31 ✓
--   每套房源仅一份 status='active' 合同 ✓（由 uk_contract_active 生成列唯一约束保证）

-- ---------------------------------------------------------------------
-- 2b. 历史合同样例：提前违约退租（house 3 现为 vacant 即因该次退租）
--     用于验证「财产卡 → 本区间没收押金」有数据可展示。
--     注意：这是 terminated 合同，不计入 4 份生效合同，也不改变 4 租 4 空。
-- ---------------------------------------------------------------------
INSERT INTO lease_contract
  (id, house_id, actual_rent, payment_mode, deposit, start_date, end_date, pay_day,
   electric_start, water_start, tenant_name, tenant_phone, note,
   status, terminated_at, end_reason, forfeited_deposit, predecessor_id)
VALUES
  (5, 3, 3400.00, '押一付三', 3400.00, '2026-06-01', '2026-12-31', 8,
   9012.0, 210.0, '陈先生', '138-****-2222', '提前违约退租，押金不予退还',
   'terminated', '2026-11-10 10:30:00', 'breach', 3400.00, NULL)
ON DUPLICATE KEY UPDATE id = id;

-- 该样例使 2026 年 11 月「没收押金」为 ¥3,400.00；
-- 其计租上界为 terminated_at 前一天（2026-11-09），11 月租金按 9 天 × 3400/30 计入累计收入。

-- ---------------------------------------------------------------------
-- 3. member 家庭成员（3 位，各自独立推送开关）
--    ⚠️ 以下 send_key_cipher / send_key_hash 均为占位，部署前必须替换。
-- ---------------------------------------------------------------------
INSERT INTO member
  (id, username, phone, send_key_cipher, send_key_hash, send_key_tail, push_enabled, digest_send_time)
VALUES
  (1, 'admin',    '138-****-0000',
      '__REPLACE_WITH_AES_GCM_CIPHERTEXT_FOR_ADMIN__',
      '__REPLACE_WITH_HMAC_SHA256_64HEX_FOR_ADMIN__',
      '0001', 1, '09:00'),
  (2, 'mama',     '139-****-2222',
      '__REPLACE_WITH_AES_GCM_CIPHERTEXT_FOR_MAMA__',
      '__REPLACE_WITH_HMAC_SHA256_64HEX_FOR_MAMA__',
      '0002', 1, '09:00'),
  (3, 'xiaoming', '137-****-4444',
      '__REPLACE_WITH_AES_GCM_CIPHERTEXT_FOR_XIAOMING__',
      '__REPLACE_WITH_HMAC_SHA256_64HEX_FOR_XIAOMING__',
      '0003', 0, '09:00')
ON DUPLICATE KEY UPDATE id = id;

-- 演示登录凭证（仅原型内有效，与 prototype/index.html 一致）：
--   admin    / SCT2026ABCD0001
--   mama     / SCT2026ABCD0002
--   xiaoming / SCT2026ABCD0003
-- 正式版登录时前端提交明文 SendKey，后端用 HMAC 与 send_key_hash 比对；
-- 明文不入库、不写日志、不返回任何响应。

-- ---------------------------------------------------------------------
-- 4. digest_template 默认每日分发文案模板（5 个变量，无人物信息）
--    \n 为换行转义；与原型 DEFAULT_DIGEST_TEMPLATE 完全一致。
--    ✅ 按审核意见删除「—— {负责人} · 每日 09:00 由 Server酱 推送」整行，
--       变量 {负责人} 一并移除，日报不再携带任何人物信息。
-- ---------------------------------------------------------------------
INSERT INTO digest_template
  (id, name, content_text, variables_json, is_default)
VALUES
  (1, 'default',
   '【家庭房源日报】{日期}\n'
   '📢 空置房源（{空置数量} 套）\n'
   '{空置房源清单}\n'
   '⏰ 未来 7 天临期（{临期数量} 项）\n'
   '{临期事项清单}',
   JSON_ARRAY('{日期}', '{空置数量}', '{空置房源清单}', '{临期数量}', '{临期事项清单}'),
   1)
ON DUPLICATE KEY UPDATE id = id;

-- ---------------------------------------------------------------------
-- 5. system_config 全局配置
-- ---------------------------------------------------------------------
INSERT INTO system_config (config_key, config_value, description)
VALUES
  ('digest_send_time',     '09:00', '新成员默认每日聚合日报推送时间（各成员可在推送配置页单独修改，存 member.digest_send_time）'),
  ('remind_advance_days',  '7',     '付款/到期提前提醒天数；详情抽屉「提前 7 天聚合提醒」为全局配置'),
  ('send_on_empty_day',    '0',     '无事项日是否仍发送日报；默认关闭以节省 Server酱 额度')
ON DUPLICATE KEY UPDATE config_key = config_key;

-- =====================================================================
-- 初始化结果自检（可在执行后运行）
-- ---------------------------------------------------------------------
-- SELECT (SELECT COUNT(*) FROM house WHERE deleted_at IS NULL)          AS houses,        -- 8
--        (SELECT COUNT(*) FROM house WHERE status = 'rented' AND deleted_at IS NULL) AS rented, -- 4
--        (SELECT COUNT(*) FROM house WHERE status = 'vacant' AND deleted_at IS NULL) AS vacant, -- 4
--        (SELECT COUNT(DISTINCT community) FROM house WHERE deleted_at IS NULL)     AS communities, -- 2
--        (SELECT COUNT(*) FROM lease_contract WHERE status = 'active')  AS contracts,     -- 4
--        (SELECT COUNT(*) FROM lease_contract WHERE status = 'terminated') AS history,    -- 1
--        (SELECT SUM(forfeited_deposit) FROM lease_contract)            AS forfeited,    -- 3400.00
--        (SELECT COUNT(*) FROM member)                                  AS members,       -- 3
--        (SELECT COUNT(*) FROM member WHERE push_enabled = 1)           AS push_enabled;  -- 2
-- =====================================================================
