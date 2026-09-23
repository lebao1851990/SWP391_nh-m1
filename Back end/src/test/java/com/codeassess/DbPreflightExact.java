package com.codeassess;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;

public class DbPreflightExact {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/plagiarism_system?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        String user = "root";
        String pass = "123456";
        String catalog = "plagiarism_system";
        
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            DatabaseMetaData metaData = conn.getMetaData();
            String[] targetTables = {"users", "exams", "submissions", "ast_fingerprints", "similarity_matrix", "plagiarism_appeals"};
            
            for (String tableName : targetTables) {
                System.out.println("====== TABLE: " + tableName + " ======");
                
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                    if (rs.next()) {
                        System.out.println("COUNT: " + rs.getInt(1));
                    }
                } catch (Exception e) {
                    System.out.println("COUNT: Table doesn't exist");
                }
                
                System.out.println("COLUMNS:");
                try (ResultSet columns = metaData.getColumns(catalog, null, tableName, "%")) {
                    while (columns.next()) {
                        System.out.println("  - " + columns.getString("COLUMN_NAME") + " " 
                            + columns.getString("TYPE_NAME") + "(" + columns.getInt("COLUMN_SIZE") + ") " 
                            + (columns.getString("IS_NULLABLE").equals("YES") ? "NULL" : "NOT NULL"));
                    }
                }
                
                System.out.println("PRIMARY KEYS:");
                try (ResultSet pks = metaData.getPrimaryKeys(catalog, null, tableName)) {
                    while (pks.next()) {
                        System.out.println("  - " + pks.getString("COLUMN_NAME") + " (" + pks.getString("PK_NAME") + ")");
                    }
                }
                
                System.out.println("FOREIGN KEYS:");
                try (ResultSet fks = metaData.getImportedKeys(catalog, null, tableName)) {
                    while (fks.next()) {
                        System.out.println("  - " + fks.getString("FKCOLUMN_NAME") + " -> " 
                            + fks.getString("PKTABLE_NAME") + "(" + fks.getString("PKCOLUMN_NAME") + ")");
                    }
                }
                
                System.out.println("INDEXES:");
                try (ResultSet idxs = metaData.getIndexInfo(catalog, null, tableName, false, false)) {
                    while (idxs.next()) {
                        String idxName = idxs.getString("INDEX_NAME");
                        String colName = idxs.getString("COLUMN_NAME");
                        boolean nonUnique = idxs.getBoolean("NON_UNIQUE");
                        if (idxName != null && colName != null) {
                            System.out.println("  - " + idxName + " (" + colName + ") Unique: " + !nonUnique);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
