# Refactor Section → Group → Question (Full Flow)

## Tóm tắt

Refactor toàn bộ flow **Section → Group → Question** để đảm bảo:
1. Câu hỏi được đánh số riêng biệt trong từng **Phần** (Section), không xuyên suốt toàn bộ đề thi.
2. Trong cùng một Phần, số câu của các nhóm sau **luôn lớn hơn** các nhóm trước.
3. Result trả về **bảng kết quả riêng theo kỹ năng** từng Phần.
4. Player hiển thị đúng số câu và section context.
5. Editor enforce auto-renumbering khi thêm/di chuyển câu hỏi.

---

## Phân tích hiện trạng

### Cơ sở hạ tầng đã có sẵn ✅
- `practice_sections` table (setId, skill, displayOrder, durationMinutes…)
- `practice_question_groups.section_id` FK → practice_sections
- `practice_questions.group_id` FK → practice_question_groups
- `PracticeSection`, `PracticeQuestionGroup`, `PracticeQuestion` entities

### Vấn đề hiện tại 🔴
1. **Editor JS**: `addQuestionWrapper()` tính `nextQNo = maxNo toàn đề + 1` — **sai** — phải tính max trong cùng Section
2. **Editor JS**: Di chuyển câu hỏi giữa nhóm (`handleMoveToGroupFromMenu`) không re-number
3. **Editor JS**: Kéo nhóm lên/xuống (`moveGroup`) không re-number câu hỏi
4. **Player**: Hiển thị question number từ DB field `questionNo` — đúng nhưng DB có thể có số sai do publish cũ
5. **Publisher**: Khi publish, `question.questionNo` lấy từ JSON draft — **không enforce** constraint "section-local"
6. **Result View**: `getReadingListeningResult` không phân nhóm theo section, trả hết câu hỏi chung
7. **PracticeSetView** dùng `groups()` flat list, không có section context

---

## Proposed Changes

### Phase 1 — Editor JS: Per-Section Question Numbering

#### [MODIFY] [editor.html](file:///d:/Downloads/ksh/src/main/resources/templates/practice/manage/editor.html)

**Mục tiêu:** Enforcing rule: trong cùng Section, questionNo phải liên tục và nhóm sau có số lớn hơn nhóm trước.

1. **`addQuestionWrapper()`** (line ~1968):
   - Hiện tại: tính `maxNo` qua **toàn bộ** `DRAFT_DATA.sections`
   - Sửa thành: tính `maxNo` chỉ trong **`DRAFT_DATA.sections[sIdx]`** (section hiện tại)

2. **`reNumberSectionQuestions(sIdx)`** — thêm hàm mới:
   ```js
   function reNumberSectionQuestions(sIdx) {
     const sec = DRAFT_DATA.sections[sIdx];
     if (!sec || !sec.groups) return;
     let counter = 1;
     sec.groups.forEach(grp => {
       if (grp.questions) {
         grp.questions.forEach(q => {
           q.questionNo = counter++;
         });
         updateGroupQuestionRange(grp);
       }
     });
   }
   ```

3. **Gọi `reNumberSectionQuestions(sIdx)`** sau khi:
   - `moveGroup(sIdx, gIdx, dir)` — sau khi swap
   - `deleteQuestion(sIdx, gIdx, qIdx)` — sau khi xóa
   - `deleteGroup(sIdx, gIdx)` — sau khi xóa
   - `handleMoveToGroupFromMenu()` — sau khi di chuyển
   - `duplicateQuestion(sIdx, gIdx, qIdx)` — sau khi nhân đôi

4. **`saveCurrentNode()` type='question'**: khi user sửa `q-no` manually, validate nó không trùng với câu khác trong section; nếu trùng thì reNumber.

5. **Tree display**: `getGroupName()` hiển thị `Nhóm X-Y` dựa trên questionFrom/questionTo — **đã đúng**, không cần đổi.

6. **Validation** (`validateDraft` gọi server-side `PracticeDraftValidator`): validator server-side cần check thứ tự số câu trong section.

---

### Phase 2 — Publisher: Enforce Section-Local Numbering khi Publish

#### [MODIFY] [PracticePublisherService.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/manage/service/PracticePublisherService.java)

Trong vòng lặp save sections/groups/questions (line ~152):
- Thêm biến `int sectionLocalQNo = 1;` cho mỗi section
- Thay `qNode.path("questionNo").asInt(qIdx + 1)` bằng `sectionLocalQNo++` để đảm bảo questionNo phản ánh đúng thứ tự trong section kể cả khi draft JSON bị sai

**Hiện tại:**
```java
PracticeQuestion question = new PracticeQuestion(
    savedSet.getId(),
    qNode.path("questionNo").asInt(qIdx + 1),  // ← có thể sai
    ...
```

**Sau khi sửa:**
```java
// sectionLocalQNo được tính auto khi lặp qua groups trong section
PracticeQuestion question = new PracticeQuestion(
    savedSet.getId(),
    sectionLocalQNo++,  // ← chính xác, liên tục trong section
    ...
```

---

### Phase 3 — Validator: Check Section-Local Ordering

#### [MODIFY] [PracticeDraftValidator.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/manage/validator/PracticeDraftValidator.java)

Trong vòng lặp sections/groups/questions, thêm validation:
- Mỗi section, nhóm sau phải có `questionFrom > questionTo của nhóm trước`
- Câu hỏi trong nhóm phải liên tục (không gap lớn)

---

### Phase 4 — DTO & Service: Section-Aware Result View

#### [MODIFY] [PracticeDtos.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/dto/PracticeDtos.java)

