# Thiết kế: Refactor module quản lý đề luyện tập dành cho Lecturer (KSH)

> **Folder**: `openspec/changes/practice-manage-refactor/`
> **Trạng thái**: Đang đề xuất

---

## 1. Mục tiêu

Refactor lại toàn bộ module quản lý luyện tập dành cho giáo viên (Lecturer), loại bỏ việc bắt lecturer phải tư duy theo cấu trúc database/entity thô. Thay vào đó, lecturer sẽ làm việc với một mô hình trực quan 4 cấp: **Bộ đề → Phần thi (Section) → Nhóm câu hỏi (Group) → Câu hỏi (Question)**.

Hỗ trợ đề thi hỗn hợp đa kỹ năng (như đề thi TOPIK chuẩn có cả Nghe, Đọc, Viết) và nhiều dạng câu hỏi phong phú hơn là chỉ trắc nghiệm MCQ thô.

---

## 2. Mô hình thực thể & Schema mới

### 2.1 Mối quan hệ 4 cấp

```
PracticeSet (1) 
   └── PracticeSection (N)
         └── PracticeQuestionGroup (N)
               └── PracticeQuestion (N)
```

### 2.2 Các thực thể đề xuất

#### `PracticeSet`
Đại diện cho toàn bộ đề thi / bộ luyện tập.
- `id` (Long, PK)
- `title` (String)
- `description` (String)
- `category` (Enum: `TOPIK_I`, `TOPIK_II`, `TOPIK_MIXED`, `GENERAL_KOREAN`, `CUSTOM`)
- `coverUrl` (String)
- `scope` (Enum: `GLOBAL`, `CLASS`)
- `classId` (Long, optional)
- `status` (Enum: `DRAFT`, `PUBLISHED`, `ARCHIVED`)
- `createdBy` (Long) - ID của giáo viên đăng đề gốc.

#### `PracticeSection`
Mỗi phần thi trong đề (ví dụ: phần Nghe, phần Đọc).
- `id` (Long, PK)
- `setId` (Long, FK)
- `title` (String)
- `skill` (Enum: `LISTENING`, `READING`, `WRITING`, `SPEAKING`)
- `sectionType` (String)
- `instructions` (Text)
- `durationMinutes` (Integer) - Thời gian làm bài của riêng phần thi này.
- `totalPoints` (BigDecimal)
- `displayOrder` (Integer)

#### `PracticeQuestionGroup`
Nhóm các câu hỏi dùng chung tài liệu (bài đọc, bài nghe, hội thoại).
- `id` (Long, PK)
- `sectionId` (Long, FK)
- `label` (String, ví dụ: "17-21" hoặc "Phần A")
- `instruction` (Text)
- `passageText` (Text)
- `transcriptText` (Text)
- `audioUrl` (String)
- `imageUrl` (String)
- `exampleJson` (Text/JSON, ví dụ mẫu <보기>)
- `questionFrom` (Integer, metadata hiển thị)
- `questionTo` (Integer, metadata hiển thị)
- `displayOrder` (Integer)

