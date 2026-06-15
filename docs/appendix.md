# Appendix

## 1. Non-Functional Requirements
### 1.1 External Interfaces
#### User Interfaces
- Responsive Design:
  - The system shall provide a web-based user interface implemented using Thymeleaf and Bootstrap.
  - The user interface shall support responsive layouts to ensure usability across desktop, tablet, and mobile web browsers.
  - The system shall provide a consistent navigation structure and visual design across all functional modules.
  - User feedback messages, validation messages, and confirmation dialogs shall be displayed appropriately to guide users during task execution.

#### Software Interfaces
- The system shall interface with an SMTP email service to support password reset requests and class invitation notifications.
- The system shall interface with the local file system for storing uploaded learning resources, supporting materials, and multimedia files.
- The system shall utilize MySQL as the primary database management system for persistent data storage.
- The application shall use Spring Security to provide authentication and authorization services.

### 1.2 Quality Attributes
#### Security
- All user passwords shall be hashed using BCrypt before being stored in the database.
- The system shall implement authentication and authorization using Spring Security.
- The system shall enforce Role-Based Access Control (RBAC) to restrict access to resources and functions according to user roles.
- The following roles shall be supported: `ROLE_ADMIN`, `ROLE_SUBJECT_LEADER`, `ROLE_TEACHER`, `ROLE_STUDENT`.
- Users shall only be permitted to access functions and data authorized for their assigned roles.
- User sessions shall expire automatically after a period of inactivity to reduce unauthorized access risks.
- Password reset operations shall require identity verification through registered email addresses.
- Audit logs shall be maintained for critical administrative activities.

#### Performance
- Response Time (Latency): Standard UI pages rendered via Thymeleaf must achieve a load time of under 2 seconds under normal network conditions.
- Resource Optimization: Multimedia assets must be compressed to minimize bandwidth usage and latency.

#### Reliability & Availability
- Critical operations shall be executed within transactional boundaries to preserve data consistency.
- The system shall recover gracefully from unexpected failures without compromising persisted data integrity.
- The system shall validate all required inputs before processing requests.
- Backup mechanisms shall be available to support administrative data recovery activities.

#### Maintainability & Extensibility
- The application shall follow the Controller–Service–Repository architectural pattern.
- Business logic shall be isolated from presentation logic.
- Source code shall follow consistent naming conventions and coding standards.

#### Compatibility
- The system shall support modern web browsers, including Google Chrome, Microsoft Edge, Mozilla Firefox.
- The system shall function without requiring browser-specific plugins.

#### Internationalization
- The system shall support UTF-8 encoding for storing and displaying multilingual content (especially Korean characters).

#### Usability
- The system shall provide intuitive workflows appropriate for all user roles.
- Similar operations across modules shall maintain consistent interaction patterns.
- Validation errors and warning messages shall clearly describe problems.

---

## 2. Business Rules
*Format: BR-[Category Abbreviation][Number]*
- **BR-C**: Constraint Rules
- **BR-P**: Process Rules
- **BR-D**: Data Rules
- **BR-CMP**: Computation Rules

**Selected Examples from User Specifications:**
- **BR-01 (BR-C):** Input email must comply with standard internet email format validation (RFC 5322 regex).
- **BR-04 (BR-P):** System must automatically assign the default "Student" role to registered accounts.
- **BR-05 (BR-C):** Email address serves as unique identifier across user database.
- **BR-09 (BR-P):** Account temporarily suspended for 30 mins after 5 consecutive failed login attempts.
- **BR-20 (BR-C):** Only Teachers or Subject Leaders with appropriate permissions may modify lesson content.
- **BR-21 (BR-P):** Resources assigned to active classes cannot be permanently deleted (must be archived).
- **BR-26 (BR-C):** Listening resources must always contain a text transcript for accessibility.
- **BR-40 (BR-C):** Only resources in "Draft" or "Revision Required" status are permitted to be submitted for review.
- **BR-47 (BR-C):** The publication pipeline is exclusively restricted to learning resources that possess an "Approved" status.
- **BR-72 (BR-P):** Quiz draft must force-submit immediately when duration counter hits zero.
- **BR-102 (BR-C):** Administrative modules restricted to global Administrator clearance tokens.

*(Note: There are 113 rules provided by the user. They will be integrated directly into task implementations as required by the specifications).*

---

## 3. System Messages
*Format: MSG[Number]*

- **MSG01:** The system is currently under maintenance. Please try again later.
- **MSG02:** Registration completed successfully.
- **MSG03:** Please complete all required fields.
- **MSG04:** This email address is already registered.
- **MSG05:** Email and password are required.
- **MSG06:** Invalid email or password.
- **MSG07:** Your account is inactive. Please contact the administrator.
- **MSG08:** Your account has been temporarily locked for 30 minutes due to multiple failed login attempts.
- **MSG09:** Password reset instructions have been sent to your email.
- **MSG12:** Your password has been reset successfully.
- **MSG15:** Profile updated successfully.
- **MSG21:** Standard Lesson created successfully.
- **MSG30:** Lesson content saved successfully.
- **MSG65:** Resource submitted for review successfully.
- **MSG70:** Resource rejected successfully.
- **MSG72:** Resource published successfully.

*(Note: There are 74+ messages provided by the user. They will be utilized as constants during Thymeleaf view rendering and API responses).*
