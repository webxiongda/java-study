package com.javastudy.repository;

import com.javastudy.domain.ReviewCard;
import com.javastudy.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewCardRepository extends JpaRepository<ReviewCard, Long> {
    List<ReviewCard> findByUserAndDueDateLessThanEqualOrderByDueDateAscChapterNoAsc(User user, LocalDate dueDate);

    List<ReviewCard> findTop8ByUserOrderByDueDateAscChapterNoAsc(User user);

    Optional<ReviewCard> findByIdAndUser(Long id, User user);

    boolean existsByUserAndChapterNoAndTypeAndPrompt(User user, int chapterNo, String type, String prompt);
}
