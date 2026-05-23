package com.javastudy.repository;

import com.javastudy.domain.PortfolioEvidence;
import com.javastudy.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioEvidenceRepository extends JpaRepository<PortfolioEvidence, Long> {
    List<PortfolioEvidence> findByUserOrderByCreatedAtDesc(User user);
}
