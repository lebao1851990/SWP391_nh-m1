package com.codeassess;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DbUsers {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/plagiarism_system?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        String user = "root";
        String pass = "123456";
        
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT email, role, password FROM users LIMIT 10")) {
                while (rs.next()) {
                    System.out.println(rs.getString("email") + " | " + rs.getString("role") + " | " + rs.getString("password"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
