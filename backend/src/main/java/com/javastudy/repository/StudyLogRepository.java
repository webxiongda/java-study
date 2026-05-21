package com.javastudy.repository;

import com.javastudy.domain.StudyLog;
import com.javastudy.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyLogRepository extends JpaRepository<StudyLog, Long> {
    List<StudyLog> findTop8ByUserOrderByCreatedAtDesc(User user);
}
