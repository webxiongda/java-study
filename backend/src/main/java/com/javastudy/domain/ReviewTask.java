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
@Table(name = "review_tasks", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "chapter_no", "review_round"}))
public class ReviewTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "chapter_no", nullable = false)
    private int chapterNo;

    @Column(nullable = false)
    private String title;

    @Column(name = "review_round", nullable = false, length = 16)
    private String round;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ReviewTask() {
    }

    public ReviewTask(User user, int chapterNo, String title, String round, LocalDate dueDate) {
        this.user = user;
        this.chapterNo = chapterNo;
        this.title = title;
        this.round = round;
        this.dueDate = dueDate;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public int getChapterNo() {
        return chapterNo;
    }

    public String getTitle() {
        return title;
    }

    public String getRound() {
        return round;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public void complete() {
        completedAt = LocalDateTime.now();
    }
}
