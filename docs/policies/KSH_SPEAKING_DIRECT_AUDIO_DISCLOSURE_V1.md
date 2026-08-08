# KSH Speaking direct-audio disclosure V1

Artifact ID: `KSH-SPEAKING-DIRECT-AUDIO-DISCLOSURE-V1`

Policy bundle: `KSH_SPEAKING_DIRECT_AUDIO_POLICY_BUNDLE_V1`

Status: `PREPRODUCTION_PRODUCT_PRIVACY_BASELINE`

## Nội dung hiển thị cho người học

### Cho phép dùng bản ghi âm để đánh giá Speaking

Nếu bạn đồng ý, KSH có thể gửi bản ghi âm của câu trả lời Speaking tới nhà
cung cấp dịch vụ đánh giá âm thanh đã được KSH phê duyệt. Mục đích duy nhất là
phân tích các đặc điểm liên quan đến bài luyện nói, như phát âm và độ trôi chảy.

Việc đồng ý là tự nguyện. Nếu không đồng ý, bản ghi âm không được gửi tới bộ
đánh giá trực tiếp. Các chức năng dựa trên transcript có thể vẫn hoạt động,
nhưng điểm hoặc nhận xét cần phân tích âm thanh sẽ không xuất hiện.

Trước khi bạn xác nhận, màn hình consent phải hiển thị tên nhà cung cấp và hành
vi lưu giữ dữ liệu đã được công bố. Khu vực xử lý có thể được hiển thị dưới dạng
thông tin nếu có; `UNKNOWN` được chấp nhận và không được suy đoán. KSH chỉ được
bật chức năng khi tài liệu xác nhận không sử dụng bản ghi âm để huấn luyện mô
hình và hành vi lưu giữ đã được review.

Chỉ reviewer được cấp quyền riêng cho đúng bài làm và trong thời hạn giới hạn
mới được nghe bản ghi âm. Quyền này có thể bị thu hồi và không được suy ra chỉ
từ vai trò giảng viên thông thường.

Bạn có thể rút lại sự đồng ý. Sau khi rút lại, KSH phải chặn mọi lần gửi mới và
mọi quyền nghe của reviewer, đánh dấu audio do KSH quản lý để xóa và đưa object
đó qua cleanup lifecycle hiện có. Local withdrawal không chờ xác nhận xóa phía
nhà cung cấp. KSH không tuyên bố dữ liệu phía nhà cung cấp đã bị xóa nếu không có
bằng chứng riêng. Audit local chỉ ghi `LOCAL_DATA_DELETION_REQUESTED` hoặc
`LOCAL_DATA_DELETED`; không tạo provider deletion receipt giả.

Bạn có thể tiếp tục bài luyện mà không đồng ý. KSH không được phát hành điểm
phát âm, độ trôi chảy hoặc điểm Speaking tổng hợp nếu chưa chứng minh audio đã
được sử dụng hợp lệ và toàn bộ calibration/readiness đang xanh.

Checkbox bắt buộc, mặc định bỏ chọn:

> Tôi đã đọc thông tin trên và đồng ý cho KSH xử lý, gửi bản ghi âm Speaking
> tới nhà cung cấp đã được công bố, chỉ nhằm mục đích đánh giá bài luyện nói.

## Product/privacy rules bound to V1

- Purpose: `PRACTICE_SPEAKING_DIRECT_AUDIO_EVALUATION` only.
- Consent scope: learner + attempt; exact media ID and digest bind at transfer.
- KSH-controlled audio retention ceiling: 30 days, or earlier withdrawal/
  attempt deletion, whichever occurs first.
- Provider readiness requires reviewed non-training and retention documentation.
  Region and provider deletion SLA are not release gates.
- KSH does not claim provider-side deletion guarantees unless separately
  documented.
- Reviewer grant ceiling: 7 days; named reviewer + attempt + purpose only.
- Grant managers: `ACADEMIC_LEADER` and `PRIVACY_RELEASE_OWNER`. `LEADER` maps
  to `ACADEMIC_LEADER`; `PRIVACY_RELEASE_OWNER` requires a separately named
  account assignment and must not mean every `ADMIN`.
- Provider training on learner audio: forbidden.
- Raw audio/handle/digest/provider request ID/secret in application logs:
  forbidden.
- Minor/guardian flows are outside V1. If the product admits learners who
  cannot provide valid consent themselves, direct audio must remain disabled
  until a guardian/legal-basis flow is implemented and approved.

Provider name and evidence IDs are runtime-bound fields, not text that may be
invented in this artifact. Provider region is optional informational metadata;
`UNKNOWN` is valid. Any material purpose, retention, reviewer-access or
training-policy change requires a new immutable disclosure version.
