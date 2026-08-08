# Practice lecturer authoring: draft, autosave, publish, version và cộng tác

Mọi route trong chương này yêu cầu `Roles.PREAUTH_LECTURER`; controller luôn lấy actor từ principal. ID owner/collaborator không lấy từ hidden field để quyết định quyền.

## 1. Mở dashboard quản lý

Giảng viên bấm quản lý Practice → `GET /practice/manage`. `PracticeManageController.dashboard`, dòng 78–160:

- query set do actor tạo, optional `status`;
- query draft theo owner;
- query active collaboration grants và shared sets;
- bulk-load tên owner/collaborator;
- render `practice/manage/dashboard` với draft, published/shared set và action phù hợp governance state.

GET chỉ đọc. `ownerLocked`, `ARCHIVED` và collaboration grant quyết định nút nào render, nhưng service vẫn kiểm lại khi POST.

## 2. Bấm “Tạo bộ luyện tập”

Form `practice/manage/dashboard.html:35` gửi:

```text
POST /practice/manage/create
```

`PracticeDraftController.createEmptyDraft`, dòng 98–102, gọi `PracticeDraftService.getOrCreateEmptyDraft(userId)`, tránh tạo nhiều empty draft vô nghĩa, rồi redirect `/practice/manage/drafts/{draftId}`. GET `/create` không mutate và redirect dashboard (`93–96`).

Editor GET (`104–122`) owner-load draft, trả `draftJson`, authoring catalog, objective explanation publish blockers và render `practice/manage/editor`.

## 3. Editor tải authoring catalog và preview

- `GET /practice/manage/authoring-catalog` (`PracticeDraftController:124–131`) trả canonical question types/content/answer rules để frontend không hard-code contract độc lập.
- Preview button/dirty preview gửi JSON `{draftJson}` tới `POST /practice/manage/drafts/{id}/preview` (`133–151`). Controller owner-check draft, `PracticeDraftPreviewService.preview` parse/normalize/render learner-safe DTO; invalid JSON trả 400. Preview không lưu DB.
- `GET .../publish-blockers` (`153–164`, editor script khoảng dòng 6597) tính explanation/editorial blockers mới nhất trước publish.

## 4. Autosave

Editor script tại `practice/manage/editor.html:6172` gửi:

```text
POST /practice/manage/drafts/{draftId}/autosave
Content-Type: application/json
{
  "draftJson": "...",
  "title": "...",
  "description": "...",
  "version": 12
}
```

`PracticeDraftController.autosave`, dòng 167–214:

1. Parse client version.
2. `PracticeDraftService.saveDraftState(draftId,userId,...,clientVersion)` owner/collaborator-check, validate immutable draft identity and optimistic version, persist JSON/title/description, increment version/update time.
3. `PracticeDraftValidator.validate` trả lỗi/warning ngay cho UI.
4. `ObjectiveExplanationEditorialService.publishBlockers` trả blockers.
5. JSON success gồm `version`, validation, blockers.
6. Optimistic/Speaking prompt authority conflict trả HTTP 409; UI phải reload/merge, không last-write-wins.

Speaking prompt fields có authority riêng; generic autosave không được ghi đè transcript/audio state do Speaking service sở hữu.

## 5. Upload ảnh/audio thủ công vào draft

Editor gọi:

- `POST /practice/manage/drafts/{id}/upload-image` (`editor.html:2823/4880`, controller `302–325`);
- `POST /practice/manage/drafts/{id}/upload-audio` (`editor.html:2930`, controller `276–300`).

Multipart field `file`; image tối đa 10 MiB và extension png/jpg/jpeg/gif/webp; audio tối đa 50 MiB và mp3/wav/m4a/ogg/webm. `LecturerAssetService.createDraftUploadAsset` owner-check draft, verify content/storage, tạo `LecturerAsset`; JSON trả `assetId`, protected `/practice/materials/{id}/content`, filename. Extension đúng nhưng bytes sai vẫn bị content verifier/storage layer từ chối.

## 6. Bấm “Xuất bản”

Form `practice/manage/editor.html:73` gửi:

```text
POST /practice/manage/drafts/{draftId}/publish
```

`PracticeDraftController.publishDraft`, dòng 216–241, gọi `PracticePublisherService.publish(draftId,userId)`.

Publisher transaction thực hiện:

