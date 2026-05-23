package com.javastudy.service;

import com.javastudy.domain.ChapterProgress;
import com.javastudy.domain.CheckResult;
import com.javastudy.domain.Mistake;
import com.javastudy.domain.Note;
import com.javastudy.domain.PortfolioEvidence;
import com.javastudy.domain.ReviewCard;
import com.javastudy.domain.ReviewTask;
import com.javastudy.domain.StudyLog;
import com.javastudy.domain.User;
import com.javastudy.domain.ValidationRecord;
import com.javastudy.dto.LearningDtos;
import com.javastudy.repository.ChapterProgressRepository;
import com.javastudy.repository.CheckResultRepository;
import com.javastudy.repository.MistakeRepository;
import com.javastudy.repository.NoteRepository;
import com.javastudy.repository.PortfolioEvidenceRepository;
import com.javastudy.repository.ReviewCardRepository;
import com.javastudy.repository.ReviewTaskRepository;
import com.javastudy.repository.StudyLogRepository;
import com.javastudy.repository.ValidationRecordRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    private final ValidationRecordRepository validationRecordRepository;
    private final ReviewCardRepository reviewCardRepository;
    private final PortfolioEvidenceRepository portfolioEvidenceRepository;

    public LearningService(
        CourseContentService courseContentService,
        ChapterProgressRepository progressRepository,
        ReviewTaskRepository reviewTaskRepository,
        NoteRepository noteRepository,
        CheckResultRepository checkResultRepository,
        MistakeRepository mistakeRepository,
        StudyLogRepository studyLogRepository,
        ValidationRecordRepository validationRecordRepository,
        ReviewCardRepository reviewCardRepository,
        PortfolioEvidenceRepository portfolioEvidenceRepository
    ) {
        this.courseContentService = courseContentService;
        this.progressRepository = progressRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.noteRepository = noteRepository;
        this.checkResultRepository = checkResultRepository;
        this.mistakeRepository = mistakeRepository;
        this.studyLogRepository = studyLogRepository;
        this.validationRecordRepository = validationRecordRepository;
        this.reviewCardRepository = reviewCardRepository;
        this.portfolioEvidenceRepository = portfolioEvidenceRepository;
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
    public LearningDtos.TodayDto today(User user) {
        var summary = summary(user);
        var dueCards = dueCards(user);
        var focus = !summary.dueReviews().isEmpty() || !dueCards.isEmpty()
            ? "先复习，再学习新章节"
            : "推进当前章节并完成强验收";
        return new LearningDtos.TodayDto(
            focus,
            summary.currentChapter(),
            summary.dueReviews(),
            dueCards,
            List.of("30 分钟：到期复习", "90 分钟：阅读 + 费曼输出", "60 分钟：编码 / Demo / 项目证据", "30 分钟：自测与复盘"),
            List.of("理论：写 3-5 句费曼总结", "Demo：记录运行命令、结果和踩坑", "自测：先答题再看答案", "项目：留下可验证交付证据")
        );
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

    @Transactional
    public void submitValidation(User user, LearningDtos.ValidationRequest request) {
        if (request.summary() == null || request.summary().trim().length() < 12) {
            throw new IllegalArgumentException("验收总结至少 12 个字");
        }
        if (("demo".equals(request.layer()) || "project".equals(request.layer()))
            && (request.evidence() == null || request.evidence().trim().length() < 8)) {
            throw new IllegalArgumentException("Demo / 项目任务需要填写可验证证据");
        }
        var chapter = courseContentService.getChapter(request.chapterNo());
        validationRecordRepository.save(new ValidationRecord(
            user,
            request.chapterNo(),
            chapter.title(),
            request.layer(),
            request.summary().trim(),
            safe(request.evidence()).trim()
        ));
        updateProgress(user, new LearningDtos.ProgressRequest(request.chapterNo(), request.layer(), true));
        if ("project".equals(request.layer())) {
            portfolioEvidenceRepository.save(new PortfolioEvidence(user, request.chapterNo(), milestoneName(request.chapterNo(), chapter.title()), "project", request.evidence()));
        }
    }

    @Transactional(readOnly = true)
    public List<LearningDtos.ReviewTaskDto> reviews(User user) {
        return reviewTaskRepository.findByUserOrderByDueDateAscChapterNoAsc(user).stream().map(this::toReviewDto).toList();
    }

    @Transactional(readOnly = true)
    public List<LearningDtos.ReviewCardDto> dueCards(User user) {
        return reviewCardRepository.findByUserAndDueDateLessThanEqualOrderByDueDateAscChapterNoAsc(user, LocalDate.now()).stream()
            .map(this::toCardDto)
            .toList();
    }

    @Transactional
    public List<LearningDtos.ReviewCardDto> answerCard(User user, long cardId, LearningDtos.CardAnswerRequest request) {
        var card = reviewCardRepository.findByIdAndUser(cardId, user).orElseThrow();
        card.answer(request.remembered());
        reviewCardRepository.save(card);
        appendLog(user, card.getChapterNo(), card.getChapterTitle(), "card", request.remembered() ? "记住复习卡" : "复习卡需加练", "", safe(request.answer()));
        if (!request.remembered()) {
            mistakeRepository.save(new Mistake(user, card.getChapterNo(), card.getChapterTitle(), card.getPrompt(), "复习卡答错或回忆不完整", "明天重做，并补充费曼解释", card.getDueDate().toString()));
        }
        return dueCards(user);
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
            createCard(user, request.chapterNo(), request.chapterTitle(), "错题", request.symptom(), defaultValue(request.fix(), "回看理论、重做 Demo，并在下次复习复盘"), LocalDate.now().plusDays(1));
        } else {
            updateProgress(user, new LearningDtos.ProgressRequest(request.chapterNo(), "check", true));
        }
    }

    @Transactional(readOnly = true)
    public LearningDtos.PortfolioDto portfolio(User user) {
        var evidence = portfolioEvidenceRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toEvidenceDto).toList();
        return new LearningDtos.PortfolioDto(
            evidence.size(),
            List.of("Day 10 Java SE 小项目", "Day 20 Java 工具库", "Day 30 博客 DAO 层", "Day 40 博客 API v1", "Day 50 博客 API v2", "Day 60 简历与部署"),
            evidence
        );
    }

    @Transactional
    public LearningDtos.PortfolioDto savePortfolioEvidence(User user, LearningDtos.PortfolioEvidenceRequest request) {
        if (request.body() == null || request.body().trim().length() < 8) {
            throw new IllegalArgumentException("作品集证据至少 8 个字");
        }
        portfolioEvidenceRepository.save(new PortfolioEvidence(
            user,
            request.chapterNo(),
            defaultValue(request.milestone(), milestoneName(request.chapterNo(), "项目里程碑")),
            defaultValue(request.evidenceType(), "note"),
            request.body().trim()
        ));
        appendLog(user, request.chapterNo(), request.milestone(), "portfolio", "记录作品集证据", "", "");
        return portfolio(user);
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
        tasks.forEach((round, dueDate) -> {
            reviewTaskRepository.findByUserAndChapterNoAndRound(user, chapterNo, round)
                .orElseGet(() -> reviewTaskRepository.save(new ReviewTask(user, chapterNo, title, round, dueDate)));
            createCard(user, chapterNo, title, "章节复习", "%s：不用看资料，讲清 %02d %s 的核心概念、一个代码例子和一个面试追问。".formatted(round, chapterNo, title), "能讲出概念、代码和工程场景，并补充一个踩坑点。", dueDate);
        });
    }

    private void createCard(User user, int chapterNo, String title, String type, String prompt, String expected, LocalDate dueDate) {
        var cleanPrompt = defaultValue(prompt, "复盘本章核心内容");
        if (!reviewCardRepository.existsByUserAndChapterNoAndTypeAndPrompt(user, chapterNo, type, cleanPrompt)) {
            reviewCardRepository.save(new ReviewCard(user, chapterNo, title, type, cleanPrompt, defaultValue(expected, "写出自己的解释和可运行证据"), dueDate));
        }
    }

    private LearningDtos.ReviewCardDto toCardDto(ReviewCard card) {
        return new LearningDtos.ReviewCardDto(
            card.getId(),
            card.getChapterNo(),
            card.getChapterTitle(),
            card.getType(),
            card.getPrompt(),
            card.getExpected(),
            card.getDueDate().format(DATE),
            card.getLastResult()
        );
    }

    private LearningDtos.PortfolioEvidenceDto toEvidenceDto(PortfolioEvidence evidence) {
        return new LearningDtos.PortfolioEvidenceDto(
            evidence.getId(),
            evidence.getChapterNo(),
            evidence.getMilestone(),
            evidence.getEvidenceType(),
            evidence.getBody(),
            evidence.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    private String milestoneName(int chapterNo, String fallback) {
        if (chapterNo <= 10) return "Day 10 Java SE 小项目";
        if (chapterNo <= 20) return "Day 20 Java 工具库";
        if (chapterNo <= 30) return "Day 30 博客 DAO 层";
        if (chapterNo <= 40) return "Day 40 博客 API v1";
        if (chapterNo <= 50) return "Day 50 博客 API v2";
        return defaultValue(fallback, "Day 60 简历与部署");
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
