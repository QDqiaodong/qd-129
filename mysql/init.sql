SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS reading_area (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    area_code VARCHAR(50) NOT NULL UNIQUE,
    area_name VARCHAR(100) NOT NULL,
    description TEXT,
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS desk_chair (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_code VARCHAR(50) NOT NULL UNIQUE,
    capacity INT NOT NULL DEFAULT 1,
    area_id BIGINT NOT NULL,
    dimensions VARCHAR(100),
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (area_id) REFERENCES reading_area(id)
);

CREATE TABLE IF NOT EXISTS tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag_code VARCHAR(50) NOT NULL UNIQUE,
    tag_name VARCHAR(100) NOT NULL,
    tag_color VARCHAR(20) DEFAULT '#409EFF',
    description TEXT,
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS desk_chair_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    desk_chair_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (desk_chair_id) REFERENCES desk_chair(id),
    FOREIGN KEY (tag_id) REFERENCES tag(id),
    UNIQUE KEY uk_desk_tag (desk_chair_id, tag_id)
);

CREATE TABLE IF NOT EXISTS area_change_log_00 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    desk_chair_id BIGINT NOT NULL,
    old_area_id BIGINT,
    new_area_id BIGINT NOT NULL,
    change_reason VARCHAR(500),
    operator VARCHAR(100),
    batch_no VARCHAR(40),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (desk_chair_id) REFERENCES desk_chair(id),
    INDEX idx_batch_no (batch_no)
);

CREATE TABLE IF NOT EXISTS area_change_log_01 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    desk_chair_id BIGINT NOT NULL,
    old_area_id BIGINT,
    new_area_id BIGINT NOT NULL,
    change_reason VARCHAR(500),
    operator VARCHAR(100),
    batch_no VARCHAR(40),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (desk_chair_id) REFERENCES desk_chair(id),
    INDEX idx_batch_no (batch_no)
);

CREATE TABLE IF NOT EXISTS area_change_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(40) NOT NULL UNIQUE,
    target_area_id BIGINT NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    fail_count INT NOT NULL DEFAULT 0,
    change_reason VARCHAR(500),
    operator VARCHAR(100),
    status VARCHAR(20) NOT NULL COMMENT 'PROCESSING/SUCCESS/FAILED',
    error_message VARCHAR(1000),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (target_area_id) REFERENCES reading_area(id)
);

CREATE TABLE IF NOT EXISTS area_change_batch_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    batch_no VARCHAR(40) NOT NULL,
    desk_chair_id BIGINT NOT NULL,
    asset_code VARCHAR(50),
    old_area_id BIGINT,
    new_area_id BIGINT,
    change_log_id BIGINT,
    status VARCHAR(20) NOT NULL COMMENT 'PENDING/SUCCESS/FAILED',
    error_message VARCHAR(1000),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (batch_id) REFERENCES area_change_batch(id),
    INDEX idx_batch_no (batch_no),
    INDEX idx_desk_chair_id (desk_chair_id)
);

CREATE TABLE IF NOT EXISTS stocktake_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(40) NOT NULL UNIQUE,
    area_id BIGINT NOT NULL,
    expected_count INT NOT NULL DEFAULT 0 COMMENT '应盘数量：建批时按分区内在册桌椅数（含停用）固化',
    actual_count INT NOT NULL DEFAULT 0 COMMENT '实盘数量：本次录入/导入唯一资产条数',
    checked_count INT NOT NULL DEFAULT 0 COMMENT '已核数量：已逐项确认的明细数',
    diff_count INT NOT NULL DEFAULT 0 COMMENT '差异数量：非一致明细数',
    remark VARCHAR(500),
    operator VARCHAR(100),
    status VARCHAR(20) NOT NULL COMMENT 'OPEN/COMPLETED',
    completed_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (area_id) REFERENCES reading_area(id),
    INDEX idx_area_status (area_id, status)
);

