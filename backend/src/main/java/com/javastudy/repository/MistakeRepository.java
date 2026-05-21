package com.javastudy.repository;

import com.javastudy.domain.Mistake;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MistakeRepository extends JpaRepository<Mistake, Long> {
}
