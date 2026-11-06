package com.codeassess.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Manual execution utility for local Docker MySQL migrations")
public class RunLocalFlywayMigration {

    @Test
    void executeMigrationAgainstLocalDatabase() {
        String url = "jdbc:mysql://localhost:3307/codeassess?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true";
        String user = "root";
        String pass = "123456";

        System.out.println("Starting Flyway migration on local Docker MySQL: " + url);

        Flyway flyway = Flyway.configure()
                .dataSource(url, user, pass)
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();

        System.out.println("Flyway migration completed!");
        System.out.println("Success: " + result.success);
        System.out.println("Migrations executed: " + result.migrationsExecuted);
        System.out.println("Target schema version: " + result.targetSchemaVersion);

        assertTrue(result.success, "Migration must be successful");
    }
}