CREATE TABLE IF NOT EXISTS repair_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(40) NOT NULL UNIQUE COMMENT '报修单号 BX+时间戳+随机串',
    desk_chair_id BIGINT NOT NULL,
    area_id BIGINT NOT NULL COMMENT '报修时所属分区快照',
    damage_part VARCHAR(100) NOT NULL COMMENT '损坏部位，如桌面/椅腿',
    urgency VARCHAR(10) NOT NULL COMMENT 'LOW/NORMAL/HIGH/URGENT 紧急程度',
    phenomenon VARCHAR(1000) NOT NULL COMMENT '损坏现象描述',
    status VARCHAR(20) NOT NULL COMMENT 'PENDING待接单/IN_PROGRESS维修中/FIXED已修复/UNFIXABLE无法修复',
    reporter VARCHAR(100) NOT NULL COMMENT '报修人',
    repairer VARCHAR(100) NULL COMMENT '接单处理人',
    accept_at DATETIME NULL COMMENT '接单时间',
    finish_at DATETIME NULL COMMENT '修复/判定无法修复时间',
    repair_note VARCHAR(1000) NULL COMMENT '维修说明/无法修复原因',
    desk_status_after TINYINT NULL COMMENT '闭环后处置：1恢复可用/0转停用',
    handled_by VARCHAR(100) NULL COMMENT '恢复/停用处置人',
    handled_at DATETIME NULL COMMENT '处置时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (desk_chair_id) REFERENCES desk_chair(id),
    FOREIGN KEY (area_id) REFERENCES reading_area(id),
    INDEX idx_area_status (area_id, status),
    INDEX idx_desk_chair_id (desk_chair_id),
    INDEX idx_status (status)
);

CREATE TABLE IF NOT EXISTS stocktake_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    batch_no VARCHAR(40) NOT NULL,
    desk_chair_id BIGINT NULL,
    asset_code VARCHAR(50) NOT NULL,
    diff_type VARCHAR(20) NOT NULL COMMENT 'MATCH/MISSING/SURPLUS/WRONG_AREA/STATUS_MISMATCH/TAG_MISMATCH',
    diff_detail VARCHAR(1000),
    book_area_id BIGINT NULL,
    book_status TINYINT NULL,
    book_tag_ids VARCHAR(500),
    book_tag_names VARCHAR(1000),
    actual_area_id BIGINT NULL,
    actual_status TINYINT NULL,
    actual_tag_ids VARCHAR(500),
    actual_tag_names VARCHAR(1000),
    check_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED',
    handle_opinion VARCHAR(1000),
    confirmed_by VARCHAR(100),
    confirmed_at DATETIME NULL,
    recheck_count INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (batch_id) REFERENCES stocktake_batch(id),
    INDEX idx_batch_no (batch_no),
    INDEX idx_asset_code (asset_code)
);

CREATE TABLE IF NOT EXISTS stocktake_handle_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    item_id BIGINT NULL,
    handle_action VARCHAR(20) NOT NULL COMMENT 'SUBMIT/CONFIRM/RECHECK/COMPLETE',
    opinion VARCHAR(1000),
    operator VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (batch_id) REFERENCES stocktake_batch(id),
    INDEX idx_batch_id (batch_id),
    INDEX idx_item_id (item_id)
);

CREATE TABLE IF NOT EXISTS lost_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_no VARCHAR(40) NOT NULL UNIQUE COMMENT '遗失登记单号 YW+时间戳+随机串',
    area_id BIGINT NOT NULL COMMENT '登记时所属阅览分区快照',
    desk_chair_id BIGINT NOT NULL COMMENT '捡到位置对应的桌椅',
    asset_code VARCHAR(50) NOT NULL COMMENT '桌椅资产编号快照',
    item_name VARCHAR(200) NOT NULL COMMENT '物品名称',
    storage_location VARCHAR(200) NOT NULL COMMENT '暂存位置',
    status VARCHAR(20) NOT NULL COMMENT 'PENDING待领取/CLAIMED已领取',
    remark VARCHAR(500),
    found_by VARCHAR(100) NOT NULL COMMENT '登记值班员',
    claimer_name VARCHAR(100) NULL COMMENT '领取人',
    claimer_verify VARCHAR(200) NULL COMMENT '领取人核验信息（证件号/学工号等）',
    claim_conclusion VARCHAR(500) NULL COMMENT '领取结论',
    claimed_by VARCHAR(100) NULL COMMENT '领取经办值班员',
    claimed_at DATETIME NULL COMMENT '领取时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (area_id) REFERENCES reading_area(id),
    FOREIGN KEY (desk_chair_id) REFERENCES desk_chair(id),
    INDEX idx_area_status (area_id, status),
    INDEX idx_desk_chair_id (desk_chair_id),
    INDEX idx_status (status)
);

