package com.javastudy.service;

import com.javastudy.domain.ChapterProgress;
import com.javastudy.domain.Mistake;
import com.javastudy.domain.Note;
import com.javastudy.domain.ReviewTask;
import com.javastudy.domain.StudyLog;
import com.javastudy.domain.User;
import com.javastudy.repository.ChapterProgressRepository;
import com.javastudy.repository.MistakeRepository;
import com.javastudy.repository.NoteRepository;
import com.javastudy.repository.ReviewTaskRepository;
import com.javastudy.repository.StudyLogRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InitialImportService {
    private final CourseContentService courseContentService;
    private final ChapterProgressRepository progressRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final MistakeRepository mistakeRepository;
    private final StudyLogRepository studyLogRepository;
    private final NoteRepository noteRepository;

    public InitialImportService(
        CourseContentService courseContentService,
        ChapterProgressRepository progressRepository,
        ReviewTaskRepository reviewTaskRepository,
        MistakeRepository mistakeRepository,
        StudyLogRepository studyLogRepository,
        NoteRepository noteRepository
    ) {
        this.courseContentService = courseContentService;
        this.progressRepository = progressRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.mistakeRepository = mistakeRepository;
        this.studyLogRepository = studyLogRepository;
        this.noteRepository = noteRepository;
    }

    public void importFor(User user) {
        importProgress(user);
        importReviewPlan(user);
        importMistakes(user);
        importStudyLog(user);
        importNotes(user);
    }

    private void importProgress(User user) {
        readLines("progress.md").stream()
            .filter(line -> line.matches("^\\|\\s*\\d{2}\\s*\\|.*"))
            .forEach(line -> {
                var cells = CourseContentService.splitRow(line);
                var progress = new ChapterProgress(user, parseInt(cells.get(1)));
                progress.importState(done(cells.get(4)), done(cells.get(5)), done(cells.get(6)), done(cells.get(7)), done(cells.get(8)), parseDate(cells.get(9)));
                progressRepository.save(progress);
            });
    }

    private void importReviewPlan(User user) {
        readLines("review-plan.md").stream()
            .filter(line -> line.matches("^\\|\\s*\\d{2}\\s+.*"))
            .forEach(line -> {
                var cells = CourseContentService.splitRow(line);
                int chapterNo = parseInt(cells.get(1).substring(0, 2));
                String title = cells.get(1).substring(3).trim();
                importReview(user, chapterNo, title, "一刷", cells.get(3), cells.get(6));
                importReview(user, chapterNo, title, "二刷", cells.get(4), cells.get(6));
                importReview(user, chapterNo, title, "三刷", cells.get(5), cells.get(6));
            });
    }

    private void importReview(User user, int chapterNo, String title, String round, String date, String status) {
        var dueDate = parseDate(date);
        if (dueDate == null) {
            return;
        }
        var task = new ReviewTask(user, chapterNo, title, round, dueDate);
        if (status != null && status.contains("✅")) {
            task.complete();
        }
        reviewTaskRepository.save(task);
    }

    private void importMistakes(User user) {
        readLines("mistakes.md").stream()
            .filter(line -> line.matches("^\\|\\s*\\d{4}-\\d{2}-\\d{2}\\s*\\|.*"))
            .forEach(line -> {
                var cells = CourseContentService.splitRow(line);
                var chapter = cells.get(2);
                int chapterNo = chapter.matches("^\\d{2}.*") ? parseInt(chapter.substring(0, 2)) : 0;
                var chapterTitle = chapter.matches("^\\d{2}-.+") ? chapter.substring(3) : chapter;
                mistakeRepository.save(new Mistake(user, chapterNo, chapterTitle, cells.get(3), cells.get(4), cells.get(5), cells.get(6)));
            });
    }

    private void importStudyLog(User user) {
        readLines("study-log.md").stream()
            .filter(line -> line.matches("^\\|\\s*\\d{4}-\\d{2}-\\d{2}\\s*\\|.*"))
            .forEach(line -> {
                var cells = CourseContentService.splitRow(line);
                var chapter = cells.get(2);
                Integer chapterNo = chapter.matches("^\\d{2}.*") ? parseInt(chapter.substring(0, 2)) : null;
                var title = chapter.matches("^\\d{2}-.+") ? chapter.substring(3) : chapter;
                studyLogRepository.save(new StudyLog(user, chapterNo, title, cells.get(3), cells.get(4), value(cells, 5), value(cells, 6)));
            });
    }

    private void importNotes(User user) {
        var notesDir = courseContentService.contentRoot().resolve("notes");
        if (!Files.isDirectory(notesDir)) {
            return;
        }
        try (var files = Files.list(notesDir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".md")).forEach(path -> {
                try {
                    var fileName = path.getFileName().toString();
                    int chapterNo = fileName.matches("^\\d{2}.*") ? parseInt(fileName.substring(0, 2)) : 0;
                    var title = fileName.replaceFirst("^\\d{2}-", "").replaceFirst("\\.md$", "");
                    noteRepository.save(new Note(user, chapterNo, title, Files.readString(path, StandardCharsets.UTF_8)));
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private List<String> readLines(String relativePath) {
        var path = courseContentService.contentRoot().resolve(relativePath);
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            return List.of();
        }
    }

    private boolean done(String cell) {
        return cell != null && (cell.contains("✅") || "done".equalsIgnoreCase(cell.trim()));
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException error) {
            return null;
        }
    }

    private int parseInt(String value) {
        return Integer.parseInt(value.trim());
    }

    private String value(List<String> cells, int index) {
        return cells.size() > index ? cells.get(index) : "";
    }
}
