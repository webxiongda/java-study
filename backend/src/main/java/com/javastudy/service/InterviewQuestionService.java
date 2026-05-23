package com.javastudy.service;

import com.javastudy.domain.InterviewAttempt;
import com.javastudy.domain.InterviewQuestion;
import com.javastudy.domain.User;
import com.javastudy.dto.InterviewDtos;
import com.javastudy.repository.InterviewAttemptRepository;
import com.javastudy.repository.InterviewQuestionRepository;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class InterviewQuestionService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final Map<String, String> CATEGORY_LABELS = Map.ofEntries(
        Map.entry("java-basics", "Java 基础"),
        Map.entry("collections", "集合"),
        Map.entry("concurrency", "并发"),
        Map.entry("jvm", "JVM"),
        Map.entry("spring", "Spring / Spring Boot"),
        Map.entry("database", "数据库 / MyBatis"),
        Map.entry("redis", "Redis"),
        Map.entry("mq", "消息队列"),
        Map.entry("security", "认证 / 安全"),
        Map.entry("system-design", "系统设计")
    );

    private static final Map<String, Integer> FREQUENCY_ORDER = Map.of(
        "HIGH", 0,
        "MEDIUM", 1,
        "LOW", 2
    );

    private final InterviewQuestionRepository questionRepository;
    private final InterviewAttemptRepository attemptRepository;

    public InterviewQuestionService(InterviewQuestionRepository questionRepository, InterviewAttemptRepository attemptRepository) {
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
    }

    @Transactional(readOnly = true)
    public List<InterviewDtos.CategorySummary> categories(User user) {
        var counts = questionRepository.countByCategory();
        Map<String, MasteryStats> masteryByCategory = computeMasteryByCategory(user);
        return counts.stream()
            .map(c -> {
                var stats = masteryByCategory.getOrDefault(c.getCategory(), MasteryStats.EMPTY);
                return new InterviewDtos.CategorySummary(
                    c.getCategory(),
                    CATEGORY_LABELS.getOrDefault(c.getCategory(), c.getCategory()),
                    c.getTotal(),
                    stats.attempted(),
                    stats.mastered()
                );
            })
            .sorted(Comparator.comparingLong(InterviewDtos.CategorySummary::total).reversed())
            .toList();
    }

    @Transactional(readOnly = true)
    public List<InterviewDtos.QuestionListItem> questions(User user, String category, String difficulty, String frequency, String keyword) {
        var attempts = attemptRepository.findByUserOrderByCreatedAtDesc(user);
        Set<Long> attemptedIds = new HashSet<>();
        Set<Long> masteredIds = new HashSet<>();
        for (var attempt : attempts) {
            attemptedIds.add(attempt.getQuestion().getId());
            if ("mastered".equals(attempt.getSelfAssessed())) {
                masteredIds.add(attempt.getQuestion().getId());
            }
        }
        return questionRepository.findAll().stream()
            .filter(q -> category == null || category.isBlank() || category.equals(q.getCategory()))
            .filter(q -> difficulty == null || difficulty.isBlank() || difficulty.equals(q.getDifficulty()))
            .filter(q -> frequency == null || frequency.isBlank() || frequency.equals(q.getFrequency()))
            .filter(q -> keyword == null || keyword.isBlank() || q.getPrompt().toLowerCase().contains(keyword.toLowerCase()))
            .sorted(Comparator.comparingInt((InterviewQuestion q) -> FREQUENCY_ORDER.getOrDefault(q.getFrequency(), 9))
                .thenComparing(InterviewQuestion::getId))
            .map(q -> new InterviewDtos.QuestionListItem(
                q.getId(),
                q.getCategory(),
                q.getDifficulty(),
                q.getFrequency(),
                q.getPrompt(),
                q.getRelatedChapterNo(),
                q.getRelatedChapterTitle(),
                q.getSource(),
                attemptedIds.contains(q.getId()),
                masteredIds.contains(q.getId())
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public InterviewDtos.QuestionDetail question(User user, long id) {
        var question = questionRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在"));
        var recent = attemptRepository.findByUserOrderByCreatedAtDesc(user).stream()
            .filter(a -> a.getQuestion().getId().equals(id))
            .limit(5)
            .map(this::toAttemptRecord)
            .toList();
        return new InterviewDtos.QuestionDetail(
            question.getId(),
            question.getCategory(),
            question.getDifficulty(),
            question.getFrequency(),
            question.getPrompt(),
            question.getReferenceAnswer(),
            question.getFollowUp(),
            question.getRelatedChapterNo(),
            question.getRelatedChapterTitle(),
            question.getSource(),
            recent
        );
    }

    @Transactional
    public InterviewDtos.AttemptResponse submitAttempt(User user, InterviewDtos.AttemptRequest request) {
        if (request.answer() == null || request.answer().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "答案不能为空");
        }
        var selfAssessed = normalizeSelfAssessed(request.selfAssessed());
        var question = questionRepository.findById(request.questionId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在"));
        var attempt = attemptRepository.save(new InterviewAttempt(user, question, request.answer().trim(), selfAssessed));
        return new InterviewDtos.AttemptResponse(attempt.getId(), selfAssessed);
    }

    @Transactional(readOnly = true)
    public InterviewDtos.StatsResponse stats(User user) {
        long total = questionRepository.count();
        long totalAttempts = attemptRepository.countByUser(user);
        long mastered = attemptRepository.countMasteredQuestions(user);
        return new InterviewDtos.StatsResponse(total, totalAttempts, mastered, categories(user));
    }

    private Map<String, MasteryStats> computeMasteryByCategory(User user) {
        var attempts = attemptRepository.findByUserOrderByCreatedAtDesc(user);
        Map<String, Set<Long>> attemptedByCat = new HashMap<>();
        Map<String, Set<Long>> masteredByCat = new HashMap<>();
        for (var attempt : attempts) {
            var cat = attempt.getQuestion().getCategory();
            attemptedByCat.computeIfAbsent(cat, k -> new HashSet<>()).add(attempt.getQuestion().getId());
            if ("mastered".equals(attempt.getSelfAssessed())) {
                masteredByCat.computeIfAbsent(cat, k -> new HashSet<>()).add(attempt.getQuestion().getId());
            }
        }
        Map<String, MasteryStats> result = new LinkedHashMap<>();
        for (var cat : attemptedByCat.keySet()) {
            result.put(cat, new MasteryStats(
                attemptedByCat.get(cat).size(),
                masteredByCat.getOrDefault(cat, Set.of()).size()
            ));
        }
        return result;
    }

    private InterviewDtos.AttemptRecord toAttemptRecord(InterviewAttempt attempt) {
        return new InterviewDtos.AttemptRecord(
            attempt.getId(),
            attempt.getAnswer(),
            attempt.getSelfAssessed(),
            attempt.getAiScore(),
            attempt.getAiFeedback(),
            attempt.getCreatedAt().format(DATE_FORMAT)
        );
    }

    private String normalizeSelfAssessed(String value) {
        if (value == null) {
            return "practiced";
        }
        return switch (value.trim().toLowerCase()) {
            case "mastered", "passed", "ok" -> "mastered";
            case "again", "failed", "retry" -> "again";
            default -> "practiced";
        };
    }

    private record MasteryStats(long attempted, long mastered) {
        static final MasteryStats EMPTY = new MasteryStats(0, 0);
    }
}
