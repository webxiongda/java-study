package com.javastudy.repository;

import com.javastudy.domain.InterviewQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {
    Optional<InterviewQuestion> findByPromptHash(String promptHash);

    List<InterviewQuestion> findByCategoryOrderByFrequencyAscIdAsc(String category);

    @Query("select q.category as category, count(q) as total from InterviewQuestion q group by q.category")
    List<CategoryCount> countByCategory();

    interface CategoryCount {
        String getCategory();
        long getTotal();
    }
}
