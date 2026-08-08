# Workflow audit: Messaging, notifications, realtime badges và mail delivery

## A. Direct messaging

### 1. Mở inbox và một conversation

Header chat link ở `templates/fragments/app-header.html:189–195` mở:

```text
GET /my/messages?page=<n>
```

`MessagingController.index`, dòng 60–68, gọi `MessagingService.listConversations(userId,page)`. Service dòng 121–126 query tối đa 20 conversation/page, recent activity trước; mỗi row resolve peer, last snippet và unread count.

Click row tại `messaging/index.html:40` gửi:

```text
GET /my/messages/{convId}?page=-1|<n>
```

`MessagingController.open`, dòng 77–97, gọi `MessagingService.openConversation` **trước** khi render sidebar. Service dòng 147–165:

1. `requireParticipant` trả 404 nếu conversation không tồn tại hoặc caller không thuộc cặp, tránh leak id.
2. Bulk mark mọi message do peer gửi là read (`readAt=now`).
3. `page<0` tính last page, 30 messages/page; page 0 là oldest slice (`:167–178`).
4. Trả peer identity + message rows oldest-first.

Controller query lại sidebar/unread sau mark-read nên badge SSR cùng request đã giảm.

### 2. Tạo conversation mới

Nút compose ở `messaging/index.html:23`:

```text
GET /my/messages/new
```

`MessagingController.compose`, dòng 129–139, gọi `searchRecipients(userId,role,null)` và render roster đầy đủ. Search box dòng 85 chỉ filter DOM ở `messaging.js:182–207`; **không gửi query lên server**.

Click recipient form ở `messaging/index.html:101–106` gửi:

```text
POST /my/messages/new
to=<targetUserId>
```

`MessagingController.start`, dòng 147–152, gọi `MessagingService.getOrCreateConversation`. Service dòng 101–110:

- `MessagingAccess.canStartConversation` kiểm target tồn tại, active, không locked, không phải self;
- Student được start với Student/Lecturer/Leader; Lecturer/Leader/Admin được start với mọi active role (`MessagingAccess:47–103`);
- ineligible trả 404 để không xác nhận user id;
- chuẩn hóa pair `(minId,maxId)`, lock stable lower user row rồi find/create unique conversation (`MessagingService:226–238`) để tránh race tạo trùng.

Sau khi conversation tồn tại, enrollment/recipient gate không được kiểm lại khi open/send; chỉ membership. Vì vậy rời lớp không tự xóa lịch sử chat cũ.

### 3. Gửi message: browser → controller → DB → STOMP peer

Composer thực ở `messaging/conversation.html:52–61`, field `body`, tối đa 2.000. `messaging.js:106–147` intercept khi có `fetch`:

```text
POST /my/messages/{convId}
Content-Type: application/x-www-form-urlencoded
X-Requested-With: XMLHttpRequest
body=<trimmed text>
```

Không có JS thì cùng form POST và server redirect, tức progressive fallback.

`MessagingController.send`, dòng 106–121:

- AJAX: gọi service và trả JSON `{ok,messageId,convId,body,createdAt,peerUnread}`;
- native: gọi cùng service rồi redirect thread.

`MessagingService.send`, dòng 255–279:

1. Buộc caller participant, nếu không 404.
2. Trim; blank hoặc >2.000 trả 400.
3. `MessageRepository.saveAndFlush(new Message(convId,senderId,body))`.
4. `ConversationRepository.touchLastMessageAt` bằng single UPDATE để bump ordering.
5. Query tổng unread của peer.
6. Đăng ký `afterCommit` push; chỉ sau DB commit mới `SimpMessagingTemplate.convertAndSendToUser(peer.email,"/queue/messages",payload)` (`:356–380`).

Payload gồm `convId`, sender name, snippet, fullBody và authoritative unreadTotal. Sender JS append bubble sau HTTP 200; lỗi giữ text và hiện inline error.

### 4. Peer nhận realtime và mark read

`WebSocketConfig:23–37` bật STOMP/SockJS endpoint `/ws`, user destination prefix `/user`; SecurityConfig dòng 240 yêu cầu authenticated handshake.

