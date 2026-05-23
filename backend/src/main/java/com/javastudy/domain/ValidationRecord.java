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
@Table(name = "validation_records")
public class ValidationRecord {
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

    @Column(name = "layer_name", nullable = false, length = 32)
    private String layer;

    @Lob
    @Column(nullable = false)
    private String summary;

    @Lob
    @Column(nullable = false)
    private String evidence;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ValidationRecord() {
    }

    public ValidationRecord(User user, int chapterNo, String chapterTitle, String layer, String summary, String evidence) {
        this.user = user;
        this.chapterNo = chapterNo;
        this.chapterTitle = chapterTitle;
        this.layer = layer;
        this.summary = summary;
        this.evidence = evidence;
        this.createdAt = LocalDateTime.now();
    }
}
