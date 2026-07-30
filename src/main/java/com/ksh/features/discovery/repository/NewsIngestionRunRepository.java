package com.ksh.features.discovery.repository;

import com.ksh.features.discovery.entity.NewsIngestionRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsIngestionRunRepository extends JpaRepository<NewsIngestionRun, Long> {
    List<NewsIngestionRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
