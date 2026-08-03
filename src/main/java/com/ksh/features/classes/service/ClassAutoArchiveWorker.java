package com.ksh.features.classes.service;

import com.ksh.entities.ClassEntity;
import com.ksh.features.classes.repository.ClassRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/** Idempotently archives ACTIVE classes when their optional end date is due. */
@Component
public class ClassAutoArchiveWorker {

    private final ClassRepository classRepository;
    private final Clock clock;

    public ClassAutoArchiveWorker(ClassRepository classRepository) {
        this(classRepository, Clock.systemDefaultZone());
    }

    ClassAutoArchiveWorker(ClassRepository classRepository, Clock clock) {
        this.classRepository = classRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${ksh.classes.auto-archive-cron:0 10 0 * * *}")
    @Transactional
    public void archiveDueClasses() {
        LocalDate today = LocalDate.now(clock);
        for (ClassEntity clazz : classRepository.findAllByStatusAndEndDateLessThanEqual(
                ClassEntity.STATUS_ACTIVE, today)) {
            clazz.archive();
        }
    }
}
