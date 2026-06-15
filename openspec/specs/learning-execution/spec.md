# Learning & Assessment Execution Specification

## Purpose
Define the student-facing learning and assessment experience: accessing materials, taking quizzes and exams, submitting assignments, and viewing grades. Also covers the Teacher's responsibility for grading subjective submissions and providing feedback.

## Requirements

### Requirement: Access Learning Materials
The system SHALL allow enrolled students to access released learning materials and update their learning progress.

#### Scenario: Successful access to materials
- **GIVEN** the student is authenticated and enrolled, and materials are released
- **WHEN** the student selects a learning resource to study
- **THEN** the system SHALL display the content, record progress, and display a confirmation message (MSG-93)

#### Scenario: Resource not yet released
- **GIVEN** the release date has not been reached
- **WHEN** the student attempts to access the resource
- **THEN** the system SHALL deny access and display an informational message (MSG-94)

#### Scenario: Student not enrolled
- **GIVEN** the student is not enrolled in the class
- **WHEN** the student attempts to access materials
- **THEN** the system SHALL deny access and display an error message (MSG-95)

---

### Requirement: Take Quiz
The system SHALL allow students to complete assigned quizzes, receive automated grading, and get immediate feedback.

#### Scenario: Successful quiz submission
- **GIVEN** a quiz has been assigned and is available
- **WHEN** the student answers and submits the quiz within the time limit
- **THEN** the system SHALL validate the attempt, automatically grade objective questions, calculate scores, and display results (MSG-96)

#### Scenario: Time limit exceeded
- **WHEN** the quiz duration expires before the student submits
- **THEN** the system SHALL automatically submit the quiz with current answers and calculate the score

#### Scenario: Quiz unavailable
- **WHEN** the student attempts to submit a quiz after its availability period has ended
- **THEN** the system SHALL reject the submission and display an error message (MSG-97)

---

### Requirement: Submit Assignment
The system SHALL allow students to submit files and text for assigned homework.

#### Scenario: Successful assignment submission
- **GIVEN** an assignment is assigned and submission is active
- **WHEN** the student uploads files or enters content and submits
- **THEN** the system SHALL validate, store the submission, record the timestamp, and display a success message (MSG-99)

#### Scenario: Unsupported file format
- **WHEN** the student uploads a file with an unsupported format
- **THEN** the system SHALL reject the upload and display an error message (MSG-100)

#### Scenario: Resubmit assignment
- **GIVEN** a previous submission exists and resubmission is allowed
- **WHEN** the student updates the submission and submits
- **THEN** the system SHALL replace the previous submission and display a success message (MSG-102)

---

### Requirement: Take Exam
The system SHALL allow students to complete examinations, automatically grade objective items, and save subjective items for manual grading.

#### Scenario: Successful exam submission
- **GIVEN** the exam is assigned and available
- **WHEN** the student answers and submits the exam
- **THEN** the system SHALL validate, auto-grade objective questions, store subjective answers for teacher grading, and display a confirmation (MSG-103)

#### Scenario: Temporary connectivity interruption
- **WHEN** the network connection is lost during the exam
- **THEN** the system SHALL temporarily save the student responses locally and display a warning message (MSG-105)

---

### Requirement: Evaluate Student Submissions
The system SHALL allow Teachers to grade subjective submissions and examinations, providing scores and constructive feedback.

#### Scenario: Successful evaluation
- **GIVEN** student submissions exist and the Teacher has grading permissions
- **WHEN** the Teacher enters scores for subjective responses, writes feedback, and submits
- **THEN** the system SHALL validate the scores, store results, update status, and display a success message (MSG-106)

#### Scenario: Invalid score
- **WHEN** the Teacher enters a score exceeding the maximum allowed value
- **THEN** the system SHALL reject the evaluation and display an error message (MSG-107)

---

### Requirement: View Assessment Results
The system SHALL allow students to view their quiz, assignment, and exam scores along with feedback.

#### Scenario: View results successfully
- **GIVEN** the assessment is finalized or released by the teacher
- **WHEN** the student accesses their grades dashboard
- **THEN** the system SHALL display scores, grades, and teacher feedback comments