#### `PracticeQuestion`
Câu hỏi riêng lẻ.
- `id` (Long, PK)
- `groupId` (Long, FK)
- `questionNo` (Integer)
- `questionType` (Enum: `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `TRUE_FALSE`, `TRUE_FALSE_NOT_GIVEN`, `MATCHING`, `MATCHING_INFORMATION`, `MATCHING_HEADING`, `GAP_FILL`, `SHORT_ANSWER`, `TEXT_COMPLETION`, `ORDERING`, `ESSAY`, `SPEAKING`)
- `prompt` (Text)
- `answerConfigJson` (Text/JSON - chứa options, correct answer keys, rules)
- `points` (BigDecimal)
- `explanation` (Text)
- `displayOrder` (Integer)

#### `PracticeEditLog` (Mới)
Lưu lịch sử khi giáo viên khác sửa tài liệu gốc (như chỉnh đáp án, giải thích, bài viết mẫu).
- `id` (Long, PK)
- `setId` (Long, FK)
- `editedBy` (Long)
- `changeSummary` (String)
- `changeDetailsJson` (Text/JSON)
- `editedAt` (LocalDateTime)

---

## 3. Kiến trúc Backend mới (Workspace & Annotations)

Thay vì nhét tất cả logic import tự động không qua giám sát, hệ thống chia nhỏ thành các dịch vụ nghiệp vụ chuyên biệt xoay quanh không gian làm việc (Workspace) và quản lý phiên nhập (Session):

1.  **`PracticePdfImportSessionService`**:
    - Quản lý vòng đời phiên nhập PDF (upload, dải trang hoạt động, trạng thái lưu nháp).
2.  **`PracticePdfRegionService`**:
    - Quản lý tọa độ vẽ vùng của giáo viên và đồng bộ hóa lưu vào DB dưới dạng Annotations.
3.  **`PracticePdfCropService`**:
    - Sử dụng PDFBox cắt (crop) ảnh các vùng vẽ trên trang PDF gốc thành tệp ảnh PNG phục vụ làm đề.
4.  **`PracticePdfAiPayloadBuilder`**:
    - Tổng hợp tọa độ, nhãn vùng vẽ và văn bản bóc tách OCR thành payload JSON chuẩn hóa gửi lên AI.
5.  **`PracticePdfAiOrchestrator`**:
    - Điều phối việc gọi OpenAI API truyền tải hình ảnh crop đa phương thức hoặc text thô để nhận đề thi.
6.  **`PracticePdfDraftAssembler`**:
    - Lấy đề thi thô do AI sinh dạng JSON, biên tập thành cấu trúc nháp để ghi vào `PracticeDraft`.
7.  **`PracticeImportDraftService`**:
    - Hỗ trợ tạo nháp thủ công không qua AI hoặc ghép thêm trang PDF vào nháp có sẵn.
8.  **`PracticeDraftService`**:
    - Lưu tạm autosave, cập nhật nháp, dọn dẹp nháp mồ côi.
9.  **`PracticePublisherService`**:
    - Biên dịch nháp JSON cuối cùng thành đề thi hoạt động chính thức trên DB (`sets`, `sections`, `groups`, `questions`).
10. **`PracticeImportSnapshotService`**:
    - Lưu và phục hồi snapshot của workspace cho tính năng Undo/Hủy bỏ thay đổi.

---

## 4. Cấu trúc Gói nghiệp vụ (Package Restructuring)

Hệ thống được tổ chức theo cấu trúc phân chia tính năng giữa Học viên (Student) và Giảng viên (Lecturer Workspace):

```
com.ksh.features.practice
├── manage/
│   ├── controller/      (API Endpoint: PracticePdfImportApiController, PracticeDraftController, PracticeImportController, PracticeManageController)
│   ├── service/         (Dịch vụ Workspace: SessionService, RegionService, CropService, PayloadBuilder, AiOrchestrator, DraftAssembler, ImportDraftService, DraftService, PublisherService, SnapshotService, LecturerAssetService)
│   ├── validator/       (ImportAiPayloadValidator, PracticeDraftValidator)
│   └── dto/             (AiDocumentImportRequest, v.v.)
├── controller/          (Học sinh làm bài: PracticeController)
├── service/             (Nghiệp vụ học sinh: PracticeService, ReadingListeningExplanationService)
├── dto/                 (PracticeDtos)
├── repository/          (Các JPA Repositories quản lý dữ liệu chính và dữ liệu tạm)
└── pdf/                 (PracticePdfStorageService xử lý file PDF thô)
```

---

## 5. Quy trình Chỉnh sửa & Quyền sở hữu Tài liệu

- **Quyền sở hữu**: Mọi `PracticeSet` được gắn với `createdBy` ghi nhận giáo viên đăng đầu tiên.
- **Sửa đổi tự do**: Khi một giáo viên khác vào chỉnh sửa/bổ sung đáp án/giải thích:
  - Thay đổi được **chấp nhận ngay lập tức** (không cần đợi duyệt).
  - Hệ thống tự động ghi lại một bản ghi vào `PracticeEditLog`.
  - Hệ thống gửi một **Notification** (Thông báo) không đồng bộ tới:
    - Chủ sở hữu gốc của tài liệu (`createdBy`).
    - Tất cả tài khoản có vai trò quản lý cấp cao (`HEAD`, `ADMIN`).

---

## 6. Luồng làm bài liên tiếp (Sequential Section Runner)

Đối với đề thi multi-section:
- Giao diện player phía học viên (`player.html`) sẽ hiển thị và chạy từng Section một cách tuần tự.
- **Ví dụ**: 
  - Học sinh làm Phần Đọc (Section 1) với thời gian 40 phút.
  - Khi hết giờ (hoặc khi học sinh chủ động bấm nộp phần Đọc), hệ thống sẽ tự động gửi đáp án của Phần Đọc lên Server để lưu nháp/đánh giá, sau đó tự động chuyển giao diện sang Phần Nghe (Section 2) với bộ đếm giờ riêng.

---

## 7. Mock Evaluator cho Matching và Gap Fill

Để hệ thống trông chuyên nghiệp và chạy mượt mà ngay cả khi API AI gặp sự cố (quá hạn ngạch 429, mất kết nối):
- Xây dựng `MatchingGapFillMockEvaluatorService`.
- Tự động so khớp đáp án kéo thả (Matching) và điền từ (Gap Fill) dựa trên regex/string matching thô nếu cấu hình key-match.
- Sinh ra giải thích đáp án mẫu tiếng Việt dựa trên prompt, đáp án đúng và giải thích thô được lưu sẵn trong câu hỏi.
