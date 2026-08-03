package com.ksh.features.questionbank.service;

import com.ksh.common.HtmlSanitizer;
import com.ksh.entities.Department;
import com.ksh.entities.User;
import com.ksh.features.admin.departments.repository.DepartmentRepository;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.questionbank.dto.QuestionBankItemForm;
import com.ksh.features.questionbank.dto.QuestionBankViews.ContributorOption;
import com.ksh.features.questionbank.dto.QuestionBankViews.ItemDetail;
import com.ksh.features.questionbank.dto.QuestionBankViews.ItemRow;
import com.ksh.features.questionbank.dto.QuestionBankViews.OptionView;
import com.ksh.features.questionbank.dto.QuestionBankViews.StatusCounts;
import com.ksh.features.questionbank.dto.QuestionBankViews.SubjectReviewView;
import com.ksh.features.questionbank.entity.QuestionBankItem;
import com.ksh.features.questionbank.entity.QuestionBankOption;
import com.ksh.features.questionbank.repository.QuestionBankItemRepository;
import com.ksh.features.questionbank.repository.QuestionBankOptionRepository;
import com.ksh.security.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Lecturer authoring and leader review reads for a subject-scoped Question Bank. */
@Service
public class QuestionBankItemService {

    private static final String MSG_EMPTY_SUBJECT =
            "Bạn chưa được gán mã môn để cộng tác soạn câu hỏi";
    private static final String MSG_NOT_FOUND = "Không tìm thấy câu hỏi cộng tác";
    private static final String MSG_FORBIDDEN = "Bạn không có quyền thao tác với câu hỏi cộng tác này";

    private final UserRepository userRepository;
    private final DepartmentRepository subjectRepository;
    private final QuestionBankAccessPolicy accessPolicy;
    private final QuestionBankItemRepository itemRepository;
    private final QuestionBankOptionRepository optionRepository;

    public QuestionBankItemService(UserRepository userRepository,
                                   DepartmentRepository subjectRepository,
                                   QuestionBankAccessPolicy accessPolicy,
                                   QuestionBankItemRepository itemRepository,
                                   QuestionBankOptionRepository optionRepository) {
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.accessPolicy = accessPolicy;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
    }

