package com.javastudy.dto;

import java.util.List;
import java.util.Map;

public final class LearningDtos {
    private LearningDtos() {
    }

    public record ChapterMeta(
        int no,
        String title,
        String summary,
        String priority,
        String hours,
        String routeStatus,
        String folder,
        Map<String, Boolean> progress,
        String finished
    ) {
    }

    public record ChapterContent(
        int no,
        String title,
        String summary,
        String priority,
        String hours,
        String routeStatus,
        String folder,
        Map<String, Boolean> progress,
        String finished,
        Map<String, String> content
    ) {
    }

    public record SummaryDto(
        String current,
        long completed,
        int total,
        long percent,
        ChapterMeta currentChapter,
        List<ReviewTaskDto> dueReviews,
        List<StudyLogDto> recentLogs
    ) {
    }

    public record ReviewTaskDto(
        String id,
        long taskId,
        int chapterNo,
        String title,
        String round,
        String dueDate,
        String status,
        boolean due
    ) {
    }

    public record StudyLogDto(
        String date,
        String chapter,
        String layer,
        String action,
        String minutes,
        String reflection
    ) {
    }

    public record ProgressRequest(int chapterNo, String layer, boolean done) {
    }

    public record ReviewCompleteRequest(Integer chapterNo) {
    }

    public record NoteRequest(int chapterNo, String title, String body) {
    }

    public record MistakeRequest(int chapterNo, String chapterTitle, String symptom, String reason, String fix, String nextReview) {
    }

    public record CheckResultRequest(
        int chapterNo,
        String chapterTitle,
        String answer,
        boolean passed,
        String symptom,
        String reason,
        String fix
    ) {
    }

    public record CheckRevealRequest(int chapterNo, String chapterTitle) {
    }
}