1. khóa/authorize draft và target set;
2. chạy `PracticeDraftValidator` + canonical assessment contract;
3. buộc objective AI explanation cần editorial approval không còn blocker;
4. buộc Speaking prompt/audio/transcript publication contract sẵn sàng;
5. `PracticePublishedGraphMutationGuard` chặn mutation graph đã có learner history khi policy yêu cầu version mới;
6. tạo immutable `PracticeSetVersion`, `PracticeTestVersion`, `PracticeSectionVersion`, group/question versions và answer/content snapshots;
7. tạo `PracticePublishedVersion`, cập nhật set current published identity/status;
8. ghi edit/version/material usage/lifecycle state trong cùng transaction.

Thành công redirect learner set `/practice/sets/{setId}`. Validation/governance failure redirect editor với message; không xuất bản một phần. Published graph không trỏ live vào draft JSON.

## 7. Sửa một set đã publish

Dashboard form `dashboard.html:172/174/229/231` gửi:

```text
POST /practice/manage/sets/{setId}/edit[?preview=true]
```

`PracticeManageController.editSet`, dòng 62–70, gọi `PracticeDraftService.createDraftFromPublishedSet(setId,userId)`. Service authorize owner/active collaborator + owner lock/archive policy, project immutable published version thành draft mới, không sửa rows version cũ. Redirect editor, optional `?preview=1`. GET fallback (`72–76`) không tạo draft.

## 8. Lịch sử và restore version

Click **Lịch sử** → `GET /practice/manage/revisions?setId=...` (`dashboard.html:173/230`; controller `162–247`). Controller:

- owner hoặc active collaborator check;
- query `PracticePublishedVersion` newest first;
- tính `canRestore` từ owner/ownerLock/grant;
- nạp explanation recovery rows nếu actor có stricter publish authority;
- render `practice/manage/revisions`.

Bấm restore:

```text
POST /practice/manage/sets/{setId}/versions/{versionId}/restore
```

`PracticeManageController.restorePublishedVersion`, dòng 283–297, gọi `PracticeRevisionService.restorePublishedVersion`. Service verify version thuộc set, permission và graph mutation guard; tạo/publish **version mới** có provenance `restoredFrom...`, không đổi hoặc xóa lịch sử cũ. Redirect revisions với flash.

## 9. Lock, unlock, archive, unarchive

Dashboard buttons dòng 175–178 gửi chung route:

```text
POST /practice/manage/sets/{setId}/{action:lock|unlock|archive|unarchive}
```

`PracticeManageController.lifecycle`, dòng 300–319, dispatch `PracticeLifecycleService`:

- lock/unlock là owner governance, ảnh hưởng collaborator edit/restore;
- archive loại set khỏi catalog/new attempts nhưng không xóa versions/results;
- unarchive restore lifecycle state nếu authority cho phép;
- service khóa set, gọi permission policy (`practice.lock/archive/restore`), entity transition và audit.

Regex path không nhận action khác. Controller luôn redirect dashboard; lỗi không làm partial transition.

## 10. Chia sẻ và thu hồi cộng tác

Owner nhập email tại `dashboard.html:181`:

```text
POST /practice/manage/sets/{setId}/share
email=lecturer@example.com
```

`PracticeManageController.shareSet`, dòng 321–334, gọi `PracticeAuthoringCollaborationService.shareSetByEmail`: owner-check, resolve active lecturer, cấm self/duplicate invalid grant, persist/re-activate collaboration. Grant mở quyền tạo draft/xem history theo owner-lock policy; không chuyển ownership.

Thu hồi button `dashboard.html:196` gửi `POST /sets/{setId}/collaborators/{collaboratorId}/revoke`; controller dòng 336–351 gọi service set `revokedAt`. Draft/published history không bị xóa; request sau của collaborator bị policy chặn.

## 11. Xóa draft

Form ở dashboard dòng 112 hoặc hidden editor form dòng 18 gửi `POST /practice/manage/drafts/{id}/delete`. `PracticeDraftController.deleteDraft`, dòng 261–273, gọi `PracticeDraftService.deleteDraft` (`PracticeDraftService.java:476–492`): service owner-authorize/lock, teardown Speaking prompt nếu có, rồi **hard-delete row `practice_drafts`**. Đây không phải soft delete. Asset/object không bị xóa trực tiếp bởi controller; teardown và reference guard chỉ queue lifecycle delete cho private asset đã thực sự unreferenced. Redirect dashboard dù success hay lỗi (flash message).

## 12. Retry explanation từ revision history

`POST /practice/manage/sets/{setId}/explanations/{questionVersionId}/retry` (`PracticeManageController:250–280`) gọi `QuestionExplanationRetryService.retryQuestionVersion`:

- verify question version thuộc set và actor có publish/recovery authority;
- READY/PENDING không tạo duplicate;
- retry quota/rate-limit trả seconds;
- otherwise enqueue generation task;
- redirect revisions. AI không chạy trong form POST.
