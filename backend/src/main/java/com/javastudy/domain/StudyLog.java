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
@Table(name = "study_logs")
public class StudyLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "chapter_no")
    private Integer chapterNo;

    @Column(name = "chapter_title")
    private String chapterTitle;

    @Column(name = "layer_name", nullable = false, length = 32)
    private String layer;

    @Column(name = "log_action", nullable = false, length = 64)
    private String action;

    @Column(length = 32)
    private String minutes;

    @Lob
    private String reflection;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected StudyLog() {
    }

    public StudyLog(User user, Integer chapterNo, String chapterTitle, String layer, String action, String minutes, String reflection) {
        this.user = user;
        this.chapterNo = chapterNo;
        this.chapterTitle = chapterTitle;
        this.layer = layer;
        this.action = action;
        this.minutes = minutes;
        this.reflection = reflection;
        this.createdAt = LocalDateTime.now();
    }

    public Integer getChapterNo() {
        return chapterNo;
    }

    public String getChapterTitle() {
        return chapterTitle;
    }

    public String getLayer() {
        return layer;
    }

    public String getAction() {
        return action;
    }

    public String getMinutes() {
        return minutes;
    }

    public String getReflection() {
        return reflection;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