`messaging.js:150–174`:

1. Kết nối SockJS `/ws` bằng HTTP session đã login.
2. Subscribe `/user/queue/messages`.
3. Mọi push cập nhật header badge.
4. Nếu `payload.convId` đúng thread đang mở, append full body và gửi:

```text
POST /my/messages/{convId}/read
```

`MessagingController.markRead`, dòng 162–167, gọi `MessagingService.markConversationRead`, dòng 298–303; service participant-gated, bulk set `readAt`, trả total unread mới.

Nếu WebSocket drop, code không retry loop gây nhiễu; SSR badge khi reload là fallback. Endpoint `GET /my/messages/unread-count` (`MessagingController:154–159`) cũng trả authoritative count, dù client hiện chủ yếu dùng STOMP.

Mọi server-rendered page còn được `MessagingHeaderAdvice.msgUnreadCount`, `src/main/java/com/ksh/features/messaging/controller/MessagingHeaderAdvice.java:20-40`, thêm model `msgUnreadCount`: authenticated user chạy một `MessagingService.unreadCount`, anonymous nhận 0. JSON handlers bỏ qua model này. Vì thế badge ban đầu đến từ DB mỗi SSR request; STOMP chỉ cập nhật sau khi page đã mở.

### 5. Chat trong shell lớp học sinh

Student mở:

```text
GET /my/classes/{classId}/messages?page=-1
```

`StudentClassMessagesController.open`, dòng 43–52, gọi `MessagingService.openClassConversation`. Service dòng 197–224:

- bắt buộc enrollment `ACTIVE`, class tồn tại và có lecturer;
- get-or-create pair student↔lecturer;
- gọi lại `openConversation` để mark read/load messages;
- render `student/class-messages.html` với class sidebar.

Composer dùng fragment conversation và vẫn POST global `/my/messages/{convId}`. URL class chỉ là shell/gate khi mở, không tạo một loại Message khác.

## B. In-app notifications

### 6. Notification được tạo từ workflow domain như thế nào

Các producer thật gọi `NotificationService.create(userId,title,content,type,referenceType,referenceId)`:

| Sự kiện | Call site | Recipient/type |
|---|---|---|
| Student xin vào lớp | `JoinClassService:162–170` | lecturer, `JOIN_REQUEST`, ref CLASS |
| Lecturer duyệt join | `JoinClassService:173–183` | student, `JOIN_APPROVED` + `CLASS_ENROLLED` |
| Lecturer từ chối join | `JoinClassService:186–193` | student, `JOIN_REJECTED` |
| Lecturer submit lớp | `ClassPendingReviewNotifier:37–47` | leader subject, `CLASS_PENDING_APPROVAL` |
| Leader duyệt/từ chối lớp | `LeaderClassApprovalService:101–107` | lecturer, `CLASS_APPROVED|CLASS_REJECTED` |
| Publish lesson | `LessonsPublishService:127–141` | fan-out ACTIVE students, `LESSON_PUBLISHED` |
| Publish assignment | `LecturerAssignmentService:258–274` | fan-out ACTIVE students, `ASSIGNMENT_PUBLISHED` |
| Grade assignment | `LecturerAssignmentService:226–242` | submitting student, `ASSIGNMENT_GRADED` |

`NotificationService.create`, dòng 62–84, insert `Notification` với `isRead=false`, `isEmailSent=false`, timestamps và optional reference. Một số producer catch notification failure để không rollback domain transition; vì vậy notification là side effect best-effort ở các call sites đó. Riêng khi notification service đã vào transaction và type mail-whitelisted, notification + outbox row atomic với nhau.

### 7. Header badge, dropdown và inbox

`NotificationHeaderAdvice`, dòng 37–41, chạy trên SSR request, thêm `notifUnreadCount` từ repository count. Header bell/panel ở `app-header.html:163–182`.

`static/js/notifications.js` thực hiện:

| Interaction | HTTP | Controller |
|---|---|---|
| Poll mỗi 60 giây | `GET /my/notifications/unread-count` | `NotificationController:109–115` |
| Mở bell | `GET /my/notifications/recent` | controller 121–134, tối đa 8 items |
| Xem tất cả | `GET /my/notifications?page=n` | controller 62–70 |

