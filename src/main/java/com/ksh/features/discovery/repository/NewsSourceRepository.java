package com.ksh.features.discovery.repository;

import com.ksh.features.discovery.entity.NewsSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsSourceRepository extends JpaRepository<NewsSource, Long> {
    List<NewsSource> findByEnabledTrueOrderByPriorityWeightDesc();
}
