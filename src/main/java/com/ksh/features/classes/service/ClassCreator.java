package com.ksh.features.classes.service;

import com.ksh.entities.ClassActivity;
import com.ksh.entities.ClassEntity;
import com.ksh.entities.Department;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.classes.dto.ClassesDtos.ClassForm;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.service.approval.ClassPendingReviewEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Encapsulates class creation, subject binding, audit, and leader notification.
 *
 * <p>Plain package-private helper instantiated by {@link ClassesService}
 * rather than a separate Spring bean.
 *
 * <p>The {@code @Transactional} boundary lives on {@link ClassesService#create}
 * so the entity and audit row commit atomically.
 */
final class ClassCreator {

    private static final Logger log = LoggerFactory.getLogger(ClassCreator.class);
    private final ClassRepository classRepository;
    private final ClassActivityWriter activityWriter;
    private final DepartmentRepository subjectRepository;
    private final ApplicationEventPublisher eventPublisher;

    ClassCreator(ClassRepository classRepository,
                 ClassActivityWriter activityWriter,
                 DepartmentRepository subjectRepository,
                 ApplicationEventPublisher eventPublisher) {
        this.classRepository = classRepository;
        this.activityWriter = activityWriter;
        this.subjectRepository = subjectRepository;
        this.eventPublisher = eventPublisher;
    }

    ClassEntity create(ClassForm form, Long userId) {
        Department subject = subjectRepository.findById(form.departmentId())
                .filter(Department::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Mã môn không tồn tại hoặc đã ngừng sử dụng"));
        ClassEntity entity = new ClassEntity(
                form.name(), userId, userId,
                form.description(), null, form.endDate(),
                form.maxStudents());
        entity.setDepartmentId(subject.getId());
        ClassEntity saved = classRepository.saveAndFlush(entity);
        activityWriter.write(saved.getId(), ClassActivity.TYPE_CREATED,
                "Tạo lớp " + saved.getName(), userId);
        try {
            eventPublisher.publishEvent(new ClassPendingReviewEvent(
                    saved.getId(), saved.getDepartmentId(), saved.getLecturerId(),
                    saved.getName(), subject.getCode()));
        } catch (RuntimeException exception) {
            log.warn("Không đăng ký được thông báo chờ duyệt cho lớp {}",
                    saved.getId(), exception);
        }
        return saved;
    }
}
