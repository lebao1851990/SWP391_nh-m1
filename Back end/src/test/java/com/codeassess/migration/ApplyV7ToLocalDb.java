package com.codeassess.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class ApplyV7ToLocalDb {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/plagiarism_system?useSSL=false&allowPublicKeyRetrieval=true";
        String user = "root";
        String pass = "123456";

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {

            // Check if table exam_classes already exists
            ResultSet rs = conn.getMetaData().getTables("plagiarism_system", null, "exam_classes", null);
            if (!rs.next()) {
                System.out.println("Creating exam_classes table in plagiarism_system...");
                stmt.execute("""
                    CREATE TABLE exam_classes (
                        exam_id VARCHAR(36) NOT NULL,
                        class_id VARCHAR(36) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (exam_id, class_id),
                        CONSTRAINT fk_exam_classes_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
                        CONSTRAINT fk_exam_classes_class FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE RESTRICT,
                        INDEX idx_exam_classes_exam (exam_id),
                        INDEX idx_exam_classes_class (class_id)
                    )
                """);
                System.out.println("Table exam_classes created successfully.");

                System.out.println("Backfilling exam_classes from exams.class_id...");
                int rows = stmt.executeUpdate("""
                    INSERT IGNORE INTO exam_classes (exam_id, class_id)
                    SELECT id, class_id FROM exams 
                    WHERE class_id IS NOT NULL 
                      AND class_id IN (SELECT id FROM classes)
                """);
                System.out.println("Backfilled " + rows + " rows into exam_classes.");
            } else {
                System.out.println("Table exam_classes already exists.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
