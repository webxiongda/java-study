package com.javastudy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview_questions")
public class InterviewQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(length = 255)
    private String topic;

    @Column(nullable = false, length = 8)
    private String difficulty;

    @Column(nullable = false, length = 8)
    private String frequency;

    @Lob
    @Column(nullable = false)
    private String prompt;

    @Lob
    @Column(name = "reference_answer", nullable = false)
    private String referenceAnswer;

    @Lob
    @Column(name = "follow_up")
    private String followUp;

    @Column(name = "related_chapter_no")
    private Integer relatedChapterNo;

    @Column(name = "related_chapter_title", length = 255)
    private String relatedChapterTitle;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(name = "prompt_hash", nullable = false, length = 40)
    private String promptHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected InterviewQuestion() {
    }

    public InterviewQuestion(
        String category,
        String topic,
        String difficulty,
        String frequency,
        String prompt,
        String referenceAnswer,
        String followUp,
        Integer relatedChapterNo,
        String relatedChapterTitle,
        String source,
        String promptHash
    ) {
        this.category = category;
        this.topic = topic;
        this.difficulty = difficulty;
        this.frequency = frequency;
        this.prompt = prompt;
        this.referenceAnswer = referenceAnswer;
        this.followUp = followUp;
        this.relatedChapterNo = relatedChapterNo;
        this.relatedChapterTitle = relatedChapterTitle;
        this.source = source;
        this.promptHash = promptHash;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getTopic() {
        return topic;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public String getFrequency() {
        return frequency;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getReferenceAnswer() {
        return referenceAnswer;
    }

    public String getFollowUp() {
        return followUp;
    }

    public Integer getRelatedChapterNo() {
        return relatedChapterNo;
    }

    public String getRelatedChapterTitle() {
        return relatedChapterTitle;
    }

    public String getSource() {
        return source;
    }
}
