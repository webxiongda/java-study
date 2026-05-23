package com.javastudy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview_attempts")
public class InterviewAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id")
    private InterviewQuestion question;

    @Lob
    @Column(nullable = false)
    private String answer;

    @Column(name = "self_assessed", nullable = false, length = 16)
    private String selfAssessed;

    @Column(name = "ai_score")
    private Integer aiScore;

    @Lob
    @Column(name = "ai_feedback")
    private String aiFeedback;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected InterviewAttempt() {
    }

    public InterviewAttempt(User user, InterviewQuestion question, String answer, String selfAssessed) {
        this.user = user;
        this.question = question;
        this.answer = answer;
        this.selfAssessed = selfAssessed;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public InterviewQuestion getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public String getSelfAssessed() {
        return selfAssessed;
    }

    public Integer getAiScore() {
        return aiScore;
    }

    public String getAiFeedback() {
        return aiFeedback;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