Inbox template `notifications/index.html:27–52` render newest-first, unread dot và mỗi row là POST form.

### 8. User click notification → mark read → redirect

Inbox row gửi:

```text
POST /my/notifications/{notifId}/open
```

Dropdown dùng AJAX (`notifications.js:153–171`):

```text
POST /my/notifications/{notifId}/open?ajax=1
```

`NotificationController.open`, dòng 79–107:

1. `findOwned` lấy reference trước.
2. `NotificationService.markRead`, dòng 125–133, chỉ update khi `(id,userId)` khớp; absent/foreign là silent no-op, không leak.
3. Recount unread.
4. AJAX trả `{ok,redirect,count}`; form thường redirect trực tiếp.

Redirect mapping thật tại controller dòng 161–179:

| Reference | Target |
|---|---|
| `CLASS + JOIN_REQUEST` | `/lecturer/classes/{id}/members` |
| `CLASS + loại khác` | `/my/classes/{id}/lessons` |
| `LESSON` | `/my/lessons/{id}` |
| không/unknown | `/my/notifications` |

`REF_ASSIGNMENT` được entity/constants hỗ trợ nhưng `resolveRedirect` hiện **không có case ASSIGNMENT**. Do đó notification assignment được mark read nhưng quay về inbox, không mở assignment. Đây là hành vi source hiện tại.

## C. Notification email và mail runtime

### 9. Type nào thực sự gửi email

`NotificationType.EMAIL_TYPES`, dòng 48–52, chỉ gồm:

```text
LESSON_PUBLISHED
ASSIGNMENT_PUBLISHED
```

Join/class approval/assignment graded là in-app only. Khi create type whitelisted, `NotificationService:69–80` tìm email recipient rồi gọi:

```text
MailOutboxService.enqueueNotification(notificationId, recipient,
  "[KSH] " + title, content)
```

`MailOutboxService`, dòng 32–55, có transaction propagation `MANDATORY`, dedupe theo notification id và insert `MailOutboxJob` state `PENDING`, available now. Notification và outbox commit/rollback cùng nhau; request publish lesson/assignment không chờ SMTP.

### 10. Worker claim → SMTP → success/retry/fail

`MailOutboxWorker`, dòng 19–75, mặc định enabled, initial delay 30 giây, fixed delay 10 giây, batch 10 (clamp 1–100). Mỗi tick gọi `MailOutboxProcessor.processDue`:

1. `MailOutboxTransactionService.findClaimableIds`, dòng 53–61, lấy `PENDING|RETRY` due hoặc expired lease.
2. `claim`, dòng 63–84, lock job, nếu còn attempts thì transition `→ PROCESSING`, tăng `attemptCount`, set worker UUID và lease 2 phút.
3. Transaction kết thúc; `MailOutboxProcessor.deliver`, dòng 55–80, gọi SMTP **ngoài DB transaction**.
4. Thành công `recordSuccess`, dòng 86–104: state `SENT`, set `sentAt`, clear lease, đồng thời set originating `Notification.isEmailSent=true`.
5. Thất bại `recordFailure`, dòng 106–123: `RETRY` với exponential backoff 1 phút, 2, 4… capped 1 giờ; hết attempts thành `FAILED`.

`MailOutboxJob.DEFAULT_MAX_ATTEMPTS=8` (`MailOutboxJob:29`); entity transitions tại dòng 113–179. Delivery có semantics **at least once**: lease ngăn hai worker chủ động cùng gửi, nhưng crash sau SMTP accept trước recordSuccess có thể gửi lại.

### 11. SMTP sender đọc config runtime ở mỗi lần gửi

`MailService.send`, dòng 35–37, delegate `DbConfiguredMailSender.sendWithDetail`. `DbConfiguredMailSender`, dòng 77–150:

