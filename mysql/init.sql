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
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (desk_chair_id) REFERENCES desk_chair(id)
);

CREATE TABLE IF NOT EXISTS area_change_log_01 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    desk_chair_id BIGINT NOT NULL,
    old_area_id BIGINT,
    new_area_id BIGINT NOT NULL,
    change_reason VARCHAR(500),
    operator VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (desk_chair_id) REFERENCES desk_chair(id)
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
