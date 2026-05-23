package com.javastudy.repository;

import com.javastudy.domain.InterviewAttempt;
import com.javastudy.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewAttemptRepository extends JpaRepository<InterviewAttempt, Long> {
    List<InterviewAttempt> findByUserOrderByCreatedAtDesc(User user);

    long countByUser(User user);

    @Query("select count(distinct a.question.id) from InterviewAttempt a where a.user = :user and a.selfAssessed = 'mastered'")
    long countMasteredQuestions(@Param("user") User user);

    @Query("select a.question.id from InterviewAttempt a where a.user = :user")
    List<Long> findAttemptedQuestionIds(@Param("user") User user);
}
