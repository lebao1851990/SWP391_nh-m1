package com.codeassess;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.DatabaseMetaData;
import java.util.Arrays;

public class DbPreflight {
    public static void main(String[] args) {
        String[] passwords = {"", "root", "password", "123456", "admin"};
        String url = "jdbc:mysql://localhost:3306/plagiarism_system?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        String user = "root";
        
        Connection conn = null;
        for (String pass : passwords) {
            try {
                conn = DriverManager.getConnection(url, user, pass);
                System.out.println("SUCCESS_PASSWORD=" + (pass.isEmpty() ? "EMPTY" : pass));
                break;
            } catch (Exception e) {
                // Ignore
            }
        }
        
        if (conn == null) {
            System.err.println("FAILED TO CONNECT TO DB");
            return;
        }
        
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"});
            System.out.println("--- TABLES ---");
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                System.out.println("TABLE: " + tableName);
                
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                    if (rs.next()) {
                        System.out.println("  COUNT: " + rs.getInt(1));
                    }
                } catch (Exception e) {
                    System.out.println("  COUNT: ERROR - " + e.getMessage());
                }
                
                System.out.println("  COLUMNS:");
                try {
                    ResultSet columns = metaData.getColumns(null, null, tableName, "%");
                    while (columns.next()) {
                        String columnName = columns.getString("COLUMN_NAME");
                        String typeName = columns.getString("TYPE_NAME");
                        int columnSize = columns.getInt("COLUMN_SIZE");
                        String isNullable = columns.getString("IS_NULLABLE");
                        System.out.println("    - " + columnName + " " + typeName + "(" + columnSize + ") " + (isNullable.equals("YES") ? "NULL" : "NOT NULL"));
                    }
                } catch (Exception e) {
                    System.out.println("  COLUMNS: ERROR - " + e.getMessage());
                }
            }
            
            System.out.println("--- USERS SAMPLE ---");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users LIMIT 10")) {
                while (rs.next()) {
                    System.out.println("User: id=" + rs.getString("id") + 
                        ", role=" + rs.getString("role") + 
                        ", full_name=" + rs.getString("full_name"));
                }
            } catch (Exception e) {
                System.out.println("users table missing or schema mismatch");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try { conn.close(); } catch (Exception e) {}
        }
    }
}
