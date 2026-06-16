package com.ksh.KoreanStudyHub.repository;

import com.ksh.KoreanStudyHub.entity.Notification;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAll(Sort sort);
    long countByStatus(String status);
}
