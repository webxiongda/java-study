package com.javastudy.repository;

import com.javastudy.domain.ReviewTask;
import com.javastudy.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewTaskRepository extends JpaRepository<ReviewTask, Long> {
    List<ReviewTask> findByUserOrderByDueDateAscChapterNoAsc(User user);

    List<ReviewTask> findByUserAndChapterNo(User user, int chapterNo);

    Optional<ReviewTask> findByUserAndChapterNoAndRound(User user, int chapterNo, String round);

    Optional<ReviewTask> findByIdAndUser(Long id, User user);
}
