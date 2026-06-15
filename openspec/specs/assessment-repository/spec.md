# Assessment Repository Specification

## Purpose
Allow Teachers and Subject Leaders to maintain a reusable bank of questions and assessment templates (quizzes, assignments, exams), enabling standardized, consistent evaluations to be distributed efficiently across multiple classes.

## Requirements

### Requirement: Maintain Question Bank
The system SHALL allow Teachers and Subject Leaders to create, update, delete, and import reusable questions.

#### Scenario: Successful question creation or update
- **GIVEN** the user is authenticated and authorized
- **WHEN** the user enters question details, selects type, topic, difficulty, and saves
- **THEN** the system SHALL validate the question, store it in the bank, and display a success message (MSG-49)

#### Scenario: Deleting a question
- **WHEN** the user requests question deletion
- **THEN** the system SHALL verify deletion eligibility, archive the question, and display a success message (MSG-50)

#### Scenario: Successful question import
- **WHEN** the user uploads a question import file in a valid format
- **THEN** the system SHALL validate the format, import the questions, and display a success message (MSG-53)

#### Scenario: Unsupported import format
- **WHEN** the user uploads a file with an invalid or unsupported import format
- **THEN** the system SHALL reject the upload and display an error message (MSG-54)

---

### Requirement: Maintain Quiz Templates
The system SHALL allow Teachers and Subject Leaders to create, update, and delete reusable quiz templates.

#### Scenario: Successful quiz template creation or update
- **WHEN** the user enters quiz metadata, selects questions from the bank, configures settings, and saves
- **THEN** the system SHALL validate, store the quiz template, and display a success message (MSG-55)

#### Scenario: No questions selected
- **WHEN** the user attempts to save a quiz template with zero questions selected
- **THEN** the system SHALL display an error message (MSG-57)

---

### Requirement: Maintain Assignment Templates
The system SHALL allow Teachers and Subject Leaders to create, update, and delete reusable assignment templates.

#### Scenario: Successful assignment template creation or update
- **WHEN** the user enters assignment info, defines submission instructions, optionally selects questions, and saves
- **THEN** the system SHALL validate, store the template, and display a success message (MSG-58)

#### Scenario: Missing submission instructions
- **WHEN** the user attempts to save an assignment template without submission instructions
- **THEN** the system SHALL display an error message (MSG-60)

---

### Requirement: Maintain Exam Templates
The system SHALL allow Teachers and Subject Leaders to create, update, and delete reusable exam templates.

#### Scenario: Successful exam template creation or update
- **WHEN** the user enters exam metadata, selects questions, configures rules and scoring settings, and saves
- **THEN** the system SHALL validate, store the exam template, and display a success message (MSG-61)

#### Scenario: Missing exam rules or scoring settings
- **WHEN** the user attempts to save an exam template without configuring rules or scoring settings
- **THEN** the system SHALL display an error message (MSG-63)
