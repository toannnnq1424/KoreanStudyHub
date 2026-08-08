# Workflow audit: Local/R2, GENERAL_UPLOADS và hai storage profile Practice

Source hiện có hai thế hệ cấu hình storage. UI hiện hành dùng bảng `storage_profiles` với ba identity cố định. Controller `StorageSettingsController`/keys `system_settings.storage.*` vẫn tồn tại cho legacy compatibility/test, nhưng màn `/admin/settings/storage` không còn render form legacy đó.

## 1. Ba vùng dữ liệu và consumer

`StorageProfileCode`, `src/main/java/com/ksh/features/storage/profile/StorageProfileCode.java:5-18`:

| Profile | Prefix vật lý cố định | Consumer ghi mới |
|---|---|---|
| `GENERAL_UPLOADS` | `general-uploads/` | avatar, lesson video/attachment, library, flashcard image, exam image và upload ngoài Practice |
| `PRACTICE_AUTHORING` | `practice-authoring/` | lecturer assets: ảnh/audio/tài liệu/candidate authoring |
| `PRACTICE_SPEAKING` | `practice-speaking/` | learner Speaking audio temporary/ready và cleanup |

Practice không inject `GENERAL_UPLOADS`; hai profile Practice thiếu/tắt/sai cấu hình thì upload fail closed, không rơi về storage global.

## 2. User mở đúng màn nào

### Global uploads

Admin có `PERM_system.storage` mở:

```text
GET /admin/settings/storage
```

`StorageSettingsController.view`, `src/main/java/com/ksh/features/admin/settings/controller/StorageSettingsController.java:54-71`, vẫn nạp legacy `form` nhưng template hiện hành `templates/admin/settings-storage.html:28-55` chỉ render card `GENERAL_UPLOADS`. Nút **“Sửa cấu hình”** ở dòng 45 mở:

```text
GET /admin/settings/storage-profiles/GENERAL_UPLOADS/edit
```

Nếu identity bị thiếu, nút dòng 52 mở `/admin/settings/storage-profiles/GENERAL_UPLOADS/new`.

### Practice storage

Admin mở:

```text
GET /admin/settings/storage-profiles
```

`StorageProfileController.list`, `.../StorageProfileController.java:48-58`, chỉ lọc `PRACTICE_AUTHORING` và `PRACTICE_SPEAKING`, render `templates/admin/settings-storage-profiles.html`. Mỗi card có **Sửa cấu hình**, **Tạm tắt/Bật lại**, và khi disabled có **Xóa nếu chưa tham chiếu** (`settings-storage-profiles.html:25-45`).

## 3. Admin chọn Local hoặc R2 và bấm Lưu

Form chung cho cả ba identity tại `templates/admin/settings-storage-profile-form.html:21-45`:

| Field | Dòng | Ý nghĩa |
|---|---:|---|
| `profileCode` | hidden 23 | identity cố định, không được đổi |
| `backend` | 28 | `LOCAL` hoặc `R2` |
| `enabled` | 29 | write gate |
| `accountId` | 35 | R2 syntax/readiness metadata |
| `accessKeyId` | 36 | AWS basic credential id |
| `secretAccessKey` | 37 | secret; blank/`********` giữ cũ |
| `bucket` | 38 | private bucket dùng cho mọi S3 operation |
| `endpoint` | 39 | endpoint override, bắt buộc HTTPS khi enabled |
| `region` | 40 | AWS SDK Region, default `auto` |
| `keyPrefix` | 43 | readonly, phải bằng fixed prefix enum |
| `revision` | hidden 23 | optimistic concurrency |

Bấm **“Lưu cấu hình”**:

```text
POST /admin/settings/storage-profiles
Content-Type: application/x-www-form-urlencoded
```

`StorageProfileController.save`, `.../StorageProfileController.java:83-103`, validate DTO/principal rồi gọi `StorageProfileAdminService.save`.