    @Transactional(readOnly = true)
    public List<ItemRow> list(Long userId, Role role, String status,
                              Long contributorId, String query) {
        User actor = requireActor(userId, role);
        Department subject = requireSubject(actor);
        List<QuestionBankItem> items = itemRepository
                .findBySubjectIdOrderByUpdatedAtDescIdDesc(subject.getId());
        Map<Long, String> userNames = userNames(items);
        String normalizedQuery = normalizeQuery(query);
        return items.stream()
                .filter(item -> matchesStatus(item, status))
                .filter(item -> contributorId == null || contributorId.equals(item.getContributorId()))
                .filter(item -> matchesQuery(item, subject.getCode(), userNames, normalizedQuery))
                .map(item -> new ItemRow(
                        item.getId(), preview(item.getContent()), item.getQuestionType(),
                        item.getWorkflowStatus(), subject.getCode(),
                        userNames.getOrDefault(item.getContributorId(), "—"),
                        item.getUpdatedAt(), canEdit(actor, item), canReview(actor, item)))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubjectReviewView reviewView(Long userId, Role role, String status,
                                        Long contributorId, String query) {
        User actor = requireActor(userId, role);
        Department subject = requireSubject(actor);
        List<QuestionBankItem> all = itemRepository
                .findBySubjectIdOrderByUpdatedAtDescIdDesc(subject.getId());
        Map<Long, String> names = userNames(all);
        String normalizedQuery = normalizeQuery(query);
        List<QuestionBankItem> filtered = all.stream()
                .filter(item -> matchesStatus(item, status))
                .filter(item -> contributorId == null || contributorId.equals(item.getContributorId()))
                .filter(item -> matchesQuery(item, subject.getCode(), names, normalizedQuery))
                .toList();
        Map<Long, List<OptionView>> options = optionsByItemId(filtered);
        List<ItemDetail> details = filtered.stream()
                .map(item -> toDetail(actor, item, subject.getCode(), names, options))
                .toList();
        return new SubjectReviewView(subject.getCode(), subject.getName(), details,
                tallyStatus(all), contributors(all, names));
    }

    @Transactional(readOnly = true)
    public StatusCounts countByStatus(Long userId, Role role) {
        User actor = requireActor(userId, role);
        return tallyStatus(itemRepository.findBySubjectIdOrderByUpdatedAtDescIdDesc(
                requireSubject(actor).getId()));
    }

    @Transactional(readOnly = true)
    public List<ContributorOption> contributorsFor(Long userId, Role role) {
        User actor = requireActor(userId, role);
        List<QuestionBankItem> items = itemRepository.findBySubjectIdOrderByUpdatedAtDescIdDesc(
                requireSubject(actor).getId());
        return contributors(items, userNames(items));
    }

    @Transactional(readOnly = true)
    public QuestionBankItemForm loadForm(Long userId, Role role, Long itemId) {
        User actor = requireActor(userId, role);
        QuestionBankItem item = requireVisibleItem(itemId, actor);
        if (!canEdit(actor, item)) {
            throw new AccessDeniedException(MSG_FORBIDDEN);
        }
        QuestionBankItemForm form = new QuestionBankItemForm();
        form.setId(item.getId());
        form.setQuestionType(item.getQuestionType());
        form.setContent(item.getContent());
        form.setExplanation(item.getExplanation());
        List<QuestionBankItemForm.OptionField> fields = new ArrayList<>();
        for (QuestionBankOption option : optionRepository
                .findByItemIdInOrderBySortOrderAscIdAsc(List.of(item.getId()))) {
            QuestionBankItemForm.OptionField field = new QuestionBankItemForm.OptionField();
            field.setContent(option.getContent());
            field.setCorrect(option.isCorrect());
            fields.add(field);
        }
        form.setOptions(fields);
        form.ensureMinOptions(4);
        form.setWorkflowAction(QuestionBankItem.STATUS_DRAFT);
        return form;
    }

    @Transactional(readOnly = true)
    public ItemDetail detail(Long userId, Role role, Long itemId) {
        User actor = requireActor(userId, role);
        Department subject = requireSubject(actor);
        QuestionBankItem item = requireVisibleItem(itemId, actor);
        Map<Long, String> names = loadNames(Stream.of(item.getContributorId(), item.getReviewedBy())
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return toDetail(actor, item, subject.getCode(), names, optionsByItemId(List.of(item)));
    }

    @Transactional
    public Long save(Long userId, Role role, QuestionBankItemForm form) {
        User actor = requireActor(userId, role);
        Long subjectId = requireSubject(actor).getId();
        List<QuestionBankOption> options = validatedOptions(form);
        String workflowStatus = resolveWorkflowAction(form.getWorkflowAction());
        QuestionBankItem item;
        if (form.getId() == null) {
            item = new QuestionBankItem(subjectId, actor.getId(),
                    normalizedQuestionType(form.getQuestionType()), workflowStatus,
                    sanitizeRequired(form.getContent(), "Nội dung câu hỏi không được để trống"),
                    sanitizeOptional(form.getExplanation()));
        } else {
            item = requireVisibleItem(form.getId(), actor);
            if (!canEdit(actor, item)) {
                throw new AccessDeniedException(MSG_FORBIDDEN);
            }
            item.updateAuthoring(normalizedQuestionType(form.getQuestionType()),
                    sanitizeRequired(form.getContent(), "Nội dung câu hỏi không được để trống"),
                    sanitizeOptional(form.getExplanation()));
            item.transitionWorkflow(workflowStatus, null, null, null, null);
        }
        QuestionBankItem saved = itemRepository.save(item);
        optionRepository.deleteByItemIdIn(List.of(saved.getId()));
        int order = 1;
        for (QuestionBankOption option : options) {
            optionRepository.save(new QuestionBankOption(
                    saved.getId(), option.getContent(), option.isCorrect(), order++));
        }
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public boolean hasSubject(Long userId, Role role) {
        User actor = requireActor(userId, role);
        Long id = accessPolicy.resolveSubjectId(actor);
        return id != null && subjectRepository.findById(id)
                .filter(Department::isActive).isPresent();
    }

    QuestionBankItem requireVisibleItem(Long itemId, User actor) {
        Long subjectId = requireSubject(actor).getId();
        return itemRepository.findByIdAndSubjectId(itemId, subjectId)
                .orElseThrow(() -> new QuestionBankValidationException(MSG_NOT_FOUND));
    }

    boolean canReview(User actor, QuestionBankItem item) {
        return accessPolicy.canCurateSubject(actor, item.getSubjectId())
                && QuestionBankItem.STATUS_REVIEW.equals(item.getWorkflowStatus());
    }

    boolean canArchive(User actor, QuestionBankItem item) {
        return accessPolicy.canCurateSubject(actor, item.getSubjectId())
                && !QuestionBankItem.STATUS_ARCHIVED.equals(item.getWorkflowStatus());
    }

    boolean canUnarchive(User actor, QuestionBankItem item) {
        return accessPolicy.canCurateSubject(actor, item.getSubjectId())
                && QuestionBankItem.STATUS_ARCHIVED.equals(item.getWorkflowStatus());
    }

    private boolean canEdit(User actor, QuestionBankItem item) {
        if (!accessPolicy.canAccessSubject(actor, item.getSubjectId())
                || QuestionBankItem.STATUS_ARCHIVED.equals(item.getWorkflowStatus())) {
            return false;
        }
        if (accessPolicy.canCurateSubject(actor, item.getSubjectId())) {
            return !QuestionBankItem.STATUS_APPROVED.equals(item.getWorkflowStatus());
        }
        return actor.getId().equals(item.getContributorId())
                && !QuestionBankItem.STATUS_APPROVED.equals(item.getWorkflowStatus());
    }

    private User requireActor(Long userId, Role role) {
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new AccessDeniedException(MSG_FORBIDDEN));
        if (actor.getRole() != role) {
            throw new AccessDeniedException(MSG_FORBIDDEN);
        }
        return actor;
    }

    private Department requireSubject(User actor) {
        Long subjectId = accessPolicy.resolveSubjectId(actor);
        if (subjectId == null || !accessPolicy.canAccessSubject(actor, subjectId)) {
            throw new QuestionBankValidationException(MSG_EMPTY_SUBJECT);
        }
        return subjectRepository.findById(subjectId)
                .filter(Department::isActive)
                .orElseThrow(() -> new QuestionBankValidationException(MSG_EMPTY_SUBJECT));
    }

    private ItemDetail toDetail(User actor, QuestionBankItem item, String subjectCode,
                                Map<Long, String> names,
                                Map<Long, List<OptionView>> options) {
        return new ItemDetail(item.getId(), item.getQuestionType(), item.getWorkflowStatus(),
                item.getContent(), preview(item.getContent()), item.getExplanation(),
                item.getReviewNote(), subjectCode,
                names.getOrDefault(item.getContributorId(), "—"), names.get(item.getReviewedBy()),
                item.getReviewedAt(), item.getApprovedAt(), item.getUpdatedAt(),
                options.getOrDefault(item.getId(), List.of()), canEdit(actor, item),
                canReview(actor, item), canArchive(actor, item), canUnarchive(actor, item));
    }

    private Map<Long, List<OptionView>> optionsByItemId(List<QuestionBankItem> items) {
        if (items.isEmpty()) return Map.of();
        Map<Long, List<OptionView>> result = new LinkedHashMap<>();
        for (QuestionBankOption option : optionRepository.findByItemIdInOrderBySortOrderAscIdAsc(
                items.stream().map(QuestionBankItem::getId).toList())) {
            result.computeIfAbsent(option.getItemId(), ignored -> new ArrayList<>())
                    .add(new OptionView(option.getContent(), option.isCorrect()));
        }
        return result;
    }

    private static StatusCounts tallyStatus(List<QuestionBankItem> items) {
        return new StatusCounts(count(items, QuestionBankItem.STATUS_REVIEW),
                count(items, QuestionBankItem.STATUS_APPROVED),
                count(items, QuestionBankItem.STATUS_REJECTED),
                count(items, QuestionBankItem.STATUS_ARCHIVED), items.size());
    }

    private static long count(List<QuestionBankItem> items, String status) {
        return items.stream().filter(item -> status.equals(item.getWorkflowStatus())).count();
    }

    private List<ContributorOption> contributors(List<QuestionBankItem> items,
                                                 Map<Long, String> names) {
        Map<Long, ContributorOption> result = new LinkedHashMap<>();
        for (QuestionBankItem item : items) {
            result.computeIfAbsent(item.getContributorId(),
                    id -> new ContributorOption(id, names.getOrDefault(id, "—")));
        }
        return new ArrayList<>(result.values());
    }

    private Map<Long, String> userNames(List<QuestionBankItem> items) {
        return loadNames(items.stream()
                .flatMap(item -> Stream.of(item.getContributorId(), item.getReviewedBy()))
                .filter(Objects::nonNull).collect(Collectors.toSet()));
    }

    private Map<Long, String> loadNames(Set<Long> ids) {
        Map<Long, String> names = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) return names;
        for (User user : userRepository.findAllById(ids)) {
            names.put(user.getId(), user.getFullName());
        }
        return names;
    }

    private List<QuestionBankOption> validatedOptions(QuestionBankItemForm form) {
        List<QuestionBankOption> options = new ArrayList<>();
        int correct = 0;
        int order = 1;
        for (QuestionBankItemForm.OptionField field : form.getOptions()) {
            String content = sanitizeOptional(field.getContent());
            if (content == null) continue;
            if (field.isCorrect()) correct++;
            options.add(new QuestionBankOption(null, content, field.isCorrect(), order++));
        }
        if (options.size() < 2) throw new QuestionBankValidationException("Mỗi câu hỏi phải có ít nhất hai đáp án");
        if (correct == 0) throw new QuestionBankValidationException("Mỗi câu hỏi phải có ít nhất một đáp án đúng");
        if (QuestionBankItem.TYPE_MCQ.equals(normalizedQuestionType(form.getQuestionType())) && correct != 1) {
            throw new QuestionBankValidationException("Câu hỏi MCQ phải có đúng một đáp án đúng");
        }
        return options;
    }

    private static boolean matchesStatus(QuestionBankItem item, String status) {
        return status == null || status.isBlank() || status.equalsIgnoreCase(item.getWorkflowStatus());
    }

    private static boolean matchesQuery(QuestionBankItem item, String subjectCode,
                                        Map<Long, String> names, String query) {
        if (query == null) return true;
        return preview(item.getContent()).toLowerCase().contains(query)
                || names.getOrDefault(item.getContributorId(), "").toLowerCase().contains(query)
                || subjectCode.toLowerCase().contains(query);
    }

    private static String preview(String html) {
        String plain = html == null ? "" : html.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ").trim();
        return plain.length() > 120 ? plain.substring(0, 117) + "..." : plain;
    }

    private static String normalizeQuery(String query) {
        return query == null || query.isBlank() ? null : query.trim().toLowerCase();
    }

    private static String resolveWorkflowAction(String action) {
        return QuestionBankItem.STATUS_REVIEW.equalsIgnoreCase(action)
                ? QuestionBankItem.STATUS_REVIEW : QuestionBankItem.STATUS_DRAFT;
    }

    private static String normalizedQuestionType(String value) {
        return QuestionBankItem.TYPE_MR.equalsIgnoreCase(value)
                ? QuestionBankItem.TYPE_MR : QuestionBankItem.TYPE_MCQ;
    }

    private static String sanitizeRequired(String value, String message) {
        String sanitized = sanitizeOptional(value);
        if (sanitized == null) throw new QuestionBankValidationException(message);
        return sanitized;
    }

    private static String sanitizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        String sanitized = HtmlSanitizer.sanitize(value.trim()).trim();
        return sanitized.isBlank() ? null : sanitized;
    }
}
