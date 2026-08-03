-- Classes, tests and flashcard decks no longer use the legacy Course catalog.
-- Remove nullable legacy references before dropping the unused catalog tables.
ALTER TABLE flashcard_decks DROP FOREIGN KEY fk_fd_course;
ALTER TABLE flashcard_decks DROP COLUMN course_id;

ALTER TABLE tests DROP FOREIGN KEY fk_test_course;
ALTER TABLE tests DROP COLUMN course_id;

DROP TABLE activity_courses;
DROP TABLE course_categories;
DROP TABLE courses;
DROP TABLE categories;
