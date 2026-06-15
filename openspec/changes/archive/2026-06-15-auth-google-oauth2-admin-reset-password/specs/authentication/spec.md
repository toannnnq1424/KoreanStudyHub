## ADDED Requirements

### Requirement: Google OAuth2 Login
The system SHALL support user authentication via Google OAuth2. If a Google authenticated email does not exist in the database, the system SHALL automatically create a new student account.

#### Scenario: Successful Google login for existing user
- **WHEN** a user initiates Google OAuth2 login and successfully authenticates with Google
- **THEN** the system SHALL log the user in and redirect them to their respective dashboard

#### Scenario: Successful Google login for new user
- **WHEN** a user does not have an account in the system and initiates Google OAuth2 login and successfully authenticates with Google
- **THEN** the system SHALL create a new active account with the STUDENT role, set provider to GOOGLE, log the user in, and redirect them to the student dashboard

### Requirement: Admin Reset User Password
The system SHALL allow an Administrator to reset any user's password. The system SHALL generate a secure random 10-character password, hash it, update the user's account, and send the plain text password to the user's email address.

#### Scenario: Successful admin password reset
- **WHEN** the Administrator requests to reset a user's password
- **THEN** the system SHALL generate a secure random 10-character password, update the user's password in the database, clear any temporary lock, send the new password via email to the user, and display a success message
