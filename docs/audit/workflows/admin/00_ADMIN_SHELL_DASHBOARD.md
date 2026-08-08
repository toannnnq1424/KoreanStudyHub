# Workflow audit: Admin shell, dashboard và hub cài đặt

Đây là toàn bộ bốn GET handler của `AdminController`; controller này không ghi DB. Class-level permission là `PERM_dashboard.system` tại `src/main/java/com/ksh/features/admin/controller/AdminController.java:35-38`.

## 1. Mở `/admin`

```text
GET /admin
```

`AdminController.root`, `AdminController.java:68-72`, không gọi service; trả `redirect:/admin/dashboard`.

## 2. Dashboard hệ thống

```text
GET /admin/dashboard
```

`AdminController.dashboard`, `AdminController.java:74-86`, gọi ba method rồi render `templates/admin/dashboard.html`:

1. `AdminDashboardService.stats()`;
2. `AdminDashboardService.usersByRole()`;
3. `AdminDashboardService.recentClasses(5)`.

`AdminDashboardService`, `src/main/java/com/ksh/features/admin/service/AdminDashboardService.java:45-100`, chạy các SQL read-only:

- user active, không soft-delete;
- mọi class không soft-delete;
- subject active;
- class `ACTIVE` không soft-delete;
- group user active theo role;
- 5 class mới nhất, LEFT JOIN subject và owner.

Template `admin/dashboard.html:19-121` render bốn KPI, donut role Chart.js tải từ CDN và bảng 5 class. Không có polling/API JSON; số liệu chỉ đổi khi reload. CDN Chart.js lỗi thì số liệu server/table vẫn có, chỉ biểu đồ client không dựng.

`static/js/admin.js:12–55` không query lại user/class. Nó đọc `data-labels`, `data-values`, `data-colors` mà Thymeleaf đã đặt trên `#roleChart`, chuyển thành mảng và gọi Chart.js để vẽ doughnut. Các mảng/chart chỉ sống trong page DOM; không có score/session hay record client nào được ghi lại. Vì vậy số liệu authoritative vẫn là ba query server ở trên, không phải biểu đồ.

## 3. Hub cài đặt

```text
GET /admin/settings
```

`AdminController.settingsIndex`, `AdminController.java:88-93`, chỉ set active sidebar và render `templates/admin/settings.html`. Hub không load/save config; các card dòng 31-91 điều hướng sang:

- AI global `/admin/settings/ai`;
- `GENERAL_UPLOADS` `/admin/settings/storage`;
- SMTP/general/OAuth/KRDICT/system prompts;
- Practice AI `/admin/settings/practice-ai`;
- Practice storage `/admin/settings/storage-profiles`.

Hiệu lực từng form nằm trong bốn tài liệu Admin còn lại. Đáng chú ý, permission vào hub là `PERM_dashboard.system`, còn từng trang con kiểm permission hẹp riêng; nhìn thấy link không tự cấp quyền lưu cấu hình.

## 4. Tab “Lớp học” Admin hiện là placeholder

```text
GET /admin/classes
```

`AdminController.placeholder`, `AdminController.java:95-102`, render `templates/admin/placeholder.html`; trang ghi tính năng đang phát triển (`placeholder.html:14-21`). Nó không gọi `ClassesService`, không list class, không có POST. Bề mặt quản lý lớp thật hiện dùng `/lecturer/classes` với policy role tương ứng, không phải tab này.
