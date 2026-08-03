-- The only new product table approved for this compaction. The primary
-- lecturer remains classes.lecturer_id; this relation grants additional
-- teaching access without transferring ownership or changing created_by.
CREATE TABLE class_co_lecturers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    lecturer_id BIGINT NOT NULL,
    assigned_by BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_class_co_lecturer (class_id, lecturer_id),
    INDEX idx_co_lecturer_user_class (lecturer_id, class_id),
    CONSTRAINT fk_co_lecturer_class FOREIGN KEY (class_id)
        REFERENCES classes(id) ON DELETE CASCADE,
    CONSTRAINT fk_co_lecturer_user FOREIGN KEY (lecturer_id)
        REFERENCES users(id),
    CONSTRAINT fk_co_lecturer_assigner FOREIGN KEY (assigned_by)
        REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
