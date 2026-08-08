# Workflows: trang chủ theo vai trò và tổng quan giảng dạy

Hai màn này là read/navigation workflows. Chúng không ghi DB, không gọi AI, không gửi notification/mail.

## 1. Mở trang chủ sau đăng nhập

### Request → controller

Sau login hoặc click logo/home, browser gửi:

```text
GET /
```

`SecurityConfig.java:241-242` yêu cầu authenticated vì `/` rơi vào `anyRequest().authenticated()`.

`HomeController.home`, dòng 31-36:

1. Nhận generic Spring `Authentication`, dùng được cho cả form principal và OAuth/OIDC principal (`HomeController.java:21-25`).
2. Đưa `authentication.getName()` và authorities vào model (`33-34`).
3. Render `home.html` (`35`).

Không service/repository/transaction. Tên hiển thị thực tế trong template lấy `principal.fullName` ở `home.html:15-17`; email/username lấy trực tiếp principal dòng 19-24.

### UI được mở theo role

Các card chỉ là `<a>` navigation, không fetch:

| Role/UI | Link source |
|---|---|
| LECTURER/LEADER/ADMIN → Tổng quan giảng dạy | `home.html:44-54` → `/lecturer/dashboard` |
| LECTURER/LEADER/ADMIN → Lớp học | dòng 56-66 → `/lecturer/classes` |
| LECTURER/LEADER/ADMIN → Kho học liệu | dòng 68-78 → `/lecturer/library` |
| LECTURER/LEADER/ADMIN → Question Bank | dòng 80-90 |
| STUDENT/LECTURER → Practice | dòng 92-103 |
| STUDENT → Lớp của tôi | dòng 105-114 |
| ADMIN → Quản trị | dòng 116-125 |
| mọi authenticated user → Hồ sơ, Khám phá | dòng 127-147 |

`sec:authorize` chỉ điều khiển khả năng nhìn card; endpoint đích vẫn phải tự enforce role. Home không quyết định hoặc đổi quyền.

## 2. Mở `/lecturer` và redirect dashboard chuẩn

```text
GET /lecturer
```

`LecturerDashboardController.root`, dòng 54-57, trả:

```text
redirect:/lecturer/dashboard
```

Controller có `@PreAuthorize(PREAUTH_LECTURER_OR_ABOVE)` (`LecturerDashboardController.java:40-42`) và security chain cũng giới hạn `/lecturer/**` cho LECTURER/LEADER/ADMIN (`SecurityConfig.java:236`). Không query DB ở redirect này.

## 3. Load KPI và bảng lớp trên teaching dashboard

### Thao tác người dùng và input

Mở card home hoặc follow redirect:

```text
GET /lecturer/dashboard?q={class name/code}&page={0-based}&size={n}
```

Form tìm kiếm ở `lecturer/dashboard.html:81-88`; pager ở dòng 144-146. Nút **Quản lý lớp học** dòng 22-25 chỉ navigate `/lecturer/classes`.

### Controller

`LecturerDashboardController.dashboard`, dòng 63-81:

1. Bind `q`, `page`, `size`; user/role lấy principal.
2. Gọi `LecturerDashboardService.getDashboard` (`70-71`).
3. Lấy size đã clamp từ Page object, thêm KPI/page/query/pager params vào model (`72-79`).
4. Render `lecturer/dashboard.html` (`80`).

### Service → batch repositories

`LecturerDashboardService.getDashboard`, transaction read-only dòng 53-113:

1. `LecturerDashboardQuerySupport.loadScopedClasses` (`56`). LECTURER query class owned bằng `ClassRepository.findAllByLecturerIdOrderByCreatedAtDesc`; role khác query mọi class non-deleted (`LecturerDashboardQuerySupport.java:53-59`, repository `ClassRepository.java:35-37`).
2. Scope rỗng trả KPI 0 + Page rỗng nhưng vẫn giữ size clamp (`57-62`).
3. Batch class ids rồi load:
   - số enrollment ACTIVE group theo class (`64-65`; `EnrollmentRepository.java:58-61`);
   - PUBLISHED lesson ids group theo class (`66`; `LessonRepository.java:54-58`);
   - ACTIVE student ids group theo class (`67`; `EnrollmentRepository.java:68-70`);
   - mọi `(user,lesson)` COMPLETED trong set lesson (`68-69`; `LecturerDashboardQuerySupport.java:89-103`);
   - subject code bằng `DepartmentRepository.findAllById` (`70-75`).
4. Với từng class, tính student count, class ACTIVE flag, và average completion của ACTIVE students trên PUBLISHED lessons (`LecturerDashboardService.java:77-103`). Zero student hoặc zero lesson → 0 (`LecturerDashboardQuerySupport.java:105-129`).
5. KPI:
   - `totalClasses = classes.size`;
   - `totalStudents = tổng enrollment ACTIVE qua các class` (cùng một student ở hai class được tính hai lần);
   - `activeClasses = số class status ACTIVE`;
   - `overallAvg = trung bình cộng classAvg của mọi class`, **không weighted theo số student** (`LecturerDashboardService.java:105-107`).
6. Sau khi KPI đã tính trên full scope, filter substring case-insensitive trên class name/subject code và paginate in-memory (`109-112`; support `131-160`). Page âm về 0, size clamp, offset quá lớn trả page rỗng.

### UI nhận kết quả

- KPI cards nằm ở `lecturer/dashboard.html:28-68`.
- Bảng lớp ở dòng 91-123; mỗi row có status, sĩ số, average và link **Xem tiến độ** tới `/lecturer/classes/{id}/progress` (`104-119`).
- Scope không có class: empty state dòng 125-132.
- Có class nhưng search không match: empty filter + link xóa filter dòng 134-142.

Search là GET/PRG tự nhiên; không mutation/flash ngoài flash có sẵn từ workflow trước.

`static/js/lecturer-dashboard.js` hiện là file hook rỗng ngoài comment/IIFE (`:1–11`): không fetch KPI, không polling và không thay đổi filter. Toàn bộ bảng/KPI của `templates/lecturer/dashboard.html` đến từ `LecturerDashboardService.getDashboard` trong chính request GET; ô search submit lại server, không lọc một dataset cache ở JavaScript.

## Phát hiện scope quan trọng

Comment service nói LECTURER chỉ class owned, `LEADER/ADMIN` thấy tất cả (`LecturerDashboardService.java:24-26`), và code thực tế đúng như vậy (`LecturerDashboardQuerySupport.java:54-59`). Dashboard này **không áp `LeaderDepartmentResolver`/department scope**. Vì thế Leader qua `/lecturer/dashboard` hiện thấy aggregate và row của mọi class non-deleted, khác với các màn leader khác có thể giới hạn department. Đây không chỉ là khác biệt UI; scope được quyết định ngay ở repository selection.

## Điều dashboard không làm

- Không tạo/cập nhật lớp, enrollment hay progress.
- Không refresh nền/WebSocket; số liệu chỉ đổi khi browser GET lại.
- Không dự đoán tiến độ, gọi AI hoặc gửi cảnh báo.
- Không có JSON API riêng cho KPI; controller render server-side Thymeleaf.
