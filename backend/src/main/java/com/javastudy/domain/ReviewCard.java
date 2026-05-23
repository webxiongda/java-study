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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_cards")
public class ReviewCard {
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

    @Column(name = "card_type", nullable = false, length = 32)
    private String type;

    @Lob
    @Column(nullable = false)
    private String prompt;

    @Lob
    @Column(nullable = false)
    private String expected;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "last_result", nullable = false, length = 16)
    private String lastResult;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ReviewCard() {
    }

    public ReviewCard(User user, int chapterNo, String chapterTitle, String type, String prompt, String expected, LocalDate dueDate) {
        this.user = user;
        this.chapterNo = chapterNo;
        this.chapterTitle = chapterTitle;
        this.type = type;
        this.prompt = prompt;
        this.expected = expected;
        this.dueDate = dueDate;
        this.lastResult = "pending";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public int getChapterNo() {
        return chapterNo;
    }

    public String getChapterTitle() {
        return chapterTitle;
    }

    public String getType() {
        return type;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getExpected() {
        return expected;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getLastResult() {
        return lastResult;
    }

    public void answer(boolean remembered) {
        answeredAt = LocalDateTime.now();
        lastResult = remembered ? "remembered" : "again";
        dueDate = LocalDate.now().plusDays(remembered ? 7 : 1);
    }
}
