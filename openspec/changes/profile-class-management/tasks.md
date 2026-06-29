# Tasks: User Profile & Class Management

**Status:** 🔲 Ready for implementation (Simulated Plan)  
**Sprint:** 2  
**Design doc:** [design.md](./design.md)  
**Spec:** [specs/profile/spec.md](../../specs/profile/spec.md) & [specs/classes/spec.md](../../specs/classes/spec.md)

---

## Phase 1 — Database Updates & Migrations

- [ ] **[DB-1] Schema cho Profile người dùng**
  - Viết file migration `V4__user_profile_fields.sql` để thêm cột `phone`, `bio`, `avatar_url` vào bảng `users`.

- [ ] **[DB-2] Schema cho Quản lý lớp học**
  - Viết file migration `V5__classes.sql` để tạo bảng `classes` phục vụ lưu trữ thông tin lớp học.
  - Tạo bảng `class_activities` để lưu trữ nhật ký hoạt động.

- [ ] **[DB-3] Schema cho Quản lý thành viên**
  - Viết file migration `V6__enrollments.sql` tạo bảng liên kết đăng ký lớp giữa học viên và lớp học.

---

## Phase 2 — Backend Service & Logic

- [ ] **[BE-1] Xem & Cập nhật Profile**
  - Viết `ProfileForm` DTO.
  - Thêm API `GET /profile` và `POST /profile`.
  - Thực hiện cập nhật thông tin người dùng trong DB thông qua `ProfileService`.

- [ ] **[BE-2] Upload ảnh đại diện (Avatar)**
  - Viết `AvatarStorageService` xử lý lưu tệp ảnh.
  - Kiểm tra điều kiện tệp: kích thước < 2MB, đúng định dạng PNG/JPG/WebP bằng magic bytes.
  - Viết API `/profile/avatar` thực hiện lưu ảnh và xóa tệp ảnh cũ.

- [ ] **[BE-3] Đổi mật khẩu tài khoản**
  - Viết `ChangePasswordForm` DTO.
  - Viết API `/profile/change-password` xác thực mật khẩu hiện tại bằng `BCryptPasswordEncoder`.

- [ ] **[BE-4] Giảng viên CRUD lớp học**
  - Viết `ClassesController` và `ClassesService`.
  - Sinh mã mời lớp học gồm 6 ký tự. Thử lại tối đa 3 lần nếu trùng mã trước khi báo lỗi.
  - Đảm bảo cơ chế Transaction cho cả việc thay đổi lớp học và lưu nhật ký hoạt động.

- [ ] **[BE-5] Quản lý thành viên đăng ký**
  - Viết API cho học viên tham gia lớp học bằng mã mời.
  - Viết API liệt kê học viên cho giảng viên xem và cho phép duyệt hoặc buộc rời lớp học.

---

## Phase 3 — Frontend Views

- [ ] **[FE-1] Form thông tin cá nhân & Đổi mật khẩu**
  - Tạo giao diện `profile.html` và `change-password.html`.
  - Hiển thị ảnh đại diện tròn, cho phép click để upload ảnh mới.

- [ ] **[FE-2] Danh sách lớp học dạng Card**
  - Tạo trang `classes/manage.html` hiển thị danh sách lớp.
  - Tạo màu nền gradient ngẫu nhiên cho từng thẻ Card lớp học để tăng thẩm mỹ.

- [ ] **[FE-3] Xem chi tiết lớp học & Danh sách thành viên**
  - Tạo giao diện xem lớp học chia làm nhiều tab (Board, Members, Settings...).
  - Thiết kế bảng danh sách học viên trong tab Members kèm nút "Rời lớp" và duyệt đăng ký.

---

## Phase 4 — Testing

- [ ] **[Test-1] Kiểm thử logic upload ảnh**
  - Kiểm tra upload ảnh đúng định dạng, sai định dạng, dung lượng quá 2MB.
  - Kiểm tra tệp ảnh cũ có bị xóa sau khi tải ảnh mới thành công hay không.
- [ ] **[Test-2] Kiểm thử đổi mật khẩu**
  - Kiểm tra đổi mật khẩu thành công khi nhập đúng mật khẩu hiện tại.
  - Kiểm tra đổi mật khẩu thất bại khi sai mật khẩu hiện tại.
- [ ] **[Test-3] Kiểm thử bảo mật lớp học**
  - Kiểm tra giảng viên A không thể chỉnh sửa lớp học của giảng viên B.
  - Học viên không thể thực hiện các thao tác quản trị lớp học.
