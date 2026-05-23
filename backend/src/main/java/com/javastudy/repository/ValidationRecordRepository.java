package com.javastudy.repository;

import com.javastudy.domain.User;
import com.javastudy.domain.ValidationRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValidationRecordRepository extends JpaRepository<ValidationRecord, Long> {
    List<ValidationRecord> findByUserAndChapterNo(User user, int chapterNo);
}
