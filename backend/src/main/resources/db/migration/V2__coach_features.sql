CREATE TABLE validation_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    chapter_no INT NOT NULL,
    chapter_title VARCHAR(255) NOT NULL,
    layer_name VARCHAR(32) NOT NULL,
    summary LONGTEXT NOT NULL,
    evidence LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_validation_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_validation_user_chapter (user_id, chapter_no)
);

CREATE TABLE review_cards (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    chapter_no INT NOT NULL,
    chapter_title VARCHAR(255) NOT NULL,
    card_type VARCHAR(32) NOT NULL,
    prompt LONGTEXT NOT NULL,
    expected LONGTEXT NOT NULL,
    due_date DATE NOT NULL,
    answered_at DATETIME(6) NULL,
    last_result VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_cards_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_cards_user_due (user_id, due_date),
    INDEX idx_cards_user_chapter (user_id, chapter_no)
);

CREATE TABLE portfolio_evidence (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    chapter_no INT NOT NULL,
    milestone VARCHAR(255) NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    body LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_portfolio_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_portfolio_user_created (user_id, created_at)
);
