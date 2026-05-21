package com.javastudy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_chapter_progress", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "chapter_no"}))
public class ChapterProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "chapter_no", nullable = false)
    private int chapterNo;

    @Column(name = "theory_done", nullable = false)
    private boolean theoryDone;

    @Column(name = "demo_done", nullable = false)
    private boolean demoDone;

    @Column(name = "check_done", nullable = false)
    private boolean checkDone;

    @Column(name = "project_done", nullable = false)
    private boolean projectDone;

    @Column(name = "review_done", nullable = false)
    private boolean reviewDone;

    @Column(name = "finished_date")
    private LocalDate finishedDate;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ChapterProgress() {
    }

    public ChapterProgress(User user, int chapterNo) {
        this.user = user;
        this.chapterNo = chapterNo;
        this.updatedAt = LocalDateTime.now();
    }

    public int getChapterNo() {
        return chapterNo;
    }

    public boolean isTheoryDone() {
        return theoryDone;
    }

    public boolean isDemoDone() {
        return demoDone;
    }

    public boolean isCheckDone() {
        return checkDone;
    }

    public boolean isProjectDone() {
        return projectDone;
    }

    public boolean isReviewDone() {
        return reviewDone;
    }

    public LocalDate getFinishedDate() {
        return finishedDate;
    }

    public boolean coreDone() {
        return theoryDone && demoDone && checkDone && projectDone;
    }

    public void setLayer(String layer, boolean done) {
        switch (layer) {
            case "theory" -> this.theoryDone = done;
            case "demo" -> this.demoDone = done;
            case "check" -> this.checkDone = done;
            case "project" -> this.projectDone = done;
            case "review" -> this.reviewDone = done;
            default -> throw new IllegalArgumentException("Unsupported layer: " + layer);
        }
        if (coreDone() && finishedDate == null) {
            finishedDate = LocalDate.now();
        }
        if (!coreDone() && !"review".equals(layer)) {
            finishedDate = null;
        }
        updatedAt = LocalDateTime.now();
    }

    public void importState(boolean theory, boolean demo, boolean check, boolean project, boolean review, LocalDate finishedDate) {
        this.theoryDone = theory;
        this.demoDone = demo;
        this.checkDone = check;
        this.projectDone = project;
        this.reviewDone = review;
        this.finishedDate = finishedDate;
        this.updatedAt = LocalDateTime.now();
    }
}
