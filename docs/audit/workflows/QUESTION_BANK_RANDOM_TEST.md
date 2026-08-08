# Workflow: tạo đề random từ Question Bank

## Thao tác người dùng

Giảng viên/Leader mở trang Question Bank, chọn mã môn và mở collapse **“Tạo đề random từ bộ chung”** tại `questionbank/list.html:122`.

Form bắt đầu tại `questionbank/list.html:124` và gửi:

| Field | UI | Ý nghĩa |
|---|---|---|
| `subjectId` | hidden, dòng 126 | mã môn đang chọn |
| `title` | text, dòng 129 | tên đề, có thể trống |
| `scope` | radio, dòng 134–139 | `SUBJECT`, `CHAPTER` hoặc `LESSON` |
| `lessonTemplateId` | select, dòng 144/153 | bài đại diện cho chương hoặc bài cụ thể |
| `questionCount` | number | số câu cần lấy |
| `classIds` | checkbox | lớp nhận đề, không bắt buộc |
| CSRF | hidden, dòng 125 | token chống request giả mạo |

Người dùng bấm **“Tạo và lưu vào Kho bài test”**. Browser submit form thường, không dùng fetch:

```text
POST /lecturer/question-bank/generate-test
Content-Type: application/x-www-form-urlencoded
```

## Controller

`LecturerQuestionBankController.generateTest`, dòng 103–126:

1. Spring bind từng form field bằng `@RequestParam`.
2. Actor lấy từ `@AuthenticationPrincipal KshUserDetails`, không nhận user id từ form.
3. Gọi `QuestionBankTestGenerationService.generate(userId, role, subjectId, title, scope, lessonTemplateId, questionCount, classIds)` ở dòng 113–114.
4. Thành công tạo flash message với số câu và số lớp, redirect `GET /lecturer/tests` ở dòng 115–120.
5. `IllegalArgumentException` hoặc `AccessDeniedException`: flash error, giữ `subjectId`, redirect lại Question Bank ở dòng 121–124.

## Service và logic random

Toàn bộ logic nằm trong `QuestionBankTestGenerationService.generate`, dòng 91–167.

### 1. Xác thực actor và input — dòng 95–104

- `requireActor` đọc user DB và buộc role trong entity khớp principal.
- `requireSubject` gọi `QuestionBankAccessPolicy.canAccessSubject` và buộc subject còn active.
- `questionCount`: mặc định 10, ép trong khoảng 1–50.
- Scope lạ được normalize về `SUBJECT`.
- `CHAPTER`/`LESSON` bắt buộc `lessonTemplateId` thuộc đúng subject.

### 2. Lấy candidates — dòng 106–108

```java
List<QuestionBankItem> candidates = new ArrayList<>(itemRepository
    .findBySubjectIdAndWorkflowStatusInOrderByUpdatedAtDescIdDesc(
        subjectId, List.of(QuestionBankItem.STATUS_APPROVED)));
```

Chỉ câu `APPROVED` của mã môn được dùng. Draft/review/rejected/archived không lọt vào đề.

### 3. Lọc scope — dòng 109–121

- `LESSON`: giữ item có `lessonTemplateId` đúng bài đã chọn.
- `CHAPTER`: tải toàn bộ lesson của subject, lấy `chapterTitle` của lesson đại diện rồi giữ item có lesson cùng chapter.
- `SUBJECT`: không lọc thêm.
- Không còn candidates: báo “Phạm vi đã chọn chưa có câu hỏi được duyệt”.

### 4. Random — dòng 125–126

```java
Collections.shuffle(candidates);
List<QuestionBankItem> selected = candidates.subList(
    0, Math.min(requested, candidates.size()));
```

Đây là toàn bộ thuật toán random: shuffle không truyền seed rồi lấy N phần tử đầu. Không có trọng số theo độ khó, không cân bằng loại câu, không lưu seed và không tránh trùng giữa hai lần tạo đề.

### 5. Tạo Test độc lập — dòng 127–157

1. Batch-load options cho các item đã chọn.
2. Tạo `Test(userId, TYPE_MODULE)`.
3. Set title/description/subject, `classId=null`.
4. Duration = `max(10, số câu × 2)` phút.
5. Passing score = `max(1, số câu / 2.0)`.
6. `TIME_MODE_INDIVIDUAL`, `shuffleQuestions=true`, `shuffleOptions=true`, `status=PUBLISHED`.
7. `testRepository.saveAndFlush` tạo test id.
8. Map mỗi QB item sang `QuestionForm`; copy content, explanation, points và options/correct flag.
9. `ExamQuestionBankWriter.appendQuestions` ghi snapshot question vào đề. Đề không giữ live reference để tự thay đổi khi Question Bank item bị sửa sau này.
10. Cập nhật `totalQuestions`; ghi hai activity `CREATED` và `PUBLISHED`.

### 6. Phân phối — dòng 159–166

- Deduplicate `classIds` bằng `LinkedHashSet`.
- Nếu có target, gọi `LecturerExamService.distributePublished`.
- Service phân phối tiếp tục kiểm quyền quản lý từng lớp và trạng thái đề/lớp; client không thể chỉ sửa checkbox value để phân phối vào lớp ngoài quyền.
- Trả `GenerationResult(testId, snapshots.size, distributedCount)`.

Source test được tạo với `classId=null` nhưng có `subjectId`; đó là bản trong Kho bài test do actor quản lý, không phải bản học sinh nhìn thấy theo lớp. Chỉ các snapshot được phân phối (mỗi bản có `classId` target) xuất hiện trong `GET /my/classes/{classId}/tests`, vốn query `classId + PUBLISHED` tại `TestCatalogService.java:100–126`.

### Guard và giới hạn downstream

- `eligibleClasses` chỉ hiện ACTIVE class cùng subject trong scope, nhưng POST vẫn gọi `LecturerExamService.distributePublished`, khóa/check lại từng class, cấm class source (nếu có), sai subject, inactive và duplicate title. Vì generate + distribute ở cùng transaction `QuestionBankTestGenerationService.generate`, một target sai làm rollback cả test source/question snapshot vừa tạo.
- Random không ghi nguồn `QuestionBankItem.id` vào `TestQuestion`; sau confirm không có đường trace DB trực tiếp từ câu trong đề quay lại bank item. `TestActivity` chỉ ghi nguồn ở mức message subject/test.
- Không có mail, notification hoặc WebSocket. Controller success redirect kho test của giảng viên; bản class snapshot là downstream UI của học sinh.

## UI nhận kết quả

Controller redirect `/lecturer/tests`; request GET mới query kho đề, nên đề snapshot vừa tạo xuất hiện trong danh sách. Flash message ví dụ:

```text
Đã tạo và lưu đề 10 câu vào Kho bài test, đồng thời phân phối tới 3 lớp
```

Workflow này hoàn toàn deterministic + database, không gọi AI.
