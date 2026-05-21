package com.javastudy.repository;

import com.javastudy.domain.ChapterProgress;
import com.javastudy.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterProgressRepository extends JpaRepository<ChapterProgress, Long> {
    List<ChapterProgress> findByUser(User user);

    Optional<ChapterProgress> findByUserAndChapterNo(User user, int chapterNo);
}
