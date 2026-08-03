-- Class discovery now uses class name + subject code. The former random
-- five-character identifier no longer has an invite, lookup, or display role.
ALTER TABLE classes DROP INDEX uk_classes_code;
ALTER TABLE classes DROP COLUMN code;
