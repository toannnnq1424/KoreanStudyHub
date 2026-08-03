package com.ksh.features.tests.service;

import com.ksh.entities.ClassEntity;
import com.ksh.entities.Department;
import com.ksh.entities.TestActivity;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.classes.repository.ClassRepository;
import com.ksh.features.tests.dto.LecturerTestDtos.BankItemSnapshot;
import com.ksh.features.tests.dto.LecturerTestDtos.BankOptionSnapshot;
import com.ksh.features.tests.dto.LecturerTestDtos.ClassOption;
import com.ksh.features.tests.dto.LecturerTestDtos.ExamForm;
import com.ksh.features.tests.dto.LecturerTestDtos.LecturerExamRow;
import com.ksh.features.tests.dto.LecturerTestDtos.ExamFilter;
import com.ksh.features.tests.dto.LecturerTestDtos.OptionForm;
import com.ksh.features.tests.dto.LecturerTestDtos.QuestionForm;
import com.ksh.features.tests.dto.LecturerTestDtos.TestDistributionResult;
import com.ksh.features.tests.dto.LecturerTestDtos.TestDistributionTarget;
import com.ksh.features.tests.dto.LecturerTestDtos.TestDistributionView;
import com.ksh.features.tests.dto.TestDtos.PreviewView;
import com.ksh.features.tests.entity.Question;
import com.ksh.features.tests.entity.QuestionOption;
import com.ksh.features.tests.entity.Test;
import com.ksh.features.tests.repository.QuestionRepository;
import com.ksh.features.tests.repository.TestRepository;
import com.ksh.common.HtmlSanitizer;
import com.ksh.features.tests.support.ExamFormValidator;
import com.ksh.features.tests.support.TestAccessResolver;
import com.ksh.features.upload.ExamImageStorageService;
import com.ksh.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.ksh.common.IConstant.DEFAULT_EXAM_PAGE_SIZE;
import static com.ksh.common.IConstant.MSG_QB_INSERT_EMPTY;
import static com.ksh.common.IConstant.MSG_QB_INSERT_LOCKED;

/**
 * Lecturer exam authoring: list owned exams, create/edit with a full question-set
 * replacement, and re-derive {@code total_questions}. Ownership is enforced via
 * {@link TestAccessResolver#requireManageable}. Question-bank persistence is
 * delegated to {@link ExamQuestionBankWriter}.
 */
@Service
public class LecturerExamService {

    private final TestRepository testRepository;
    private final QuestionRepository questionRepository;
    private final ClassRepository classRepository;
    private final DepartmentRepository departmentRepository;
    private final TestAccessResolver accessResolver;
    private final TestActivityWriter activityWriter;
    private final TakeViewBuilder takeViewBuilder;
    private final ExamQuestionBankWriter questionBankWriter;
    private final ExamQuestionBankPickerService questionBankPicker;
    private final ExamImageStorageService examImageStorage;

    public LecturerExamService(TestRepository testRepository,
                               QuestionRepository questionRepository,
                               ClassRepository classRepository,
                               DepartmentRepository departmentRepository,
                               TestAccessResolver accessResolver,
                               TestActivityWriter activityWriter,
                               TakeViewBuilder takeViewBuilder,
                               ExamQuestionBankWriter questionBankWriter,
                               ExamQuestionBankPickerService questionBankPicker,
                               ExamImageStorageService examImageStorage) {
        this.testRepository = testRepository;
        this.questionRepository = questionRepository;
        this.classRepository = classRepository;
        this.departmentRepository = departmentRepository;
        this.accessResolver = accessResolver;
        this.activityWriter = activityWriter;
        this.takeViewBuilder = takeViewBuilder;
        this.questionBankWriter = questionBankWriter;
        this.questionBankPicker = questionBankPicker;
        this.examImageStorage = examImageStorage;
    }

