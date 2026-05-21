package com.javastudy.repository;

import com.javastudy.domain.CheckResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {
}