Thêm DTO mới:
```java
public record SectionResultRow(
    Long sectionId,
    String sectionTitle,
    String skill,
    int correctCount,
    int incorrectCount,
    int totalCount,
    BigDecimal sectionScore,
    BigDecimal sectionTotalPoints,
    List<ReviewGroupRow> groups
) {}
```

Cập nhật `ReadingListeningResultView` thêm field `List<SectionResultRow> sectionResults`.

#### [MODIFY] [PracticeService.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/service/PracticeService.java)

Trong `getReadingListeningResult()`:
- Load `List<PracticeSection> sections = sectionRepository.findBySetIdOrderByDisplayOrderAsc(setId)`
- Nhóm groups theo section: `group.getSectionId()` → section
- Build `SectionResultRow` cho mỗi section với groups/questions thuộc section đó
- Return result với `sectionResults` field populated

Thêm `PracticeSectionRepository` dependency vào `PracticeService`.

#### [MODIFY] [PracticeService constructor](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/service/PracticeService.java)
- Inject `PracticeSectionRepository sectionRepository`

---

### Phase 5 — Repository: New Query Method

#### [MODIFY] [PracticeQuestionGroupRepository.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/repository/PracticeQuestionGroupRepository.java)

Thêm method:
```java
List<PracticeQuestionGroup> findBySectionIdOrderByDisplayOrderAsc(Long sectionId);
```

#### [MODIFY] [PracticeSectionRepository.java](file:///d:/Downloads/ksh/src/main/java/com/ksh/features/practice/repository/PracticeSectionRepository.java)

Kiểm tra đã có `findBySetIdOrderByDisplayOrderAsc` — nếu chưa thì thêm.

---

### Phase 6 — Result Templates: Hiển thị theo Section

#### [MODIFY] [rl-result.html](file:///d:/Downloads/ksh/src/main/resources/templates/practice/rl-result.html)

Thêm phần hiển thị **tóm tắt theo từng Section**:
- Nếu `result.sectionResults` có > 1 section → hiển thị bảng chia theo section
- Mỗi section row: tên phần, kỹ năng, điểm/tổng, đúng/sai

#### [MODIFY] [rl-result-detail.html](file:///d:/Downloads/ksh/src/main/resources/templates/practice/rl-result-detail.html)

Thêm tab/section header phân nhóm groups theo section với badge kỹ năng.

---

### Phase 7 — Player: Hiển thị Section-Aware Question Numbers

#### [MODIFY] [player.html](file:///d:/Downloads/ksh/src/main/resources/templates/practice/player.html)

Hiện tại player dùng `${q.questionNo()}` từ `PracticeQuestionRow` — đây là đúng nếu DB đã đúng. Sau Phase 2 (Publisher enforce), DB sẽ có questionNo section-local. Không cần thay đổi logic player.

Thêm header hiển thị "Phần X/N · Kỹ năng" khi `totalSections > 1` — **đã có** (line 32-34).

---

## Open Questions

> [!IMPORTANT]
> **Câu hỏi 1**: Khi re-number tự động trong editor, **user có muốn giữ questionNo cũ** (mà họ đã nhập thủ công) hay **cho phép hệ thống reset về auto-numbering**?
> 
> Đề xuất: Khi thêm câu mới → auto-number. Khi move group → auto re-number section. Khi user edit `q-no` manually → validate nhưng không override.

> [!IMPORTANT]
> **Câu hỏi 2**: Result View cho đề MIXED (Nghe + Viết): phần Viết/Nói vẫn dùng `result.html` (có rubric AI), phần Đọc/Nghe dùng `rl-result.html`. Nếu đề có **cả hai loại** trong cùng một đề MIXED, route nào được dùng?
>
> Đề xuất: Giữ logic hiện tại (`isReadingOrListeningOrAutoMixed`) — nếu có ESSAY/SPEAKING thì dùng `result.html`, ngược lại dùng `rl-result.html`. Phase 4 chỉ bổ sung section breakdown vào `rl-result-detail.html`.

> [!NOTE]
> **Câu hỏi 3**: Có cần **Flyway migration** để fix `questionNo` trong các đề đã publish (có thể bị cross-section numbering)? Nếu yes thì cần thêm V24 migration.

---

## Verification Plan

### Automated Tests
- Compile check: `mvn compile -pl .` để xác nhận không có lỗi Java
- Spring Boot startup: server khởi động không lỗi

### Manual Verification
1. **Editor**: Tạo đề có 2 phần (Nghe + Đọc). Thêm câu hỏi vào phần Nghe → kiểm tra số câu bắt đầu từ 1. Thêm câu hỏi vào phần Đọc → kiểm tra số câu **cũng bắt đầu từ 1** (section-local), không tiếp nối từ phần Nghe.
2. **Editor**: Di chuyển nhóm lên/xuống → câu hỏi được tự động re-number theo thứ tự mới.
3. **Publish**: Xuất bản đề và kiểm tra DB — `questionNo` trong `practice_questions` phải đúng theo từng section.
4. **Player**: Làm bài đề có 2 phần → số câu hiển thị đúng trong mỗi phần.
5. **Result**: Xem kết quả đề thi hỗn hợp → có breakdown theo từng Section.

---

## Files to Change

| File | Loại thay đổi |
|------|---------------|
| `editor.html` | JS: per-section numbering, reNumber helper |
| `PracticePublisherService.java` | Enforce sectionLocalQNo khi publish |
| `PracticeDraftValidator.java` | Check ordering constraint |
| `PracticeDtos.java` | Thêm `SectionResultRow` |
| `PracticeService.java` | Section-aware result grouping |
| `PracticeQuestionGroupRepository.java` | `findBySectionId...` |
| `PracticeSectionRepository.java` | Verify/add query methods |
| `rl-result.html` | Section breakdown display |
| `rl-result-detail.html` | Section-grouped detail display |
