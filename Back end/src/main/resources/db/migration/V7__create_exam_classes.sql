CREATE TABLE exam_classes (
    exam_id VARCHAR(36) NOT NULL,
    class_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (exam_id, class_id),
    CONSTRAINT fk_exam_classes_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_classes_class FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE RESTRICT,
    INDEX idx_exam_classes_exam (exam_id),
    INDEX idx_exam_classes_class (class_id)
);

-- Backfill existing relationships from exams.class_id
INSERT INTO exam_classes (exam_id, class_id)
SELECT id, class_id FROM exams 
WHERE class_id IS NOT NULL 
  AND class_id IN (SELECT id FROM classes);
