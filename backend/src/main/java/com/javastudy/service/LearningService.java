package com.javastudy.service;

import com.javastudy.domain.ChapterProgress;
import com.javastudy.domain.CheckResult;
import com.javastudy.domain.Mistake;
import com.javastudy.domain.Note;
import com.javastudy.domain.ReviewTask;
import com.javastudy.domain.StudyLog;
import com.javastudy.domain.User;
import com.javastudy.dto.LearningDtos;
import com.javastudy.repository.ChapterProgressRepository;
import com.javastudy.repository.CheckResultRepository;
import com.javastudy.repository.MistakeRepository;
import com.javastudy.repository.NoteRepository;
import com.javastudy.repository.ReviewTaskRepository;
import com.javastudy.repository.StudyLogRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final CourseContentService courseContentService;
    private final ChapterProgressRepository progressRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final NoteRepository noteRepository;
    private final CheckResultRepository checkResultRepository;
    private final MistakeRepository mistakeRepository;
    private final StudyLogRepository studyLogRepository;

    public LearningService(
        CourseContentService courseContentService,
        ChapterProgressRepository progressRepository,
        ReviewTaskRepository reviewTaskRepository,
        NoteRepository noteRepository,
        CheckResultRepository checkResultRepository,
        MistakeRepository mistakeRepository,
        StudyLogRepository studyLogRepository
    ) {
        this.courseContentService = courseContentService;
        this.progressRepository = progressRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.noteRepository = noteRepository;
        this.checkResultRepository = checkResultRepository;
        this.mistakeRepository = mistakeRepository;
        this.studyLogRepository = studyLogRepository;
    }

    @Transactional(readOnly = true)
    public LearningDtos.SummaryDto summary(User user) {
        var chapters = chapters(user);
        long completed = chapters.stream().filter(this::coreDone).count();
        var current = chapters.stream().filter(chapter -> !coreDone(chapter)).findFirst().orElse(null);
        var currentText = current == null ? "全部完成" : "%02d-%s".formatted(current.no(), current.title());
        var dueReviews = reviews(user).stream().filter(LearningDtos.ReviewTaskDto::due).toList();
        var logs = studyLogRepository.findTop8ByUserOrderByCreatedAtDesc(user).stream().map(this::toLogDto).toList();
        long percent = chapters.isEmpty() ? 0 : Math.round(completed * 100.0 / chapters.size());
        return new LearningDtos.SummaryDto(currentText, completed, chapters.size(), percent, current, dueReviews, logs);
    }

    @Transactional(readOnly = true)
    public List<LearningDtos.ChapterMeta> chapters(User user) {
        var progressByChapter = progressRepository.findByUser(user).stream()
            .collect(Collectors.toMap(ChapterProgress::getChapterNo, Function.identity()));
        return courseContentService.listChapters().stream()
            .map(chapter -> toChapterMeta(chapter, progressByChapter.get(chapter.no())))
            .toList();
    }

    @Transactional(readOnly = true)
    public LearningDtos.ChapterContent chapter(User user, int chapterNo) {
        var courseChapter = courseContentService.getChapter(chapterNo);
        var progress = progressRepository.findByUserAndChapterNo(user, chapterNo).orElse(null);
        var meta = toChapterMeta(courseChapter, progress);
        return new LearningDtos.ChapterContent(
            meta.no(),
            meta.title(),
            meta.summary(),
            meta.priority(),
            meta.hours(),
            meta.routeStatus(),
            meta.folder(),
            meta.progress(),
            meta.finished(),
            courseContentService.readChapterContent(chapterNo)
        );
    }

    @Transactional
    public void updateProgress(User user, LearningDtos.ProgressRequest request) {
        var progress = progressRepository.findByUserAndChapterNo(user, request.chapterNo())
            .orElseGet(() -> new ChapterProgress(user, request.chapterNo()));
        var wasCoreDone = progress.coreDone();
        progress.setLayer(request.layer(), request.done());
        progressRepository.save(progress);
        var chapter = courseContentService.getChapter(request.chapterNo());
        appendLog(user, request.chapterNo(), chapter.title(), request.layer(), request.done() ? "完成" : "取消完成", "", "");
        if (!wasCoreDone && progress.coreDone() && progress.getFinishedDate() != null) {
            ensureReviewTasks(user, request.chapterNo(), chapter.title(), progress.getFinishedDate());
        }
    }

    @Transactional(readOnly = true)
    public List<LearningDtos.ReviewTaskDto> reviews(User user) {
        return reviewTaskRepository.findByUserOrderByDueDateAscChapterNoAsc(user).stream().map(this::toReviewDto).toList();
    }

    @Transactional
    public void completeReview(User user, Long taskId, Integer chapterNo) {
        if (taskId != null) {
            var task = reviewTaskRepository.findByIdAndUser(taskId, user).orElseThrow();
            task.complete();
            reviewTaskRepository.save(task);
            appendLog(user, task.getChapterNo(), task.getTitle(), "review", "完成复习", "", "");
            return;
        }
        if (chapterNo == null) {
            throw new IllegalArgumentException("taskId or chapterNo is required");
        }
        var tasks = reviewTaskRepository.findByUserAndChapterNo(user, chapterNo);
        tasks.forEach(ReviewTask::complete);
        reviewTaskRepository.saveAll(tasks);
        var progress = progressRepository.findByUserAndChapterNo(user, chapterNo).orElseGet(() -> new ChapterProgress(user, chapterNo));
        progress.setLayer("review", true);
        progressRepository.save(progress);
        var chapter = courseContentService.getChapter(chapterNo);
        appendLog(user, chapterNo, chapter.title(), "review", "完成复习", "", "");
    }

    @Transactional
    public void saveNote(User user, LearningDtos.NoteRequest request) {
        noteRepository.save(new Note(user, request.chapterNo(), request.title(), request.body()));
        appendLog(user, request.chapterNo(), request.title(), "note", "记录笔记", "", "");
    }

    @Transactional
    public void saveMistake(User user, LearningDtos.MistakeRequest request) {
        mistakeRepository.save(new Mistake(user, request.chapterNo(), request.chapterTitle(), safe(request.symptom()), safe(request.reason()), safe(request.fix()), safe(request.nextReview())));
    }

    @Transactional
    public void saveCheckResult(User user, LearningDtos.CheckResultRequest request) {
        var status = request.passed() ? "自测通过" : "自测需复习";
        checkResultRepository.save(new CheckResult(user, request.chapterNo(), request.chapterTitle(), request.answer(), request.passed()));
        noteRepository.save(new Note(user, request.chapterNo(), request.chapterTitle(), "### 自测记录\n\n**结果**：" + status + "\n\n**我的答案**：\n\n" + request.answer()));
        appendLog(user, request.chapterNo(), request.chapterTitle(), "check", status, "", "");
        if (!request.passed()) {
            mistakeRepository.save(new Mistake(
                user,
                request.chapterNo(),
                request.chapterTitle(),
                defaultValue(request.symptom(), "自测未通过"),
                defaultValue(request.reason(), "自评为需复习"),
                defaultValue(request.fix(), "回看理论、重做 Demo，并在下次复习复盘"),
                "%s / %s / %s".formatted(LocalDate.now().plusDays(3), LocalDate.now().plusDays(7), LocalDate.now().plusDays(30))
            ));
        }
    }

    @Transactional
    public void revealAnswer(User user, LearningDtos.CheckRevealRequest request) {
        appendLog(user, request.chapterNo(), request.chapterTitle(), "check", "显示参考答案", "", "");
    }

    public void appendLog(User user, Integer chapterNo, String chapterTitle, String layer, String action, String minutes, String reflection) {
        studyLogRepository.save(new StudyLog(user, chapterNo, chapterTitle, layer, action, minutes, reflection));
    }

    public void ensureReviewTasks(User user, int chapterNo, String title, LocalDate finishedDate) {
        Map<String, LocalDate> tasks = Map.of(
            "一刷", finishedDate.plusDays(3),
            "二刷", finishedDate.plusDays(7),
            "三刷", finishedDate.plusDays(30)
        );
        tasks.forEach((round, dueDate) -> reviewTaskRepository.findByUserAndChapterNoAndRound(user, chapterNo, round)
            .orElseGet(() -> reviewTaskRepository.save(new ReviewTask(user, chapterNo, title, round, dueDate))));
    }

    private LearningDtos.ChapterMeta toChapterMeta(CourseContentService.CourseChapter chapter, ChapterProgress progress) {
        var progressMap = new LinkedHashMap<String, Boolean>();
        progressMap.put("theory", progress != null && progress.isTheoryDone());
        progressMap.put("demo", progress != null && progress.isDemoDone());
        progressMap.put("check", progress != null && progress.isCheckDone());
        progressMap.put("project", progress != null && progress.isProjectDone());
        progressMap.put("review", progress != null && progress.isReviewDone());
        return new LearningDtos.ChapterMeta(
            chapter.no(),
            chapter.title(),
            chapter.summary(),
            chapter.priority(),
            chapter.hours(),
            chapter.routeStatus(),
            chapter.folder(),
            progressMap,
            progress != null && progress.getFinishedDate() != null ? progress.getFinishedDate().format(DATE) : "-"
        );
    }

    private boolean coreDone(LearningDtos.ChapterMeta chapter) {
        return Boolean.TRUE.equals(chapter.progress().get("theory"))
            && Boolean.TRUE.equals(chapter.progress().get("demo"))
            && Boolean.TRUE.equals(chapter.progress().get("check"))
            && Boolean.TRUE.equals(chapter.progress().get("project"));
    }

    private LearningDtos.ReviewTaskDto toReviewDto(ReviewTask task) {
        var due = !task.isCompleted() && !task.getDueDate().isAfter(LocalDate.now());
        return new LearningDtos.ReviewTaskDto(
            String.valueOf(task.getId()),
            task.getId(),
            task.getChapterNo(),
            task.getTitle(),
            task.getRound(),
            task.getDueDate().format(DATE),
            task.isCompleted() ? "✅ 已复习" : "⏳ 待复习",
            due
        );
    }

    private LearningDtos.StudyLogDto toLogDto(StudyLog log) {
        var chapter = log.getChapterNo() == null ? "" : "%02d-%s".formatted(log.getChapterNo(), safe(log.getChapterTitle()));
        return new LearningDtos.StudyLogDto(
            log.getCreatedAt().toLocalDate().format(DATE),
            chapter,
            log.getLayer(),
            log.getAction(),
            safe(log.getMinutes()),
            safe(log.getReflection())
        );
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
