package com.javastudy.controller;

import com.javastudy.domain.User;
import com.javastudy.dto.InterviewDtos;
import com.javastudy.service.InterviewQuestionService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/interview")
public class InterviewController {
    private final InterviewQuestionService service;

    public InterviewController(InterviewQuestionService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public List<InterviewDtos.CategorySummary> categories(Authentication authentication) {
        return service.categories(currentUser(authentication));
    }

    @GetMapping("/questions")
    public List<InterviewDtos.QuestionListItem> questions(
        Authentication authentication,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String difficulty,
        @RequestParam(required = false) String frequency,
        @RequestParam(required = false) String q
    ) {
        return service.questions(currentUser(authentication), category, difficulty, frequency, q);
    }

    @GetMapping("/questions/{id}")
    public InterviewDtos.QuestionDetail question(Authentication authentication, @PathVariable long id) {
        return service.question(currentUser(authentication), id);
    }

    @PostMapping("/attempts")
    public InterviewDtos.AttemptResponse attempt(Authentication authentication, @RequestBody InterviewDtos.AttemptRequest request) {
        return service.submitAttempt(currentUser(authentication), request);
    }

    @GetMapping("/stats")
    public InterviewDtos.StatsResponse stats(Authentication authentication) {
        return service.stats(currentUser(authentication));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return user;
    }
}
