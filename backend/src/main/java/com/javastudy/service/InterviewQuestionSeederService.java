package com.javastudy.service;

import com.javastudy.domain.InterviewQuestion;
import com.javastudy.repository.InterviewQuestionRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewQuestionSeederService {
    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionSeederService.class);

    private static final Pattern CATEGORY_HEADER = Pattern.compile("^##\\s+([^（(\\n]+?)(?:[（(].*)?$");
    private static final Pattern QUESTION_HEADER = Pattern.compile("^###\\s+(⭐+)\\s*(.+?)$");
    private static final Pattern FOLLOW_UP_LINE = Pattern.compile("^-\\s*🔥\\s*追问[:：]?\\s*(.+)$");
    private static final Pattern Q5_BLOCK = Pattern.compile(
        "^#{2,3}\\s+Q5[^\\n]*?[：:]\\s*(.+?)(?=^#{2,3}\\s+Q|^---|\\z)",
        Pattern.DOTALL | Pattern.MULTILINE
    );
    private static final Pattern A5_BLOCK = Pattern.compile(
        "^#{2,3}\\s+A5[：:]?\\s*\\n(.+?)(?=^#{2,3}\\s+A|^---|\\z)",
        Pattern.DOTALL | Pattern.MULTILINE
    );
    private static final Pattern INLINE_ANSWER_MARKER = Pattern.compile(
        "\\*\\*参考答案[：:]?\\*\\*\\s*[:：]?\\s*"
    );

    private static final Map<String, String> CATEGORY_KEY_BY_HEADER = Map.ofEntries(
        Map.entry("Java 基础", "java-basics"),
        Map.entry("Java基础", "java-basics"),
        Map.entry("集合", "collections"),
        Map.entry("并发", "concurrency"),
        Map.entry("JVM", "jvm"),
        Map.entry("Spring / Spring Boot", "spring"),
        Map.entry("Spring", "spring"),
        Map.entry("数据库 / MyBatis", "database"),
        Map.entry("数据库", "database"),
        Map.entry("Redis", "redis"),
        Map.entry("认证 / 安全", "security"),
        Map.entry("认证", "security"),
        Map.entry("系统设计", "system-design")
    );

    private final InterviewQuestionRepository repository;
    private final CourseContentService courseContentService;

    public InterviewQuestionSeederService(InterviewQuestionRepository repository, CourseContentService courseContentService) {
        this.repository = repository;
        this.courseContentService = courseContentService;
    }

    @Transactional
    public int seedIfEmpty() {
        if (repository.count() > 0) {
            return 0;
        }
        int inserted = 0;
        inserted += seedFromBank();
        inserted += seedFromChapterQ5();
        log.info("Interview question seeder inserted {} questions.", inserted);
        return inserted;
    }

    private int seedFromBank() {
        var bankPath = courseContentService.contentRoot().resolve("interview-bank.md");
        if (!Files.exists(bankPath)) {
            log.warn("interview-bank.md not found at {}", bankPath);
            return 0;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(bankPath, StandardCharsets.UTF_8);
        } catch (IOException error) {
            log.warn("Failed to read interview-bank.md", error);
            return 0;
        }
        int inserted = 0;
        String currentCategory = null;
        String currentPrompt = null;
        String currentFrequency = null;
        StringBuilder currentAnswer = new StringBuilder();
        String currentFollowUp = null;

        for (String raw : lines) {
            String line = raw.strip();
            if (line.startsWith("## ")) {
                inserted += flushBankQuestion(currentCategory, currentPrompt, currentFrequency, currentAnswer.toString(), currentFollowUp);
                currentPrompt = null;
                currentAnswer.setLength(0);
                currentFollowUp = null;
                currentCategory = resolveBankCategory(line);
            } else if (line.startsWith("### ")) {
                var matcher = QUESTION_HEADER.matcher(line);
                if (matcher.matches()) {
                    inserted += flushBankQuestion(currentCategory, currentPrompt, currentFrequency, currentAnswer.toString(), currentFollowUp);
                    currentFrequency = mapStarsToFrequency(matcher.group(1));
                    currentPrompt = matcher.group(2).trim();
                    currentAnswer.setLength(0);
                    currentFollowUp = null;
                }
            } else if (line.startsWith("- ")) {
                var followMatch = FOLLOW_UP_LINE.matcher(line);
                if (followMatch.matches()) {
                    currentFollowUp = followMatch.group(1).trim();
                } else if (currentPrompt != null) {
                    if (currentAnswer.length() > 0) {
                        currentAnswer.append('\n');
                    }
                    currentAnswer.append(line);
                }
            } else if (!line.isEmpty() && currentPrompt != null) {
                if (currentAnswer.length() > 0) {
                    currentAnswer.append('\n');
                }
                currentAnswer.append(line);
            }
        }
        inserted += flushBankQuestion(currentCategory, currentPrompt, currentFrequency, currentAnswer.toString(), currentFollowUp);
        return inserted;
    }

    private int flushBankQuestion(String category, String prompt, String frequency, String answer, String followUp) {
        if (category == null || prompt == null || frequency == null) {
            return 0;
        }
        String trimmedAnswer = answer == null ? "" : answer.trim();
        if (trimmedAnswer.isEmpty()) {
            trimmedAnswer = "（待补充参考答案）";
        }
        return persist(
            category,
            null,
            mapFrequencyToDifficulty(frequency),
            frequency,
            prompt.trim(),
            trimmedAnswer,
            followUp,
            null,
            null,
            "BANK"
        );
    }

    private int seedFromChapterQ5() {
        int inserted = 0;
        var chapters = courseContentService.listChapters();
        for (var chapter : chapters) {
            String category = chapterToCategory(chapter.no());
            if (category == null) {
                continue;
            }
            var checkPath = courseContentService.contentRoot()
                .resolve("chapters")
                .resolve(chapter.folder())
                .resolve("03-check.md");
            if (!Files.exists(checkPath)) {
                continue;
            }
            String body;
            try {
                body = Files.readString(checkPath, StandardCharsets.UTF_8);
            } catch (IOException error) {
                continue;
            }
            Matcher qm = Q5_BLOCK.matcher(body);
            if (!qm.find()) {
                continue;
            }
            String q5Block = qm.group(1).trim();
            String prompt;
            String answer;
            Matcher inlineAnswer = INLINE_ANSWER_MARKER.matcher(q5Block);
            if (inlineAnswer.find()) {
                prompt = firstSentence(q5Block.substring(0, inlineAnswer.start()));
                answer = q5Block.substring(inlineAnswer.end()).trim();
            } else {
                prompt = firstSentence(q5Block);
                Matcher am = A5_BLOCK.matcher(body);
                answer = am.find() ? am.group(1).trim() : "（章节自测参考答案，详见原章节）";
            }
            if (answer.isBlank()) {
                answer = "（章节自测参考答案，详见原章节）";
            }
            inserted += persist(
                category,
                null,
                "L2",
                "MEDIUM",
                prompt,
                answer,
                null,
                chapter.no(),
                chapter.title(),
                "CHECK_Q5"
            );
        }
        return inserted;
    }

    private int persist(
        String category,
        String topic,
        String difficulty,
        String frequency,
        String prompt,
        String referenceAnswer,
        String followUp,
        Integer chapterNo,
        String chapterTitle,
        String source
    ) {
        String hash = sha1(prompt);
        if (repository.findByPromptHash(hash).isPresent()) {
            return 0;
        }
        repository.save(new InterviewQuestion(
            category,
            topic,
            difficulty,
            frequency,
            prompt,
            referenceAnswer,
            followUp,
            chapterNo,
            chapterTitle,
            source,
            hash
        ));
        return 1;
    }

    private String resolveBankCategory(String headerLine) {
        var matcher = CATEGORY_HEADER.matcher(headerLine);
        if (!matcher.matches()) {
            return null;
        }
        String headerName = matcher.group(1).trim();
        for (var entry : CATEGORY_KEY_BY_HEADER.entrySet()) {
            if (headerName.equals(entry.getKey()) || headerName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String mapStarsToFrequency(String stars) {
        return switch (stars.length()) {
            case 3 -> "HIGH";
            case 2 -> "MEDIUM";
            default -> "LOW";
        };
    }

    private String mapFrequencyToDifficulty(String frequency) {
        return switch (frequency) {
            case "HIGH" -> "L1";
            case "MEDIUM" -> "L2";
            default -> "L3";
        };
    }

    private String chapterToCategory(int no) {
        if (no >= 2 && no <= 9) return "java-basics";
        if (no >= 11 && no <= 14) return "collections";
        if (no >= 16 && no <= 19) return "java-basics";
        if (no >= 24 && no <= 29) return "database";
        if (no >= 31 && no <= 39) return "spring";
        if (no >= 41 && no <= 45) return "security";
        if (no == 46 || no == 47) return "redis";
        if (no == 48) return "mq";
        if (no >= 51 && no <= 53) return "concurrency";
        if (no == 54) return "jvm";
        if (no == 59) return "system-design";
        return null;
    }

    private String firstSentence(String text) {
        String collapsed = text.replaceAll("\\s+", " ").trim();
        if (collapsed.length() <= 280) {
            return collapsed;
        }
        return collapsed.substring(0, 280) + "…";
    }

    private String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(input.trim().toLowerCase().replaceAll("\\s+", " ").getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-1 not available", error);
        }
    }

}
