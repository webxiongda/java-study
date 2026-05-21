CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    secret_hash VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL
);

CREATE TABLE user_chapter_progress (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    chapter_no INT NOT NULL,
    theory_done BOOLEAN NOT NULL DEFAULT FALSE,
    demo_done BOOLEAN NOT NULL DEFAULT FALSE,
    check_done BOOLEAN NOT NULL DEFAULT FALSE,
    project_done BOOLEAN NOT NULL DEFAULT FALSE,
    review_done BOOLEAN NOT NULL DEFAULT FALSE,
    finished_date DATE NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_progress_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_progress_user_chapter UNIQUE (user_id, chapter_no)
);

CREATE TABLE review_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    chapter_no INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    review_round VARCHAR(16) NOT NULL,
    due_date DATE NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_review_user_chapter_round UNIQUE (user_id, chapter_no, review_round)
);

CREATE TABLE notes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    chapter_no INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    body LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_notes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notes_user_chapter (user_id, chapter_no)
);

CREATE TABLE check_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    chapter_no INT NOT NULL,
    chapter_title VARCHAR(255) NOT NULL,
    answer LONGTEXT NOT NULL,
    passed BOOLEAN NOT NULL,
    revealed_answer BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_check_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_check_user_chapter (user_id, chapter_no)
);

CREATE TABLE mistakes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    chapter_no INT NOT NULL,
    chapter_title VARCHAR(255) NOT NULL,
    symptom LONGTEXT NOT NULL,
    reason LONGTEXT NOT NULL,
    fix_text LONGTEXT NOT NULL,
    next_review VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_mistakes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_mistakes_user_chapter (user_id, chapter_no)
);

CREATE TABLE study_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    chapter_no INT NULL,
    chapter_title VARCHAR(255) NULL,
    layer_name VARCHAR(32) NOT NULL,
    log_action VARCHAR(64) NOT NULL,
    minutes VARCHAR(32) NULL,
    reflection LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_logs_user_created (user_id, created_at)
);
