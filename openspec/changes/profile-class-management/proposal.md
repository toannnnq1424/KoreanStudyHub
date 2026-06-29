# Proposal: User Profile & Class Management (KSH-2.1 - 2.3, KSH-3.1 - 3.3)

**Status:** ✅ Approved (Completed)  
**Proposed by:** toannq1424  
**Date:** 2026-06-15  
**Sprint target:** Sprint 2  
**Spec reference:** [specs/profile/spec.md](../../specs/profile/spec.md) & [specs/classes/spec.md](../../specs/classes/spec.md)

---

## 1. Problem Statement

Sau khi có nền tảng đăng nhập, hệ thống cần hỗ trợ người dùng tự cá nhân hóa tài khoản của mình (cập nhật thông tin, thay đổi mật khẩu, tải ảnh đại diện).
Đồng thời, giảng viên cần có khả năng tạo lớp học Hàn ngữ, tạo mã mời tham gia lớp học và quản lý danh sách học viên trong lớp để chuẩn bị cho việc dạy và học.

---

## 2. Proposed Solution

Xây dựng cụm tính năng quản lý cá nhân và quản lý lớp học:
1. **Thông tin cá nhân (KSH-2.1 & 2.2):** Cho phép cập nhật Họ tên, Số điện thoại, Tiểu sử. Upload avatar (JPEG/PNG/WebP, dung lượng tối đa 2MB), lưu trữ cục bộ tại máy chủ.
2. **Đổi mật khẩu (KSH-2.3):** Biểu mẫu thay đổi mật khẩu bằng cách xác thực mật khẩu cũ.
3. **Quản lý lớp học của Giảng viên (KSH-3.1 & 3.2):** Giảng viên có quyền CRUD lớp học, tự động sinh mã mời gồm 6 ký tự ngẫu nhiên. Giao diện xem chi tiết lớp học chia làm các tab điều hướng.
4. **Quản lý Thành viên & Đăng ký (KSH-3.3):** Học viên nhập mã mời lớp học để tham gia. Giảng viên xem danh sách học viên đã tham gia và có nút duyệt/rời lớp.

---

## 3. Scope — In / Out

| In scope | Out of scope |
|----------|-------------|
| Xem và sửa thông tin cá nhân | Thay đổi Email đăng nhập (Email cố định làm định danh) |
| Đổi mật khẩu trong tài khoản | Khôi phục mật khẩu thông qua SMS |
| Giảng viên quản lý lớp của mình | Admin phân công thay đổi giảng viên phụ trách |
| Học viên join lớp bằng code 6 ký tự | Thanh toán học phí hoặc giới hạn số lớp tối đa |
| Upload ảnh đại diện lưu local disk | Lưu trữ ảnh đại diện trên AWS S3 / Cloudinary |

---

## 4. Affected Files

### Mới tạo (NEW) / Sửa đổi (MODIFY)

- `com.ksh.entities.ClassEntity` - Ánh xạ bảng `classes`.
- `com.ksh.entities.Enrollment` - Ánh xạ bảng `enrollments` liên kết Học viên và Lớp học.
- `com.ksh.entities.ClassActivity` - Ghi nhận lịch sử thao tác của lớp.
- `com.ksh.features.controller.profile.ProfileController` & `ChangePasswordController` - Quản lý trang profile.
- `com.ksh.features.controller.classes.ClassesController` - Quản lý lớp học phía Giảng viên.
- `com.ksh.features.service.profile.ProfileService` - Xử lý logic nghiệp vụ profile.
- `com.ksh.features.service.classes.ClassesService` & `ClassMembersService` - Nghiệp vụ lớp học và thành viên.
- `templates/profile/profile.html`, `templates/profile/change-password.html` - Giao diện cá nhân.
- `templates/classes/manage.html`, `templates/classes/form.html`, `templates/classes/detail-members.html` - Giao diện quản lý lớp.

---

## 5. Security Checklist

- [x] Lịch sử thao tác trên lớp học (`ClassActivity`) được lưu đồng thời trong cùng Transaction của các thay đổi lớp.
- [x] Chỉ giảng viên sở hữu lớp mới có quyền sửa đổi thông tin lớp học (kiểm tra `lecturer_id == user.id` hoặc ADMIN/HEAD).
- [x] Mật khẩu cũ được xác thực bằng cách so khớp BCrypt trước khi ghi đè mật khẩu mới.
- [x] Ảnh đại diện được xác thực loại tệp bằng MIME type + Magic bytes phòng tránh upload shell độc hại.
- [x] Tệp ảnh cũ bị xóa khỏi đĩa cứng khi tệp mới được tải lên để tối ưu dung lượng lưu trữ.