1. Load cached settings group `SMTP` ở send-time; admin save evict cache nên không cần restart.
2. `smtp.host` trống: return failure ngay, không network.
3. Build `JavaMailSenderImpl` với host, port mặc định 587, username/password; timeout connect/read/write 10 giây.
4. `smtp.encryption=tls` bật STARTTLS; `ssl` bật SSL; khác là none.
5. Build UTF-8 text MIME, From=`smtp.from_email` hoặc username fallback, optional from name/reply-to.
6. SMTP accept → success; Mail/Messaging exception → failure reason (secret/message body không log).

Khi SMTP config đầy đủ, các mail outbox job mới/đến hạn bắt đầu delivery. Khi chưa cấu hình hoặc sai config, notification in-app vẫn tồn tại; outbox retry rồi FAILED, không rollback nội dung đã publish.

### 12. Password reset là direct mail, không qua outbox

`PasswordRecoveryService.requestReset`, dòng 76–108:

1. Throttle email+IP và neutral-return nếu account không tồn tại.
2. Invalidate token cũ, generate raw token, chỉ lưu digest và expiry.
3. Tạo link `/reset-password?token=<raw>`.
4. Gọi `MailService.send` trực tiếp ở dòng 102–103 trong request.

SMTP failure chỉ warn generic, không log recipient/token/link. Token row vẫn đã được save; flow UI vẫn neutral để chống email enumeration. Đây không dùng durable outbox, nên không có automatic outbox retry.

Admin “send test” cũng gọi direct `sendWithDetail` qua `EmailSettingsService.sendTest:137–153`; nó trả lỗi chi tiết cho toast, không enqueue.

### 13. Retention/operations

`MailOutboxRetentionWorker` mặc định chạy sau 30 giây, mỗi 5 phút; xóa theo batch rows terminal `SENT` quá 30 ngày và `FAILED` quá 90 ngày (`:44–83`). `MailOutboxOperationsService` cung cấp snapshot không PII: count theo status, claimable, expired leases, oldest age (`:46–163`). Retention không xóa `PENDING/RETRY/PROCESSING`.

## Security và failure semantics

- Messaging/notification routes đều authenticated và CSRF-protected cho POST.
- Foreign conversation trả 404; foreign notification open là silent no-op.
- STOMP route authenticated; server push target là authenticated principal email.
- Message body được render bằng text (`textContent` ở `messaging.js:50–58`), không chèn HTML.
- Notification email chỉ hai whitelisted types; badge/read state độc lập email delivery.
- Outbox không giữ DB lock khi gọi SMTP, có lease/retry/terminal state.


## 14. Method-level handler trace (coverage gate)

- **MessagingController.unreadCount** — GET /my/messages/unread-count, authenticated, returns raw JSON {count: MessagingService.unreadCount(principal.id)} (MessagingController:154-159). Service is one read-only repository count of unread peer-sent messages (:289-292): no mark-read, conversation load, cache or write. It is an available fallback endpoint; app-wide messaging.js normally updates badges from SSR/STOMP rather than polling this URL.
- **NotificationController.index** — GET /my/notifications?page=0, authenticated. It reads one newest-first paged NotificationRow list (page clamps nonnegative in NotificationService.listForUser:95-100) and a separate authoritative unread count, adds both to SSR model and returns notifications/index (NotificationController:61-70). It does not mark any notification read; the page template row is a separate POST open action.
- **NotificationController.unreadCount** — GET /my/notifications/unread-count, authenticated, responds HTTP 200 raw JSON {count}; exactly one NotificationService.unreadCount repository count, no mutation (NotificationController:109-115; NotificationService:104-113). notifications.js polls only when notification header link exists, once per 60 seconds, preserves last SSR badge on failure, and caps display at 99+ (:49-85).
- **NotificationController.recent** — GET /my/notifications/recent, authenticated, reads page 0 newest-first then takes at most 8 items and independently recounts unread. It returns raw JSON {items:[id,title,content,isRead,createdAt HH:mm dd/MM/yyyy,href],count}; href is controller redirect resolution, not a signed URL (NotificationController:117-145). Header bell fetches it on open and renders escaped strings; no read/write occurs until the separate POST /{id}/open?ajax=1 (notifications.js:109-171).
