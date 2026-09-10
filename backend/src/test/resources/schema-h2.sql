CREATE TABLE reading_area (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    area_code VARCHAR(50) NOT NULL UNIQUE,
    area_name VARCHAR(100) NOT NULL,
    description CLOB,
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE desk_chair (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_code VARCHAR(50) NOT NULL UNIQUE,
    capacity INT NOT NULL DEFAULT 1,
    area_id BIGINT NOT NULL,
    dimensions VARCHAR(100),
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag_code VARCHAR(50) NOT NULL UNIQUE,
    tag_name VARCHAR(100) NOT NULL,
    tag_color VARCHAR(20) DEFAULT '#409EFF',
    description CLOB,
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE desk_chair_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    desk_chair_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (desk_chair_id, tag_id)
);

CREATE TABLE area_change_log_00 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    desk_chair_id BIGINT NOT NULL,
    old_area_id BIGINT,
    new_area_id BIGINT NOT NULL,
    change_reason VARCHAR(500),
    operator VARCHAR(100),
    batch_no VARCHAR(40),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE area_change_log_01 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    desk_chair_id BIGINT NOT NULL,
    old_area_id BIGINT,
    new_area_id BIGINT NOT NULL,
    change_reason VARCHAR(500),
    operator VARCHAR(100),
    batch_no VARCHAR(40),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE area_change_batch (
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

CREATE TABLE area_change_batch_item (
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
