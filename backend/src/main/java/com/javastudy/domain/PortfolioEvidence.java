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
@Table(name = "portfolio_evidence")
public class PortfolioEvidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "chapter_no", nullable = false)
    private int chapterNo;

    @Column(nullable = false)
    private String milestone;

    @Column(name = "evidence_type", nullable = false, length = 32)
    private String evidenceType;

    @Lob
    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PortfolioEvidence() {
    }

    public PortfolioEvidence(User user, int chapterNo, String milestone, String evidenceType, String body) {
        this.user = user;
        this.chapterNo = chapterNo;
        this.milestone = milestone;
        this.evidenceType = evidenceType;
        this.body = body;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public int getChapterNo() {
        return chapterNo;
    }

    public String getMilestone() {
        return milestone;
    }

    public String getEvidenceType() {
        return evidenceType;
    }

    public String getBody() {
        return body;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