    /**
     * One page of manageable exams: LECTURER owns/created, LEADER is limited
     * to tests attached to classes in their department, and ADMIN is global
     * for non-Practice test management.
     */
    @Transactional(readOnly = true)
    public Page<LecturerExamRow> listOwned(Long userId, int page) {
        return listOwned(userId, page, new ExamFilter("", null, null, null));
    }

    @Transactional(readOnly = true)
    public Page<LecturerExamRow> listOwned(Long userId, int page, ExamFilter filter) {
        Role role = accessResolver.managementRole(userId);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), DEFAULT_EXAM_PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        List<Long> classIds = manageableClassIds(userId, role);
        Page<Test> result = testRepository.searchManageable(userId, classIds,
                role == Role.ADMIN, role == Role.LECTURER, filter.classId(),
                filter.keyword(), filter.status(), filter.type(), pageable);
        return toRows(result);
    }

    /**
     * One page of exams belonging to a single class. The service repeats the
     * class authorization boundary so non-controller callers cannot turn this
     * list method into a cross-department enumeration path.
     */
    @Transactional(readOnly = true)
    public Page<LecturerExamRow> listForClass(Long classId, Long userId, Role role, int page) {
        accessResolver.requireManageableClass(classId, userId, role);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), DEFAULT_EXAM_PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        return toRows(testRepository.findByClassId(classId, pageable));
    }

    /** Maps a page of exams to list rows, resolving class names in one batch. */
    private Page<LecturerExamRow> toRows(Page<Test> tests) {
        Map<Long, String> classNames = resolveClassNames(tests.getContent());
        return tests.map(t -> new LecturerExamRow(t.getId(), t.getTitle(), t.getType(),
                t.getStatus(), classNames.get(t.getClassId()),
                t.getTotalQuestions() == null ? 0 : t.getTotalQuestions(), t.getEndAt()));
    }

    /** Classes available to the actor under canonical role/class scope. */
    @Transactional(readOnly = true)
    public List<ClassOption> ledClasses(Long userId) {
        Role role = accessResolver.managementRole(userId);
        List<ClassOption> options = new ArrayList<>();
        for (ClassEntity c : accessResolver.manageableClasses(userId, role)) {
            options.add(new ClassOption(c.getId(), c.getName()));
        }
        return options;
    }

    /** Loads an owned exam as an editable form (with its questions + options). */
    @Transactional(readOnly = true)
    public ExamForm getForEdit(Long testId, Long userId) {
        Test test = accessResolver.requireManageable(testId, userId);
        List<Question> questions = questionRepository
                .findByTestIdOrderBySortOrderAscIdAsc(testId);
        Map<Long, List<QuestionOption>> optionsByQuestion = questionBankWriter.loadOptions(questions);
        List<QuestionForm> qForms = new ArrayList<>();
        for (Question q : questions) {
            List<OptionForm> optForms = optionsByQuestion.getOrDefault(q.getId(), List.of())
                    .stream().map(o -> new OptionForm(o.getId(), o.getContent(), o.isCorrect()))
                    .toList();
            qForms.add(new QuestionForm(q.getId(), q.getQuestionType(), q.getContent(),
                    q.getExplanation(), q.getPoints(), optForms));
        }
        return new ExamForm(test.getId(), test.getTitle(), test.getDescription(),
                test.getClassId(), test.getType(), test.getStatus(), test.getTimeMode(),
                test.getDurationMinutes(), test.getStartAt(), test.getEndAt(),
                test.getPassingScore(), test.isShuffleQuestions(), test.isShuffleOptions(),
                test.getMediaType(), test.getMediaUrl(), qForms,
                questionBankWriter.hasStudentActivity(testId));
    }

    /**
     * Builds a student-style preview of an owned exam without starting an attempt.
     * Ownership is enforced via {@link TestAccessResolver#requireManageable}.
     */
    @Transactional(readOnly = true)
    public PreviewView previewAsStudent(Long testId, Long userId) {
        Test test = accessResolver.requireManageable(testId, userId);
        return takeViewBuilder.buildPreview(test);
    }

    /**
     * Creates or updates an exam. Always persists form fields (including media)
     * and question/option content. When student responses already exist the bank
     * shape is locked (no add/remove/reorder of questions or options) so FK rows
     * in {@code test_responses} stay valid — content text/HTML may still change.
     * Returns the persisted exam id.
     */
    @Transactional
    public Long save(Long userId, ExamForm form) {
        ExamFormValidator.validate(form);
        requireLeadsClass(userId, form.classId());
        ExamForm claimedForm = claimStagedImages(userId, form);

        boolean creating = claimedForm.id() == null;
        Test test = creating
                ? new Test(userId, defaultType(claimedForm.type()))
                : accessResolver.requireManageableForUpdate(claimedForm.id(), userId);
        String previousStatus = creating ? null : test.getStatus();
        applyFields(test, claimedForm);
        Test saved = testRepository.save(test);

        if (creating || !questionBankWriter.hasStudentActivity(saved.getId())) {
            questionBankWriter.replaceQuestions(saved.getId(), claimedForm.questions());
        } else {
            questionBankWriter.updateQuestionContentsInPlace(saved.getId(), claimedForm.questions());
        }
        saved.setTotalQuestions(claimedForm.questions().size());
        testRepository.save(saved);

        recordSaveActivity(saved, userId, creating, previousStatus);
        return saved.getId();
    }

    /**
     * Converts owner-bound staged image URLs to durable URLs inside the same
     * transaction that persists the exam. One session deduplicates a URL reused
     * across fields and registers storage compensation for rollback/commit.
     */
    private ExamForm claimStagedImages(Long userId, ExamForm form) {
        ExamImageStorageService.ClaimSession claim = examImageStorage.beginClaim(userId);
        List<QuestionForm> questions = form.questions().stream()
                .map(question -> new QuestionForm(
                        question.id(),
                        question.type(),
                        claim.claimIn(question.content()),
                        claim.claimIn(question.explanation()),
                        question.points(),
                        question.options().stream()
                                .map(option -> new OptionForm(
                                        option.id(),
                                        claim.claimIn(option.content()),
                                        option.correct()))
                                .toList()))
                .toList();
        return new ExamForm(
                form.id(),
                form.title(),
                claim.claimIn(form.description()),
                form.classId(),
                form.type(),
                form.status(),
                form.timeMode(),
                form.durationMinutes(),
                form.startAt(),
                form.endAt(),
                form.passingScore(),
                form.shuffleQuestions(),
                form.shuffleOptions(),
                form.mediaType(),
                form.mediaUrl(),
                questions,
                form.questionBankLocked());
    }

    /**
     * Inserts approved shared-bank questions into an owned test as exam-owned
     * snapshot rows. The bank content/options are copied (not live-linked) via
     * {@link ExamQuestionBankPickerService}, so later bank edits never mutate the
     * inserted questions. Rejected when student responses already exist (locked
     * shape). Returns the number of questions actually inserted.
     */
    @Transactional
    public int insertFromBank(Long userId, Role role, Long testId, List<Long> itemIds) {
        Test test = accessResolver.requireManageableForUpdate(testId, userId, role);
        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException(MSG_QB_INSERT_EMPTY);
        }
        // Appending questions changes the bank shape, which is unsafe once graded.
        if (questionBankWriter.hasStudentActivity(testId)) {
            throw new IllegalArgumentException(MSG_QB_INSERT_LOCKED);
        }
        List<BankItemSnapshot> snapshots =
                questionBankPicker.approvedSnapshotsByIds(userId, role, testId, itemIds);
        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException(MSG_QB_INSERT_EMPTY);
        }
        List<QuestionForm> questions = new ArrayList<>();
        for (BankItemSnapshot snapshot : snapshots) {
            List<OptionForm> options = new ArrayList<>();
            for (BankOptionSnapshot option : snapshot.options()) {
                options.add(new OptionForm(null, option.content(), option.correct()));
            }
            questions.add(new QuestionForm(null, snapshot.questionType(), snapshot.content(),
                    snapshot.explanation(), BigDecimal.ONE, options));
        }
        int inserted = questionBankWriter.appendQuestions(testId, questions);

        int total = questionRepository.findByTestIdOrderBySortOrderAscIdAsc(testId).size();
        test.setTotalQuestions(total);
        testRepository.save(test);
        activityWriter.write(test.getId(), TestActivity.TYPE_UPDATED,
                "Chèn " + inserted + " câu hỏi từ ngân hàng vào bài test \"" + test.getTitle() + "\"",
                null, userId);
        return inserted;
    }

    /**
     * Returns ACTIVE classes in the same subject that may receive a snapshot of
     * the selected published test. The source test remains unchanged and no
     * Practice row is ever eligible for this flow.
     */
    @Transactional(readOnly = true)
    public TestDistributionView distributionView(Long userId, Role role, Long testId) {
        Test source = requireDistributable(testId, userId, role, false);
        Long sourceSubjectId = requireSourceSubjectId(source);
        Department subject = departmentRepository.findById(sourceSubjectId)
                .orElseThrow(() -> new IllegalArgumentException("Mã môn của bài test không còn tồn tại"));

        List<TestDistributionTarget> targets = accessResolver.manageableClasses(userId, role).stream()
                .filter(clazz -> source.getClassId() == null || !clazz.getId().equals(source.getClassId()))
                .filter(clazz -> ClassEntity.STATUS_ACTIVE.equals(clazz.getStatus()))
                .filter(clazz -> sourceSubjectId.equals(clazz.getSubjectId()))
                .filter(clazz -> !testRepository.existsByClassIdAndTitleIgnoreCase(
                        clazz.getId(), source.getTitle()))
                .map(clazz -> new TestDistributionTarget(clazz.getId(), clazz.getName()))
                .toList();
        return new TestDistributionView(source.getId(), source.getTitle(),
                subject.getCode(), targets);
    }

    /**
     * Atomically copies one finished PUBLISHED test, including its questions
     * and options, to one or more ACTIVE classes with the same subject code.
     * Each copy is an independent published snapshot backed by the existing
     * tests/questions/question_options tables; no extra mapping table is needed.
     */
    @Transactional
    public TestDistributionResult distributePublished(Long userId, Role role, Long testId,
                                                       List<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một lớp nhận bài test");
        }
        Test source = requireDistributable(testId, userId, role, true);
        Long sourceSubjectId = requireSourceSubjectId(source);
        List<QuestionForm> questionSnapshot = snapshotQuestions(source.getId());
        if (questionSnapshot.isEmpty()) {
            throw new IllegalArgumentException("Bài test chưa có câu hỏi để phân phối");
        }

        List<Long> createdIds = new ArrayList<>();
        for (Long classId : new LinkedHashSet<>(classIds)) {
            if (classId == null) {
                continue;
            }
            ClassEntity permitted = accessResolver.requireManageableClass(classId, userId, role);
            ClassEntity target = classRepository.findByIdForUpdate(permitted.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Lớp nhận không còn tồn tại"));
            if (source.getClassId() != null && target.getId().equals(source.getClassId())) {
                throw new IllegalArgumentException("Không thể phân phối lại vào chính lớp nguồn");
            }
            if (!ClassEntity.STATUS_ACTIVE.equals(target.getStatus())) {
                throw new IllegalArgumentException("Chỉ được phân phối tới lớp đã duyệt và đang hoạt động");
            }
            if (!sourceSubjectId.equals(target.getSubjectId())) {
                throw new IllegalArgumentException("Chỉ được phân phối tới lớp có cùng mã môn");
            }
            if (testRepository.existsByClassIdAndTitleIgnoreCase(target.getId(), source.getTitle())) {
                throw new IllegalArgumentException(
                        "Lớp \"" + target.getName() + "\" đã có bài test cùng tên");
            }

            Test snapshot = copyExamFields(source, target.getId(), userId);
            Test saved = testRepository.saveAndFlush(snapshot);
            questionBankWriter.appendQuestions(saved.getId(), questionSnapshot);
            saved.setTotalQuestions(questionSnapshot.size());
            testRepository.save(saved);
            activityWriter.write(saved.getId(), TestActivity.TYPE_CREATED,
                    "Nhận bản phân phối từ bài test \"" + source.getTitle() + "\"",
                    activityWriter.serialize(Map.of(
                            "sourceTestId", source.getId(),
                            "sourceTestSubjectId", sourceSubjectId)), userId);
            activityWriter.write(saved.getId(), TestActivity.TYPE_PUBLISHED,
                    "Phát hành bản phân phối bài test \"" + source.getTitle() + "\"",
                    null, userId);
            createdIds.add(saved.getId());
        }
        if (createdIds.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một lớp nhận bài test");
        }
        activityWriter.write(source.getId(), TestActivity.TYPE_UPDATED,
                "Phân phối bài test \"" + source.getTitle() + "\" tới "
                        + createdIds.size() + " lớp",
                activityWriter.serialize(Map.of("distributedTestIds", createdIds)), userId);
        return new TestDistributionResult(List.copyOf(createdIds));
    }

    private Test requireDistributable(Long testId, Long userId, Role role, boolean lock) {
        Test source = lock
                ? accessResolver.requireManageableForUpdate(testId, userId, role)
                : accessResolver.requireManageable(testId, userId, role);
        if (source.isPractice()) {
            throw new IllegalArgumentException("Practice test không thuộc luồng phân phối này");
        }
        if (!Test.STATUS_PUBLISHED.equals(source.getStatus())) {
            throw new IllegalArgumentException("Chỉ bài test đã hoàn tất và xuất bản mới được phân phối");
        }
        return source;
    }

    private Long requireSourceSubjectId(Test source) {
        if (source.getSubjectId() != null) return source.getSubjectId();
        if (source.getClassId() == null) {
            throw new IllegalArgumentException("Bài test nguồn chưa có mã môn");
        }
        return classRepository.findById(source.getClassId())
                .map(ClassEntity::getSubjectId)
                .orElseThrow(() -> new IllegalArgumentException("Lớp nguồn không còn tồn tại"));
    }

    private List<QuestionForm> snapshotQuestions(Long testId) {
        List<Question> questions = questionRepository.findByTestIdOrderBySortOrderAscIdAsc(testId);
        Map<Long, List<QuestionOption>> optionsByQuestion = questionBankWriter.loadOptions(questions);
        return questions.stream().map(question -> new QuestionForm(
                null,
                question.getQuestionType(),
                question.getContent(),
                question.getExplanation(),
                question.getPoints(),
                optionsByQuestion.getOrDefault(question.getId(), List.of()).stream()
                        .map(option -> new OptionForm(null, option.getContent(), option.isCorrect()))
                        .toList()))
                .toList();
    }

    private static Test copyExamFields(Test source, Long classId, Long createdBy) {
        Test snapshot = new Test(createdBy, source.getType());
        snapshot.setTitle(source.getTitle());
        snapshot.setDescription(source.getDescription());
        snapshot.setClassId(classId);
        snapshot.setSubjectId(source.getSubjectId());
        snapshot.setDurationMinutes(source.getDurationMinutes());
        snapshot.setPassingScore(source.getPassingScore());
        snapshot.setShuffleQuestions(source.isShuffleQuestions());
        snapshot.setShuffleOptions(source.isShuffleOptions());
        snapshot.setStatus(Test.STATUS_PUBLISHED);
        snapshot.setStartAt(source.getStartAt());
        snapshot.setEndAt(source.getEndAt());
        snapshot.setTimeMode(source.getTimeMode());
        snapshot.setMediaType(source.getMediaType());
        snapshot.setMediaUrl(source.getMediaUrl());
        return snapshot;
    }

    /**
     * Appends audit rows for a save: always a CREATED/UPDATED row, plus a
     * PUBLISHED row when the status transitions into {@code PUBLISHED} on this
     * save (create-as-published counts as a transition too). Audit failures
     * never block the save — they are best-effort within the same transaction.
     */
    private void recordSaveActivity(Test saved, Long userId, boolean creating, String previousStatus) {
        String type = creating ? TestActivity.TYPE_CREATED : TestActivity.TYPE_UPDATED;
        String description = (creating ? "Tạo bài test \"" : "Cập nhật bài test \"")
                + saved.getTitle() + "\"";
        String metadata = activityWriter.serialize(Map.of(
                "status", saved.getStatus() == null ? "" : saved.getStatus(),
                "totalQuestions", saved.getTotalQuestions() == null ? 0 : saved.getTotalQuestions()));
        activityWriter.write(saved.getId(), type, description, metadata, userId);

        boolean nowPublished = Test.STATUS_PUBLISHED.equals(saved.getStatus());
        boolean wasPublished = Test.STATUS_PUBLISHED.equals(previousStatus);
        if (nowPublished && !wasPublished) {
            activityWriter.write(saved.getId(), TestActivity.TYPE_PUBLISHED,
                    "Phát hành bài test \"" + saved.getTitle() + "\"", null, userId);
        }
    }

    private void applyFields(Test test, ExamForm form) {
        test.setTitle(form.title().trim());
        // Description may hold a reading-passage HTML body from the Quill editor.
        String description = trimToNull(form.description());
        test.setDescription(description == null ? null : HtmlSanitizer.sanitize(description));
        test.setClassId(form.classId());
        classRepository.findById(form.classId()).ifPresent(clazz -> test.setSubjectId(clazz.getSubjectId()));
        test.setType(defaultType(form.type()));
        test.setStatus(form.status() == null ? Test.STATUS_DRAFT : form.status());
        test.setTimeMode(form.timeMode() == null ? Test.TIME_MODE_FIXED_WINDOW : form.timeMode());
        test.setDurationMinutes(form.durationMinutes());
        test.setStartAt(form.startAt());
        test.setEndAt(form.endAt());
        test.setPassingScore(form.passingScore());
        test.setShuffleQuestions(form.shuffleQuestions());
        test.setShuffleOptions(form.shuffleOptions());
        String mediaType = trimToNull(form.mediaType());
        String mediaUrl = trimToNull(form.mediaUrl());
        if (mediaType == null && mediaUrl == null) {
            test.setMediaType(null);
            test.setMediaUrl(null);
        } else {
            test.setMediaType(mediaType);
            test.setMediaUrl(mediaUrl);
        }
    }

    private void requireLeadsClass(Long userId, Long classId) {
        accessResolver.requireManageableClass(
                classId, userId, accessResolver.managementRole(userId));
    }

    private List<Long> manageableClassIds(Long userId, Role role) {
        List<Long> ids = new ArrayList<>(accessResolver.manageableClasses(userId, role).stream()
                .map(ClassEntity::getId)
                .toList());
        // Sentinel keeps the JPQL IN clause valid when the actor has no class.
        if (ids.isEmpty()) ids.add(-1L);
        return ids;
    }

    private Map<Long, String> resolveClassNames(List<Test> tests) {
        Map<Long, String> names = new HashMap<>();
        List<Long> ids = tests.stream().map(Test::getClassId)
                .filter(id -> id != null).distinct().toList();
        if (ids.isEmpty()) return names;
        for (ClassEntity c : classRepository.findAllById(ids)) {
            names.put(c.getId(), c.getName());
        }
        return names;
    }

    private static String defaultType(String type) {
        return type == null || type.isBlank() ? Test.TYPE_MOCK : type;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
