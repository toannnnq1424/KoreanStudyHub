# Authentication Domain Specification

## Purpose
Manage all identity and access lifecycle concerns: guest access, account registration, authentication, session management, and credential recovery. This domain ensures only valid, active users can access protected resources, enforces security policies (lockout, password strength), and provides a complete audit trail of login and logout activity.

## Requirements

### Requirement: View Landing Page
The system SHALL display the landing page to unauthenticated guest users so they can understand the platform's purpose and main functions.

#### Scenario: Display landing page successfully
- **GIVEN** the guest user is not authenticated
- **WHEN** the guest user accesses the landing page URL
- **THEN** the system SHALL display the landing page successfully

#### Scenario: System temporarily unavailable
- **GIVEN** the system is in maintenance mode
- **WHEN** the guest user accesses the landing page URL
- **THEN** the system SHALL display a maintenance notification (MSG-01)

---

### Requirement: Register Account
The system SHALL allow guest users to register an account using their email address, default to "Student" role, and set their status to active.

#### Scenario: Successful registration
- **GIVEN** the user is not logged in and the email is not registered
- **WHEN** the user enters required information and submits registration
- **THEN** the system SHALL create a new active account with the "Student" role and display a successful registration message (MSG-02)

#### Scenario: Required information missing
- **WHEN** the user submits the registration form with missing mandatory details
- **THEN** the system SHALL display validation messages (MSG-03) and prompt the user to update the information

#### Scenario: Email already exists
- **GIVEN** the email address is already registered in the system
- **WHEN** the user submits registration with that email
- **THEN** the system SHALL display an error message (MSG-04)

---

### Requirement: Login
The system SHALL authenticate registered users using their credentials, establish a session, and redirect them to their respective dashboard.

#### Scenario: Successful login
- **GIVEN** the user account has been registered and is active
- **WHEN** the user enters their email and password and submits
- **THEN** the system SHALL establish a user session, record the login activity, and redirect the user to their corresponding dashboard

#### Scenario: Attempt to access protected resource directly
- **GIVEN** the user is unauthenticated
- **WHEN** the user attempts to access a protected URL
- **THEN** the system SHALL redirect the user to the login screen

#### Scenario: Required fields are blank
- **WHEN** the user submits the login form with empty email or password
- **THEN** the system SHALL display validation messages (MSG-05) and remain on the login screen

#### Scenario: Invalid credentials
- **WHEN** the user submits incorrect email or password
- **THEN** the system SHALL increment the failed login counter and display an authentication error (MSG-06)

#### Scenario: Account inactive
- **GIVEN** the user account status is inactive
- **WHEN** the user attempts to log in
- **THEN** the system SHALL deny authentication and display an account status message (MSG-07)

#### Scenario: Account temporarily locked
- **GIVEN** the user has five consecutive failed login attempts
- **WHEN** the user attempts to log in
- **THEN** the system SHALL temporarily lock the account for 30 minutes and display a warning message (MSG-08)

---

### Requirement: Forgot Password
The system SHALL allow registered users to request a password reset by providing their registered email address.

#### Scenario: Successful forgot password request
- **GIVEN** the user has a registered email address
- **WHEN** the user enters their email address and submits the request
- **THEN** the system SHALL generate a password reset token, send reset instructions through email, and display a confirmation message (MSG-09)

#### Scenario: Email not found
- **WHEN** the user submits an email address that is not found in the system
- **THEN** the system SHALL display an error message (MSG-10)

#### Scenario: Email delivery failure
- **WHEN** the email delivery fails due to mail service issues
- **THEN** the system SHALL display a delivery failure notification (MSG-11)

---

### Requirement: Reset Password
The system SHALL allow registered users to reset their password using a valid reset token.

#### Scenario: Successful password reset
- **GIVEN** the user possesses a valid reset token
- **WHEN** the user enters and confirms a new password and submits the request
- **THEN** the system SHALL validate password requirements, update the password, invalidate the reset token, and display a success message (MSG-12)

#### Scenario: Invalid token
- **WHEN** the user attempts to access the reset link with an invalid token
- **THEN** the system SHALL display an error message (MSG-13)

#### Scenario: Expired token
- **WHEN** the user attempts to access the reset link with an expired token
- **THEN** the system SHALL display a notification (MSG-14) and request the user to initiate the Forgot Password process again

---

### Requirement: Update Personal Profile
The system SHALL allow authenticated users to update their profile information.

#### Scenario: Successful profile update
- **GIVEN** the user is logged in
- **WHEN** the user updates profile details and submits the changes
- **THEN** the system SHALL validate the information, save the updated profile, and display a success message (MSG-15)

#### Scenario: Invalid profile information
- **WHEN** the user submits invalid profile details
- **THEN** the system SHALL display validation messages (MSG-16) and prompt the user to correct the information

---

### Requirement: Change Password
The system SHALL allow authenticated users to change their password by verifying their current password.

#### Scenario: Successful password change
- **GIVEN** the user is logged in and the account is active
- **WHEN** the user submits their correct current password along with a new confirmed password
- **THEN** the system SHALL update the password and display a success message (MSG-17)

#### Scenario: Incorrect current password
- **WHEN** the user submits an incorrect current password
- **THEN** the system SHALL display an error message (MSG-18)

#### Scenario: Password confirmation mismatch
- **WHEN** the user enters a new password and a confirmation that does not match
- **THEN** the system SHALL display a validation message (MSG-19)

---

### Requirement: Logout
The system SHALL securely invalidate an active user session upon logout.

#### Scenario: Successful logout
- **GIVEN** the user is logged in and an active session exists
- **WHEN** the user selects logout
- **THEN** the system SHALL invalidate the current session, record the logout activity, and redirect the user to the landing page with a confirmation message (MSG-20)

#### Scenario: Session already expired
- **GIVEN** the user session has already expired
- **WHEN** the user attempts to perform any action or clicks logout
- **THEN** the system SHALL redirect the user to the landing page

---

### Requirement: Google OAuth2 Login
The system SHALL support user authentication via Google OAuth2. If a Google authenticated email does not exist in the database, the system SHALL automatically create a new student account.

#### Scenario: Successful Google login for existing user
- **WHEN** a user initiates Google OAuth2 login and successfully authenticates with Google
- **THEN** the system SHALL log the user in and redirect them to their respective dashboard

#### Scenario: Successful Google login for new user
- **WHEN** a user does not have an account in the system and initiates Google OAuth2 login and successfully authenticates with Google
- **THEN** the system SHALL create a new active account with the STUDENT role, set provider to GOOGLE, log the user in, and redirect them to the student dashboard

---

### Requirement: Admin Reset User Password
The system SHALL allow an Administrator to reset any user's password. The system SHALL generate a secure random 10-character password, hash it, update the user's account, and send the plain text password to the user's email address.

#### Scenario: Successful admin password reset
- **WHEN** the Administrator requests to reset a user's password
- **THEN** the system SHALL generate a secure random 10-character password, update the user's password in the database, clear any temporary lock, send the new password via email to the user, and display a success message
