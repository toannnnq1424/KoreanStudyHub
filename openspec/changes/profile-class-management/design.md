# Design: User Profile & Class Management

**Status:** ✅ Approved (Completed)  
**Feature:** KSH-2.1 - 2.3, KSH-3.1 - 3.3  
**Sprint:** 2

---

## 1. Architecture Overview

```
User (Lecturer/Student) ──► ProfileController ──────► ProfileService (Profile/Avatar/Password)
                         │
                         ├─► ClassesController ──────► ClassesService (Create/Edit Class)
                         │
                         └─► ClassMembersController ─► ClassMembersService (Manage Students)
```

- **Giới hạn chỉnh sửa lớp:** Quyền chỉnh sửa lớp được kiểm tra tập trung tại `ClassesService` sử dụng phương thức `getEditable(classId, userId, role)`. Giảng viên chỉ được thao tác trên lớp của họ. HEAD và ADMIN có toàn quyền.
- **Tải ảnh đại diện:** `AvatarStorageService` nhận `MultipartFile`. Sau khi kiểm tra hợp lệ, tệp được lưu vào thư mục `uploads/avatars/`. Đường dẫn URL lưu vào trường `avatar_url` của bảng `users`.
- **Mã mời lớp học:** Mã mời được sinh tự động bằng cách lấy ngẫu nhiên 6 ký tự chữ và số. Đảm bảo tính duy nhất bằng cách retry 3 lần nếu xảy ra đụng độ trước khi ném ra lỗi `ClassCodeGenerationException`.

---

## 2. Database Schema

### `classes` table
- `id` bigint AUTO_INCREMENT PRIMARY KEY
- `name` varchar(300) NOT NULL
- `code` varchar(6) UNIQUE NOT NULL (mã invite)
- `lecturer_id` bigint NOT NULL (FK users.id)
- `description` text
- `start_date` date
- `end_date` date
- `max_students` int
- `status` varchar(20) DEFAULT 'ACTIVE'
- `created_at` timestamp DEFAULT CURRENT_TIMESTAMP
- `deleted_at` timestamp NULL DEFAULT NULL

### `enrollments` table
- `id` bigint AUTO_INCREMENT PRIMARY KEY
- `class_id` bigint NOT NULL (FK classes.id)
- `user_id` bigint NOT NULL (FK users.id)
- `status` varchar(50) NOT NULL (ACTIVE, PENDING, REJECTED)
- `join_method` varchar(50) NOT NULL (CODE, MANUAL)
- `enrolled_at` timestamp DEFAULT CURRENT_TIMESTAMP

### `class_activities` table
- `id` bigint AUTO_INCREMENT PRIMARY KEY
- `class_id` bigint NOT NULL
- `type` varchar(50) NOT NULL (CREATE, UPDATE, DELETE, ENROLL)
- `description` text NOT NULL
- `actor_id` bigint NOT NULL (FK users.id)
- `created_at` timestamp DEFAULT CURRENT_TIMESTAMP

---

## 3. Storage Design (Avatars)
Tệp tải lên được lưu tại: `<project_root>/uploads/avatars/<uuid>.<extension>`.
Cấu hình static resource mapping trong Spring Boot (`WebConfig.java`):
```java
@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/uploads/**")
            .addResourceLocations("file:uploads/");
}
```

---

## 4. UI Layout & Styling
- Giao diện Profile chia hai phần: ảnh đại diện lớn bên trái và form cập nhật thông tin bên phải.
- Màn hình Lớp học hiển thị danh sách dạng Card Grid. Mỗi lớp được gán màu gradient ngẫu nhiên bằng CSS để tăng tính thẩm mỹ.
- Danh sách thành viên lớp hiển thị danh sách dạng Table có cột trạng thái, ảnh đại diện nhỏ, nút cho phép giảng viên Kick học viên ra khỏi lớp.
