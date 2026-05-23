CREATE TABLE interview_questions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category VARCHAR(64) NOT NULL,
    topic VARCHAR(255) NULL,
    difficulty VARCHAR(8) NOT NULL,
    frequency VARCHAR(8) NOT NULL,
    prompt LONGTEXT NOT NULL,
    reference_answer LONGTEXT NOT NULL,
    follow_up LONGTEXT NULL,
    related_chapter_no INT NULL,
    related_chapter_title VARCHAR(255) NULL,
    source VARCHAR(16) NOT NULL,
    prompt_hash CHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_interview_prompt_hash (prompt_hash),
    INDEX idx_interview_category (category),
    INDEX idx_interview_chapter (related_chapter_no)
);

CREATE TABLE interview_attempts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    answer LONGTEXT NOT NULL,
    self_assessed VARCHAR(16) NOT NULL,
    ai_score INT NULL,
    ai_feedback LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_attempt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_attempt_question FOREIGN KEY (question_id) REFERENCES interview_questions(id) ON DELETE CASCADE,
    INDEX idx_attempt_user (user_id),
    INDEX idx_attempt_user_question (user_id, question_id)
);
