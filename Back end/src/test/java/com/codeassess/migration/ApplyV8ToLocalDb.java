package com.codeassess.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class ApplyV8ToLocalDb {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/plagiarism_system?useSSL=false&allowPublicKeyRetrieval=true";
        String user = "root";
        String pass = "123456";

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {

            ResultSet rsAll = conn.getMetaData().getColumns("plagiarism_system", null, "similarity_matrix", null);
            while (rsAll.next()) {
                System.out.println("COL: " + rsAll.getString("COLUMN_NAME") + 
                                   " | TYPE: " + rsAll.getString("TYPE_NAME") + 
                                   " | SIZE: " + rsAll.getInt("COLUMN_SIZE") + 
                                   " | NULLABLE: " + rsAll.getString("IS_NULLABLE") + 
                                   " | DEFAULT: " + rsAll.getString("COLUMN_DEF"));
            }
            ResultSet rsFk = conn.getMetaData().getImportedKeys("plagiarism_system", null, "similarity_matrix");
            while (rsFk.next()) {
                System.out.println("FK: " + rsFk.getString("FK_NAME") + 
                                   " (" + rsFk.getString("FKCOLUMN_NAME") + 
                                   ") -> " + rsFk.getString("PKTABLE_NAME") + 
                                   "(" + rsFk.getString("PKCOLUMN_NAME") + ")");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
