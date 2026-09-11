CREATE TABLE IF NOT EXISTS reading_area (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    area_code VARCHAR(50) NOT NULL UNIQUE,
    area_name VARCHAR(100) NOT NULL,
    description CLOB,
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS desk_chair (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_code VARCHAR(50) NOT NULL UNIQUE,
    capacity INT NOT NULL DEFAULT 1,
    area_id BIGINT NOT NULL,
    dimensions VARCHAR(100),
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag_code VARCHAR(50) NOT NULL UNIQUE,
    tag_name VARCHAR(100) NOT NULL,
    tag_color VARCHAR(20) DEFAULT '#409EFF',
    description CLOB,
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS desk_chair_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    desk_chair_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (desk_chair_id, tag_id)
);

CREATE TABLE IF NOT EXISTS area_change_log_00 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    desk_chair_id BIGINT NOT NULL,
    old_area_id BIGINT,
    new_area_id BIGINT NOT NULL,
    change_reason VARCHAR(500),
    operator VARCHAR(100),
    batch_no VARCHAR(40),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS area_change_log_01 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    desk_chair_id BIGINT NOT NULL,
    old_area_id BIGINT,
    new_area_id BIGINT NOT NULL,
    change_reason VARCHAR(500),
    operator VARCHAR(100),
    batch_no VARCHAR(40),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stocktake_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(40) NOT NULL UNIQUE,
    area_id BIGINT NOT NULL,
    expected_count INT NOT NULL DEFAULT 0,
    actual_count INT NOT NULL DEFAULT 0,
    checked_count INT NOT NULL DEFAULT 0,
    diff_count INT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    operator VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stocktake_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    batch_no VARCHAR(40) NOT NULL,
    desk_chair_id BIGINT NULL,
    asset_code VARCHAR(50) NOT NULL,
    diff_type VARCHAR(20) NOT NULL,
    diff_detail VARCHAR(1000),
    book_area_id BIGINT NULL,
    book_status TINYINT NULL,
    book_tag_ids VARCHAR(500),
    book_tag_names VARCHAR(1000),
    actual_area_id BIGINT NULL,
    actual_status TINYINT NULL,
    actual_tag_ids VARCHAR(500),
    actual_tag_names VARCHAR(1000),
    check_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    handle_opinion VARCHAR(1000),
    confirmed_by VARCHAR(100),
    confirmed_at TIMESTAMP NULL,
    recheck_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stocktake_handle_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    item_id BIGINT NULL,
    handle_action VARCHAR(20) NOT NULL,
    opinion VARCHAR(1000),
    operator VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS repair_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(40) NOT NULL UNIQUE,
    desk_chair_id BIGINT NOT NULL,
    area_id BIGINT NOT NULL,
    damage_part VARCHAR(100) NOT NULL,
    urgency VARCHAR(10) NOT NULL,
    phenomenon VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reporter VARCHAR(100) NOT NULL,
    repairer VARCHAR(100) NULL,
    accept_at TIMESTAMP NULL,
    finish_at TIMESTAMP NULL,
    repair_note VARCHAR(1000) NULL,
    desk_status_after TINYINT NULL,
    handled_by VARCHAR(100) NULL,
    handled_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_repair_area_status ON repair_order (area_id, status);
CREATE INDEX IF NOT EXISTS idx_repair_desk_chair ON repair_order (desk_chair_id);
CREATE INDEX IF NOT EXISTS idx_repair_status ON repair_order (status);
