package com.javastudy.dto;

import java.util.List;

public final class InterviewDtos {
    private InterviewDtos() {
    }

    public record CategorySummary(
        String category,
        String label,
        long total,
        long attempted,
        long mastered
    ) {
    }

    public record QuestionListItem(
        long id,
        String category,
        String difficulty,
        String frequency,
        String prompt,
        Integer relatedChapterNo,
        String relatedChapterTitle,
        String source,
        boolean attempted,
        boolean mastered
    ) {
    }

    public record QuestionDetail(
        long id,
        String category,
        String difficulty,
        String frequency,
        String prompt,
        String referenceAnswer,
        String followUp,
        Integer relatedChapterNo,
        String relatedChapterTitle,
        String source,
        List<AttemptRecord> recentAttempts
    ) {
    }

    public record AttemptRecord(
        long id,
        String answer,
        String selfAssessed,
        Integer aiScore,
        String aiFeedback,
        String createdAt
    ) {
    }

    public record AttemptRequest(
        long questionId,
        String answer,
        String selfAssessed
    ) {
    }

    public record AttemptResponse(
        long attemptId,
        String selfAssessed
    ) {
    }

    public record StatsResponse(
        long totalQuestions,
        long totalAttempts,
        long masteredQuestions,
        List<CategorySummary> byCategory
    ) {
    }
}
