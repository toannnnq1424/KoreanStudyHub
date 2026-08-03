package com.ksh.features.classes.service;

import com.ksh.entities.ClassActivity;
import com.ksh.entities.ClassEntity;
import com.ksh.entities.Department;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.classes.dto.ClassesDtos.ClassForm;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.classes.service.approval.ClassPendingReviewEvent;
import com.ksh.features.classes.service.codes.ClassCodeGenerationException;
import com.ksh.features.classes.service.codes.ClassCodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Encapsulates the {@link ClassEntity} creation flow including the collision-
 * retry loop for the unique class code, the {@link ClassActivity#TYPE_CREATED}
 * audit row, subject binding, and leader-review notification.
 *
 * <p>Plain package-private helper instantiated by {@link ClassesService}
 * rather than a separate Spring bean.
 *
 * <p>The {@code @Transactional} boundary lives on {@link ClassesService#create}
 * so the entity and audit row commit atomically.
 */
final class ClassCreator {

    private static final Logger log = LoggerFactory.getLogger(ClassCreator.class);
    static final int MAX_CODE_GEN_ATTEMPTS = 3;

    private final ClassRepository classRepository;
    private final ClassActivityWriter activityWriter;
    private final ClassCodeGenerator codeGenerator;
    private final DepartmentRepository subjectRepository;
    private final ApplicationEventPublisher eventPublisher;

    ClassCreator(ClassRepository classRepository,
                 ClassActivityWriter activityWriter,
                 ClassCodeGenerator codeGenerator,
                 DepartmentRepository subjectRepository,
                 ApplicationEventPublisher eventPublisher) {
        this.classRepository = classRepository;
        this.activityWriter = activityWriter;
        this.codeGenerator = codeGenerator;
        this.subjectRepository = subjectRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Selects an unused class code before performing one transactional flush.
     * A concurrent unique violation aborts the whole unit atomically rather
     * than being retried in a rollback-only persistence context.
     */
    ClassEntity create(ClassForm form, Long userId) {
        Department subject = subjectRepository.findById(form.departmentId())
                .filter(Department::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Mã môn không tồn tại hoặc đã ngừng sử dụng"));
        String code = null;
        for (int attempt = 1; attempt <= MAX_CODE_GEN_ATTEMPTS; attempt++) {
            String candidate = codeGenerator.generate();
            if (classRepository.countAnyByCode(candidate) == 0) {
                code = candidate;
                break;
            }
        }
        if (code == null) {
            throw new ClassCodeGenerationException(
                    "Không sinh được mã lớp sau " + MAX_CODE_GEN_ATTEMPTS + " lần thử", null);
        }
        ClassEntity entity = new ClassEntity(
                form.name(), userId, userId,
                form.description(), null, form.endDate(),
                form.maxStudents());
        entity.setCode(code);
        entity.setDepartmentId(subject.getId());
        ClassEntity saved = classRepository.saveAndFlush(entity);
        activityWriter.write(saved.getId(), ClassActivity.TYPE_CREATED,
                "Tạo lớp " + saved.getName(), userId);
        try {
            eventPublisher.publishEvent(new ClassPendingReviewEvent(
                    saved.getId(), saved.getDepartmentId(), saved.getLecturerId(),
                    saved.getName(), saved.getCode()));
        } catch (RuntimeException exception) {
            log.warn("Không đăng ký được thông báo chờ duyệt cho lớp {}",
                    saved.getId(), exception);
        }
        return saved;
    }
}