`StorageProfileAdminService.save`, `.../StorageProfileAdminService.java:67-102`:

1. Buộc code/backend tồn tại và prefix đúng identity.
2. Blank hoặc masked secret giữ secret DB cũ.
3. Create từ chối row đã tồn tại; edit lock row và bắt buộc revision khớp.
4. Nếu checkbox enabled=true, gọi `StorageProfileResolver.validate` trước khi commit.
5. Save/flush, invalidate cached S3 client đúng profile; request kế tiếp rebuild client bằng revision mới.

### Điều kiện R2 được coi là cấu hình đủ

`StorageProfileResolver.requireR2`, `src/main/java/com/ksh/features/storage/profile/StorageProfileResolver.java:96-118`, yêu cầu:

- account ID không blank và khớp `[A-Za-z0-9_-]{3,128}`;
- Access Key ID và Secret Access Key không blank;
- bucket khớp pattern lowercase kiểu S3, dài 3-63;
- endpoint là absolute HTTPS, có host, không user-info/query/fragment;
- region không blank.

Account ID là release/readiness field nhưng `StorageProfileR2Clients.build`, `.../StorageProfileR2Clients.java:40-50`, không đưa accountId vào AWS SDK; client thực tế dùng access key, secret, endpoint và region. Bucket được `StorageProfileObjectStore` đưa vào `R2ObjectStorage` cho S3 operations.

Validation khi save chỉ kiểm syntax/presence, **không gọi HeadBucket**. Vì vậy form có thể lưu+bật dù key không có quyền hoặc bucket không tồn tại; lỗi remote chỉ xuất hiện ở put/open đầu tiên. UI profile hiện không có nút test R2.

### Local không luôn được phép

`StorageProfileResolver` đọc `app.storage-profiles.allow-local`, mặc định `false` (`StorageProfileResolver.java:19-24`). Khi false, profile `backend=LOCAL` không thể được enable: `validate` trả `STORAGE_PROFILE_UNAVAILABLE` ở dòng 47-51. Muốn Local hoạt động phải bật property môi trường và lưu/bật profile; chỉ chọn Local trong form chưa đủ.

## 4. Từ profile đến ghi object thật

`StorageProfileObjectStore.put`, `src/main/java/com/ksh/features/storage/profile/StorageProfileObjectStore.java:27-35`:

1. `resolveForWrite(profileCode)` bắt buộc profile tồn tại, enabled và valid.
2. Validate logical key lowercase/safe, chống `/`, `..`, colon/control/path escape.
3. Ghép vật lý `{fixedPrefix}/{logicalKey}` và fence prefix (`StorageProfileObjectStore.java:92-99`).
4. Nếu LOCAL: gọi `LocalObjectStorage` dưới `app.upload.dir`.
5. Nếu R2: `StorageProfileR2Clients.client(profile)` tạo/cache AWS S3 client theo `(profileCode, revision, key, secret, endpoint, region)`; `R2ObjectStorage.put` ghi vào bucket.

Read dùng `resolveForRead`, không bắt buộc profile enabled nhưng vẫn bắt buộc cấu hình valid (`StorageProfileResolver.java:26-38`). Điều này cho phép tắt write trong khi vẫn đọc object cũ. Delete luôn gọi backend đúng profile và kiểm object không còn (`StorageProfileObjectStore.java:53-60`).

### GENERAL_UPLOADS

Bean `ObjectStorage` toàn product được cấu hình tại `ObjectStorageConfig.java:42-54` thành `GeneralUploadsObjectStorage`. New write luôn:

```text
ObjectStorage.put(key)
  -> StorageProfileObjectStore.put(GENERAL_UPLOADS, key)
  -> general-uploads/{key}
```

`GeneralUploadsObjectStorage.java:26-30` không fallback legacy cho write. Nếu GENERAL_UPLOADS thiếu/tắt, avatar/lesson/library/flashcard/test-image write fail closed.

