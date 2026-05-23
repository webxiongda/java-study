package com.javastudy.controller;

import com.javastudy.domain.User;
import com.javastudy.dto.LearningDtos;
import com.javastudy.service.LearningService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class LearningController {
    private final LearningService learningService;

    public LearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping("/summary")
    public LearningDtos.SummaryDto summary(Authentication authentication) {
        return learningService.summary(currentUser(authentication));
    }

    @GetMapping("/today")
    public LearningDtos.TodayDto today(Authentication authentication) {
        return learningService.today(currentUser(authentication));
    }

    @GetMapping("/chapters")
    public List<LearningDtos.ChapterMeta> chapters(Authentication authentication) {
        return learningService.chapters(currentUser(authentication));
    }

    @GetMapping("/chapters/{chapterNo}")
    public LearningDtos.ChapterContent chapter(Authentication authentication, @PathVariable int chapterNo) {
        return learningService.chapter(currentUser(authentication), chapterNo);
    }

    @PatchMapping("/progress")
    public Map<String, Boolean> progress(Authentication authentication, @RequestBody LearningDtos.ProgressRequest request) {
        learningService.updateProgress(currentUser(authentication), request);
        return Map.of("ok", true);
    }

    @PostMapping("/validations")
    public Map<String, Boolean> validations(Authentication authentication, @RequestBody LearningDtos.ValidationRequest request) {
        learningService.submitValidation(currentUser(authentication), request);
        return Map.of("ok", true);
    }

    @GetMapping("/reviews")
    public List<LearningDtos.ReviewTaskDto> reviews(Authentication authentication) {
        return learningService.reviews(currentUser(authentication));
    }

    @GetMapping("/cards/due")
    public List<LearningDtos.ReviewCardDto> dueCards(Authentication authentication) {
        return learningService.dueCards(currentUser(authentication));
    }

    @PostMapping("/cards/{cardId}/answer")
    public List<LearningDtos.ReviewCardDto> answerCard(Authentication authentication, @PathVariable long cardId, @RequestBody LearningDtos.CardAnswerRequest request) {
        return learningService.answerCard(currentUser(authentication), cardId, request);
    }

    @PatchMapping("/reviews")
    public List<LearningDtos.ReviewTaskDto> completeReviewsByChapter(Authentication authentication, @RequestBody LearningDtos.ReviewCompleteRequest request) {
        var user = currentUser(authentication);
        learningService.completeReview(user, null, request.chapterNo());
        return learningService.reviews(user);
    }

    @PatchMapping("/reviews/{taskId}")
    public List<LearningDtos.ReviewTaskDto> completeReview(Authentication authentication, @PathVariable long taskId) {
        var user = currentUser(authentication);
        learningService.completeReview(user, taskId, null);
        return learningService.reviews(user);
    }

    @PostMapping("/notes")
    public Map<String, Boolean> notes(Authentication authentication, @RequestBody LearningDtos.NoteRequest request) {
        learningService.saveNote(currentUser(authentication), request);
        return Map.of("ok", true);
    }

    @PostMapping("/mistakes")
    public Map<String, Boolean> mistakes(Authentication authentication, @RequestBody LearningDtos.MistakeRequest request) {
        learningService.saveMistake(currentUser(authentication), request);
        return Map.of("ok", true);
    }

    @PostMapping("/check-result")
    public Map<String, Boolean> checkResult(Authentication authentication, @RequestBody LearningDtos.CheckResultRequest request) {
        learningService.saveCheckResult(currentUser(authentication), request);
        return Map.of("ok", true);
    }

    @PostMapping("/check-reveals")
    public Map<String, Boolean> checkReveal(Authentication authentication, @RequestBody LearningDtos.CheckRevealRequest request) {
        learningService.revealAnswer(currentUser(authentication), request);
        return Map.of("ok", true);
    }

    @GetMapping("/portfolio")
    public LearningDtos.PortfolioDto portfolio(Authentication authentication) {
        return learningService.portfolio(currentUser(authentication));
    }

    @PostMapping("/portfolio/evidence")
    public LearningDtos.PortfolioDto portfolioEvidence(Authentication authentication, @RequestBody LearningDtos.PortfolioEvidenceRequest request) {
        return learningService.savePortfolioEvidence(currentUser(authentication), request);
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return user;
    }
}