CREATE TABLE IF NOT EXISTS seat_hold_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(40) NOT NULL UNIQUE COMMENT '占座批次号 ZZ+时间戳+随机串',
    area_id BIGINT NOT NULL COMMENT '值班员开批时选定的阅览分区',
    time_slot VARCHAR(100) NOT NULL COMMENT '高峰时段，如 08:00-11:30 / 午间高峰',
    total_count INT NOT NULL DEFAULT 0 COMMENT '本批勾选占住的资产总数',
    held_count INT NOT NULL DEFAULT 0 COMMENT '在占数量：明细状态 HOLDING',
    timeout_count INT NOT NULL DEFAULT 0 COMMENT '超时未到数量：明细状态 TIMEOUT',
    released_count INT NOT NULL DEFAULT 0 COMMENT '已释放数量：明细状态 RELEASED',
    remark VARCHAR(500),
    operator VARCHAR(100) NOT NULL COMMENT '开批值班员',
    status VARCHAR(20) NOT NULL COMMENT 'OPEN进行中/ENDED已结束',
    ended_at DATETIME NULL COMMENT '整批结束时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (area_id) REFERENCES reading_area(id),
    INDEX idx_area_status (area_id, status),
    INDEX idx_status (status)
);

CREATE TABLE IF NOT EXISTS seat_hold_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    batch_no VARCHAR(40) NOT NULL,
    desk_chair_id BIGINT NOT NULL,
    asset_code VARCHAR(50) NOT NULL COMMENT '资产编号快照',
    area_id BIGINT NOT NULL COMMENT '勾选时所属分区快照',
    item_status VARCHAR(20) NOT NULL COMMENT 'HOLDING在占/TIMEOUT超时未到/RELEASED已释放',
    previous_desk_status TINYINT NOT NULL DEFAULT 1 COMMENT '占住前桌椅启用状态，释放时按此恢复',
    released_by VARCHAR(100) NULL COMMENT '释放操作人',
    released_at DATETIME NULL COMMENT '释放时间',
    timeout_by VARCHAR(100) NULL COMMENT '标记超时未到操作人',
    timeout_at DATETIME NULL COMMENT '标记超时未到时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (batch_id) REFERENCES seat_hold_batch(id),
    INDEX idx_batch_id (batch_id),
    INDEX idx_batch_no (batch_no),
    INDEX idx_desk_chair_id (desk_chair_id),
    INDEX idx_item_status (item_status)
);

INSERT INTO reading_area (area_code, area_name, description) VALUES
('A001', '第一阅览区', '主馆一层东侧，自然科学类'),
('A002', '第二阅览区', '主馆一层西侧，社会科学类'),
('B001', '第三阅览区', '主馆二层东侧，文学艺术类'),
('B002', '第四阅览区', '主馆二层西侧，外文原版类'),
('C001', '考研专区', '副馆一层，考研复习专用');

INSERT INTO tag (tag_code, tag_name, tag_color, description) VALUES 
('TAG001', '自习专用', '#409EFF', '仅供自习使用'),
('TAG002', '考研专区', '#67C23A', '考研复习专用'),
('TAG003', '临时阅览', '#E6A23C', '临时阅览座位'),
('TAG004', '双人桌', '#F56C6C', '可容纳两人'),
('TAG005', '四人桌', '#909399', '可容纳四人');

INSERT INTO desk_chair (asset_code, capacity, area_id, dimensions) VALUES 
('DC001', 2, 1, '120x60x75cm'),
('DC002', 2, 1, '120x60x75cm'),
('DC003', 4, 1, '160x80x75cm'),
('DC004', 1, 2, '80x50x75cm'),
('DC005', 2, 2, '120x60x75cm'),
('DC006', 2, 3, '120x60x75cm'),
('DC007', 4, 3, '160x80x75cm'),
('DC008', 1, 4, '80x50x75cm'),
('DC009', 2, 4, '120x60x75cm'),
('DC010', 2, 5, '120x60x75cm'),
('DC011', 1, 5, '80x50x75cm'),
('DC012', 2, 5, '120x60x75cm');

INSERT INTO desk_chair_tag (desk_chair_id, tag_id) VALUES 
(1, 1), (1, 4),
(2, 1), (2, 4),
(3, 1), (3, 5),
(4, 1),
(5, 1), (5, 4),
(6, 1), (6, 4),
(7, 1), (7, 5),
(8, 1),
(9, 1), (9, 4),
(10, 2), (10, 4),
(11, 2),
(12, 2), (12, 4);
