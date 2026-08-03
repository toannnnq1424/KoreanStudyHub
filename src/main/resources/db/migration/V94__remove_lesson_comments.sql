-- Student lesson discussions are outside the compact learning flow.
-- Drop dependent audit tables first, then the comment aggregate itself.
DROP TABLE activity_comments;
DROP TABLE comment_moderation;
DROP TABLE comments;

DELETE rp FROM role_permissions rp
JOIN permissions p ON p.id = rp.permission_id
WHERE p.feature_key LIKE 'comment.%'
   OR p.feature_key LIKE 'moderation.comment_%'
   OR p.feature_key = 'moderation.view_hidden';

DELETE FROM permissions
WHERE feature_key LIKE 'comment.%'
   OR feature_key LIKE 'moderation.comment_%'
   OR feature_key = 'moderation.view_hidden';
