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
@Table(name = "check_results")
public class CheckResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "chapter_no", nullable = false)
    private int chapterNo;

    @Column(name = "chapter_title", nullable = false)
    private String chapterTitle;

    @Lob
    @Column(nullable = false)
    private String answer;

    @Column(nullable = false)
    private boolean passed;

    @Column(name = "revealed_answer", nullable = false)
    private boolean revealedAnswer;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CheckResult() {
    }

    public CheckResult(User user, int chapterNo, String chapterTitle, String answer, boolean passed) {
        this.user = user;
        this.chapterNo = chapterNo;
        this.chapterTitle = chapterTitle;
        this.answer = answer;
        this.passed = passed;
        this.createdAt = LocalDateTime.now();
    }
}