Đối với read/exist/list/delete, adapter thử exact profile trước, rồi mới legacy store để giữ khả năng đọc byte trước migration (`GeneralUploadsObjectStorage.java:32-91`). Đây là fallback **chỉ cho dữ liệu general cũ**, không áp dụng Practice.

Các consumer `ObjectStorage` thật gồm:

- `AvatarStorageService` / `ProfileController`;
- `LessonVideoStorageService`, `LessonAttachmentStorageService`, `LessonAttachmentsService`;
- `LibraryStorageService`;
- `FlashcardImageStorageService`;
- `ExamImageStorageService` và `PublicUploadsController`;
- stream/range controller cho lesson video/attachments.

### PRACTICE_AUTHORING

`ProfiledPracticeAuthoringStorage`, `.../ProfiledPracticeAuthoringStorage.java:25-71`, là `@Primary AssetStorageService`, hard-code profile `PRACTICE_AUTHORING`. Upload được hash SHA-256, logical key nằm trong `lecturer-assets/...` hoặc `practice-seed/...`, rồi ghi exact profile. DB asset ghi cả `storage_profile_code` và backend provider; load/delete từ chối profile khác.

Nó mở các thao tác upload/serve/lifecycle ảnh, audio prompt, PDF authoring source và lecturer assets mô tả trong `practice/02`, `practice/03`, `practice/05`.

### PRACTICE_SPEAKING

`ProfiledPracticeSpeakingAudioStorage`, `.../ProfiledPracticeSpeakingAudioStorage.java:22-83`, hard-code `PRACTICE_SPEAKING`. Audio learner được đọc vào temp inspection file để giới hạn size/hash, rồi ghi logical key `learner-speaking/temporary/{uuid}`. Khi record DB/transaction sẵn sàng, `promoteTemporary`, dòng 86-102, copy sang `learner-speaking/ready/{uuid}`; cleanup/delete vẫn exact profile.

Nó mở upload/playback/STT input/retention cleanup của Speaking. Nó không tự bật AI; STT/evaluation còn cần Practice AI binding riêng.

## 5. Toggle, delete và secret reveal

### Toggle

Các card submit:

```text
POST /admin/settings/storage-profiles/{code}/toggle
revision=<currentRevision>
```

`StorageProfileController.toggle`, dòng 105-118, gọi `StorageProfileAdminService.toggle`; service dòng 104-114 lock row, kiểm revision, flip, và khi bật validate full config. Tắt chặn write mới nhưng exact read vẫn có thể hoạt động.

### Delete

```text
POST /admin/settings/storage-profiles/{code}/delete
revision=<currentRevision>
```

`StorageProfileAdminService.delete`, dòng 116-130, chỉ xóa khi profile disabled và không còn reference trong lecturer assets, Practice lifecycle/media/cleanup/migration jobs. `GENERAL_UPLOADS` reference count không bao gồm DB URLs của avatar/lesson/library vì các module đó lưu key/URL theo schema khác; UI global hiện cũng không render nút delete.

### Reveal secret

JS `static/js/admin-settings-storage-profiles.js:4-31` gọi:

```text
GET /admin/settings/storage-profiles/{code}/secret
Accept: application/json
```

`StorageProfileController.reveal`, dòng 135-142, trả secret rõ trong JSON `Cache-Control: no-store`; cùng permission storage, không re-auth và không audit reveal riêng.

## 6. Legacy `system_settings.storage.*`: endpoint còn nhưng UI không còn form

`StorageSettingsController` vẫn khai báo:

```text
POST /admin/settings/storage
POST /admin/settings/storage/test
```

Legacy form DTO ghi `storage.provider`, `storage.r2.account_id`, `access_key_id`, `secret_access_key`, `bucket`, `endpoint`, `region` qua `StorageSettingsService.save`, `.../StorageSettingsService.java:73-114`. Test endpoint dùng cấu hình **đã lưu**, dựng S3 client và `HeadBucket` (`StorageSettingsService.java:116-145`).

