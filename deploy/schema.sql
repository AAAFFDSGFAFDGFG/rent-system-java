-- =====================================================================
-- 家·房源 | MySQL 8.x 建表脚本（审核门 2 产物）
-- 依据：database-design.md（门 1 定稿，第 3 版）
-- 数据库：rent_system / utf8mb4 / utf8mb4_0900_ai_ci / InnoDB
-- ---------------------------------------------------------------------
-- 【重复执行策略】
--   1) 本脚本全部使用 CREATE DATABASE IF NOT EXISTS / CREATE TABLE IF NOT EXISTS，
--      可重复执行，不会破坏已有数据。
--   2) 注意：IF NOT EXISTS 只判断「表是否存在」，不会同步已存在表的结构变更；
--      后续字段调整请另写 ALTER 迁移脚本，不要直接改本文件重跑。
--   3) 开发环境需要「彻底重建」时，取消文件末尾【可选：重建区块】的注释后执行，
--      该区块会 DROP 全部表并清空数据，生产环境严禁使用。
-- ---------------------------------------------------------------------
-- 【外键说明】所有外键仅保留 ON DELETE RESTRICT（禁止级联删除），
--   不使用 ON UPDATE CASCADE：主键均为自增、业务上永不更新；
--   且 MySQL 26.x 对「子表含 STORED 生成列 + ON UPDATE CASCADE」的组合直接拒绝建表（实测 ERROR 1215）。
-- ---------------------------------------------------------------------
-- 【索引策略】按审核意见「只保留必要的」：
--   仅保留 主键 / 业务唯一约束 / 外键(InnoDB 自动建索引) / 状态机必需的生成列唯一约束；
--   不创建任何为加速查询而设计的普通或联合索引（数据量仅几十套房源）。
-- ---------------------------------------------------------------------
-- 【敏感字段】member 的 SendKey 不存明文：
--   send_key_cipher = AES-GCM 密文（密钥来自部署机环境变量，不入库）
--   send_key_hash   = HMAC-SHA256 校验值（登录比对用，不可逆）
--   send_key_tail   = 明文尾四位（界面脱敏展示用，非机密）
--   种子数据见 seed-data.sql，均为占位值，部署前必须替换。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS rent_system
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE rent_system;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. house 房源（无依赖，最先建）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS house (
  id                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '房源ID',
  community             VARCHAR(64)      NOT NULL COMMENT '小区',
  building              VARCHAR(32)      NOT NULL COMMENT '楼栋',
  floor_no              SMALLINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '楼层',
  room_no               VARCHAR(8)       NOT NULL COMMENT '房号（字母 A/B）',
  layout                VARCHAR(32)      NOT NULL DEFAULT '两室一厅' COMMENT '户型',
  area                  DECIMAL(6,2)     NOT NULL DEFAULT 60.00 COMMENT '面积（㎡）',
  face                  VARCHAR(32)      NOT NULL DEFAULT '南' COMMENT '朝向',
  expected_rent         DECIMAL(10,2)    NOT NULL COMMENT '预期月租（元），空置房源展示',
  status                VARCHAR(16)      NOT NULL DEFAULT 'vacant' COMMENT 'rented / vacant',
  tags_json             JSON             NULL COMMENT '标签数组，界面不展示仅存储',
  deleted_at            DATETIME         NULL COMMENT '软删除标记；非 NULL 视为已删除',
  active_location_flag  TINYINT UNSIGNED GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED
                        COMMENT '生成列：未删除=1、已删除=NULL，供唯一约束使用',
  created_at            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_house_location (community, building, floor_no, room_no, active_location_flag),
  CONSTRAINT chk_house_status   CHECK (status IN ('rented', 'vacant')),
  CONSTRAINT chk_house_rent     CHECK (expected_rent >= 0),
  CONSTRAINT chk_house_floor    CHECK (floor_no >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '房源';

-- 唯一约束说明：未删除记录的 active_location_flag 恒为 1，
-- 因此 (小区,楼栋,楼层,房号) 只能有一条有效记录；软删除后该列为 NULL，
-- MySQL 唯一索引允许多个 NULL，可重新录入同一物理房源。

-- ---------------------------------------------------------------------
-- 2. lease_contract 租赁合同（依赖 house）
--    一对多保留历史；退租只置 terminated，不删行
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lease_contract (
  id                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '合同ID',
  house_id          BIGINT UNSIGNED  NOT NULL COMMENT '房源ID',
  actual_rent       DECIMAL(10,2)    NOT NULL COMMENT '实际月租（元），收入计算取此值',
  payment_mode      VARCHAR(32)      NOT NULL DEFAULT '押一付三' COMMENT '付款方式（自由文本）',
  deposit           DECIMAL(10,2)    NOT NULL COMMENT '押金（元）',
  start_date        DATE             NOT NULL COMMENT '起租日',
  end_date          DATE             NOT NULL COMMENT '到期日',
  pay_day           TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '每月付款日，1-31',
  electric_start    DECIMAL(12,1)    NULL COMMENT '入住电表度数',
  water_start       DECIMAL(12,1)    NULL COMMENT '用水起始吨数',
  tenant_name       VARCHAR(64)      NOT NULL COMMENT '租客姓名',
  tenant_phone      VARCHAR(32)      NULL COMMENT '租客电话',
  note              VARCHAR(255)     NULL COMMENT '备注',
  status            VARCHAR(16)      NOT NULL DEFAULT 'active' COMMENT 'active / terminated',
  terminated_at     DATETIME         NULL COMMENT '合同终止日（退租当天 / 续租换约当天）；计租上界为此日前一天',
  end_reason        VARCHAR(16)      NULL COMMENT '终止原因：expired 到期退租 / breach 提前违约 / renewed 续租换约',
  forfeited_deposit DECIMAL(10,2)    NOT NULL DEFAULT 0.00 COMMENT '没收押金；breach 时=deposit，其余=0',
  predecessor_id    BIGINT UNSIGNED  NULL COMMENT '续租来源合同ID（首次签约 NULL）',
  active_house_id   BIGINT UNSIGNED  GENERATED ALWAYS AS (IF(status = 'active', house_id, NULL)) STORED
                    COMMENT '生成列：仅生效合同取 house_id，保证一房一生效合同',
  created_at        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_contract_active (active_house_id),
  CONSTRAINT fk_contract_house FOREIGN KEY (house_id) REFERENCES house (id) ON DELETE RESTRICT,
  CONSTRAINT fk_contract_predecessor FOREIGN KEY (predecessor_id) REFERENCES lease_contract (id) ON DELETE RESTRICT,
  CONSTRAINT chk_contract_status  CHECK (status IN ('active', 'terminated')),
  CONSTRAINT chk_contract_reason  CHECK (end_reason IS NULL OR end_reason IN ('expired', 'breach', 'renewed')),
  CONSTRAINT chk_contract_forfeit CHECK (forfeited_deposit >= 0),
  CONSTRAINT chk_contract_rent    CHECK (actual_rent > 0),
  CONSTRAINT chk_contract_deposit CHECK (deposit >= 0),
  CONSTRAINT chk_contract_dates   CHECK (end_date > start_date),
  CONSTRAINT chk_contract_payday  CHECK (pay_day BETWEEN 1 AND 31)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '租赁合同（保留历史，含续租与违约）';

-- ---------------------------------------------------------------------
-- 3. member 家庭成员（无依赖）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS member (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '成员ID',
  username         VARCHAR(32)     NOT NULL COMMENT '用户名，登录与展示用',
  phone            VARCHAR(32)     NULL COMMENT '成员手机',
  send_key_cipher  VARCHAR(512)    NOT NULL COMMENT 'SendKey AES-GCM 密文',
  send_key_hash    CHAR(64)        NOT NULL COMMENT 'SendKey HMAC-SHA256 校验值',
  send_key_tail    VARCHAR(4)      NOT NULL COMMENT 'SendKey 尾四位，脱敏展示',
  push_enabled     TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '本人是否接收每日推送',
  digest_send_time TIME            NOT NULL DEFAULT '09:00' COMMENT '本人每日聚合日报推送时间（每成员独立，可整点修改）',
  created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_username (username),
  CONSTRAINT chk_member_push CHECK (push_enabled IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '家庭成员';

-- ---------------------------------------------------------------------
-- 4. digest_template 每日分发文案模板（无依赖）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS digest_template (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  name           VARCHAR(64)     NOT NULL DEFAULT 'default' COMMENT '模板名',
  content_text   TEXT            NOT NULL COMMENT '模板正文（5 个变量，无人物信息）',
  variables_json JSON            NULL COMMENT '支持的变量列表',
  is_default     TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否为默认模板',
  created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_template_name (name),
  CONSTRAINT chk_template_default CHECK (is_default IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '每日分发文案模板';

-- ---------------------------------------------------------------------
-- 5. digest_report 聚合日报（依赖 digest_template）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS digest_report (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日报ID',
  report_date         DATE            NOT NULL COMMENT '日报日期',
  digest_type         VARCHAR(16)     NOT NULL DEFAULT 'daily' COMMENT '汇总类型',
  template_id         BIGINT UNSIGNED NULL COMMENT '渲染所用模板',
  content_text        TEXT            NOT NULL COMMENT '按模板渲染后的最终正文',
  vacant_snapshot     JSON            NULL COMMENT '空置房源清单快照',
  upcoming_snapshot   JSON            NULL COMMENT '临期事项清单快照',
  channel             VARCHAR(32)     NOT NULL DEFAULT 'serverchan' COMMENT '默认推送通道',
  idempotency_key     VARCHAR(128)    NOT NULL COMMENT '幂等键 = 日期 + 汇总类型',
  status              VARCHAR(16)     NOT NULL DEFAULT 'generated' COMMENT 'generated / dispatched / partial',
  created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_digest (report_date, digest_type),
  CONSTRAINT fk_digest_template FOREIGN KEY (template_id) REFERENCES digest_template (id) ON DELETE RESTRICT,
  CONSTRAINT chk_digest_status CHECK (status IN ('generated', 'dispatched', 'partial'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '聚合日报';

-- 幂等说明：uk_digest(report_date, digest_type) 本身就是「一天一份正文」的硬保证；
-- idempotency_key 作为列保留供外部追溯与日志对齐，按「只保留必要索引」不再单独建唯一索引。

-- ---------------------------------------------------------------------
-- 6. notification_record 发送记录（依赖 digest_report / member）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_record (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  digest_id        BIGINT UNSIGNED NOT NULL COMMENT '所属日报',
  member_id        BIGINT UNSIGNED NOT NULL COMMENT '接收成员',
  report_date      DATE            NOT NULL COMMENT '日报日期',
  digest_type      VARCHAR(16)     NOT NULL DEFAULT 'daily' COMMENT '汇总类型',
  channel          VARCHAR(32)     NOT NULL DEFAULT 'serverchan' COMMENT '实际通道',
  status           VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT 'pending / success / degraded / failed',
  retry_count      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '重试次数',
  error_message    VARCHAR(512)    NULL COMMENT '失败原因（禁止含 SendKey）',
  sent_at          DATETIME        NULL COMMENT '实际发送时间',
  idempotency_key  VARCHAR(128)    NOT NULL COMMENT '幂等键 = 日期 + 成员 + 汇总类型',
  created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_idem (idempotency_key),
  CONSTRAINT fk_notification_digest FOREIGN KEY (digest_id) REFERENCES digest_report (id) ON DELETE RESTRICT,
  CONSTRAINT fk_notification_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT,
  CONSTRAINT chk_notification_status CHECK (status IN ('pending', 'success', 'degraded', 'failed'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '按成员分发记录';

-- ---------------------------------------------------------------------
-- 7. reminder_record 提醒记录（方案 B：已发送事项流水）
--    依赖 house / lease_contract / digest_report
--    不预生成待办池；仅在日报正文收录该事项时写入
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reminder_record (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  house_id     BIGINT UNSIGNED NOT NULL COMMENT '房源ID',
  contract_id  BIGINT UNSIGNED NULL COMMENT '关联合同',
  type         VARCHAR(16)     NOT NULL COMMENT 'pay / expire',
  plan_date    DATE            NOT NULL COMMENT '事项原计划日期',
  digest_id    BIGINT UNSIGNED NOT NULL COMMENT '由哪份日报发出',
  channel      VARCHAR(32)     NOT NULL DEFAULT 'serverchan' COMMENT '实际发送通道',
  sent_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '随日报实际发出的时间',
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reminder (digest_id, house_id, type, plan_date),
  CONSTRAINT fk_reminder_house    FOREIGN KEY (house_id)    REFERENCES house (id)          ON DELETE RESTRICT,
  CONSTRAINT fk_reminder_contract FOREIGN KEY (contract_id) REFERENCES lease_contract (id) ON DELETE RESTRICT,
  CONSTRAINT fk_reminder_digest   FOREIGN KEY (digest_id)   REFERENCES digest_report (id)  ON DELETE RESTRICT,
  CONSTRAINT chk_reminder_type CHECK (type IN ('pay', 'expire'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '已发送提醒事项流水（方案 B）';

-- 日历高亮与临期列表不查询本表，仍由 lease_contract 实时计算（与原型一致）。

-- ---------------------------------------------------------------------
-- 8. system_config 系统配置（无依赖）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS system_config (
  config_key   VARCHAR(64)  NOT NULL COMMENT '配置键',
  config_value VARCHAR(255) NOT NULL COMMENT '配置值',
  description  VARCHAR(255) NULL COMMENT '说明',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统全局配置';

-- =====================================================================
-- 建表依赖顺序：house → lease_contract → member → digest_template
--              → digest_report → notification_record → reminder_record
--              → system_config
-- 全部表均为 InnoDB / utf8mb4 / utf8mb4_0900_ai_ci。
-- 索引合计：8 个主键 + 7 个业务唯一约束 + 7 个外键自动索引 + 13 个 CHECK + 2 个生成列，
--          无任何为加速查询设计的扩展索引。
-- 校验约束：状态枚举、金额 > 0、日期先后、付款日 1-31 均在库层兜底，
--          应用层仍需做同样的校验并返回友好错误（见门 3 API 文档）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 【可选：重建区块】仅开发环境使用，会清空全部数据！
-- 取消注释后把本区块移动到文件开头（CREATE DATABASE 之后）执行。
-- ---------------------------------------------------------------------
-- SET FOREIGN_KEY_CHECKS = 0;
-- DROP TABLE IF EXISTS reminder_record;
-- DROP TABLE IF EXISTS notification_record;
-- DROP TABLE IF EXISTS digest_report;
-- DROP TABLE IF EXISTS digest_template;
-- DROP TABLE IF EXISTS lease_contract;
-- DROP TABLE IF EXISTS house;
-- DROP TABLE IF EXISTS member;
-- DROP TABLE IF EXISTS system_config;
-- SET FOREIGN_KEY_CHECKS = 1;
