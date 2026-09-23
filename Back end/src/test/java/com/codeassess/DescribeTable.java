package com.codeassess;

import java.sql.*;

public class DescribeTable {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/plagiarism_system?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true";
        String user = "root";
        String pass = "123456";
        
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            System.out.println("====== DESCRIBE scoring_criteria ======");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("DESCRIBE scoring_criteria")) {
                while (rs.next()) {
                    System.out.println(rs.getString("Field") + " | " + rs.getString("Type") + " | " + rs.getString("Null"));
                }
            }
            
            System.out.println("====== DESCRIBE submissions ======");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("DESCRIBE submissions")) {
                while (rs.next()) {
                    System.out.println(rs.getString("Field") + " | " + rs.getString("Type") + " | " + rs.getString("Null"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