Nhưng current `settings-storage.html` chỉ có card/profile links, không có `<form action="/admin/settings/storage">`, không load JS test và không có nút gọi `/test`. Một caller thủ công vẫn có thể POST nếu có CSRF/permission, nhưng các new writes ưu tiên exact `GENERAL_UPLOADS`; legacy config chỉ còn ở bridge read compatibility. Vì vậy hướng dẫn Admin hiện hành phải dùng `/admin/settings/storage-profiles/GENERAL_UPLOADS/edit`, không dùng controller legacy.

## 7. Đổi bucket/key không tự di chuyển file cũ

Save profile chỉ cập nhật row/revision và invalidate client; không list/copy/migrate byte. Sau khi đổi bucket/endpoint/key, exact-profile read dùng cấu hình mới ngay, nên object chỉ nằm ở bucket cũ có thể không còn đọc được. Practice có classes `PracticeStorageMigrationJobService`/migration port nhưng không có button/controller/scheduler tự chạy trong source hiện tại; xem `practice/07_BACKGROUND_JOBS_FAILURE_RETENTION.md`.

Do đó thao tác an toàn là migrate/verify bytes trước, rồi đổi profile; không giả định bấm Lưu sẽ chuyển dữ liệu.

## Tóm tắt “nhập R2 thì điều gì mở khóa”

```text
[Chọn đúng profile]
  -> R2 + accountId + accessKeyId + secret + bucket + https endpoint + region
  -> tick enabled
  -> POST /admin/settings/storage-profiles
  -> validate syntax + revision (chưa HeadBucket)
  -> storage_profiles + invalidate client
  -> user upload ở module tương ứng
  -> resolveForWrite(profile)
  -> prefix fence
  -> AWS S3 PutObject vào bucket
  -> DB nghiệp vụ lưu exact profile/key
  -> read/delete sau dùng đúng profile đó
```

## Method-level ledger cho form/profile/legacy endpoint

| Handler exact | Query/mutation và response thật |
|---|---|
| `StorageProfileController.edit` | `GET /admin/settings/storage-profiles/{code}/edit` → `StorageProfileAdminService.form(code)`/`findById`; render form với secret sentinel `********` và revision, hoặc redirect `STORAGE_PROFILE_NOT_FOUND` (`controller:60–69`, service `:58–65`). |
| `StorageProfileController.create` | `GET .../{code}/new` → query `missingCodes`; code đã tồn tại redirect error, code thiếu mới tạo DTO với fixed profile code/prefix và render create form (`controller:71–81`). GET này không insert row. |
| `StorageProfileController.delete` | POST code+expected revision → lock profile, reject revision conflict, yêu cầu disabled và `referenceCount(code)==0`, rồi hard-delete profile/invalidate client; redirect theo GENERAL hay Practice (`controller:120–133`, service `:116–150`). Object trong bucket không bị xóa/migrate. |
| `StorageSettingsController.save` | Legacy POST `/admin/settings/storage` bind provider/R2 fields; `StorageSettingsService.save` validate provider/completeness, giữ secret masked, upsert group `STORAGE`, evict cache và invalidate R2 client; redirect/validation render (`controller:75–101`, service `:73–114`). UI hiện hành không render form này như luồng cấu hình chính. |
| `StorageSettingsController.testConnection` | POST `/admin/settings/storage/test` không nhận draft form; service đọc **saved** group, build client và gọi S3 `HeadBucket`, trả JSON `TestResult` success/error (`controller:103–108`, service `:116–145`). Nó không test giá trị user vừa gõ nhưng chưa save. |

`GENERAL_UPLOADS` mở upload ngoài Practice; `PRACTICE_AUTHORING` mở asset giảng viên; `PRACTICE_SPEAKING` mở learner audio. Không profile nào tự mở AI provider, và save không migrate object cũ.
