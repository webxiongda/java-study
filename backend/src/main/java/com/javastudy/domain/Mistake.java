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
@Table(name = "mistakes")
public class Mistake {
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
    private String symptom;

    @Lob
    @Column(nullable = false)
    private String reason;

    @Lob
    @Column(name = "fix_text", nullable = false)
    private String fix;

    @Column(name = "next_review", nullable = false)
    private String nextReview;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Mistake() {
    }

    public Mistake(User user, int chapterNo, String chapterTitle, String symptom, String reason, String fix, String nextReview) {
        this.user = user;
        this.chapterNo = chapterNo;
        this.chapterTitle = chapterTitle;
        this.symptom = symptom;
        this.reason = reason;
        this.fix = fix;
        this.nextReview = nextReview;
        this.createdAt = LocalDateTime.now();
    }
}
