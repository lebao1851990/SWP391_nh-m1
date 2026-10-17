package com.codeassess.testconfig;

import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import javax.sql.DataSource;

/**
 * Spring TestExecutionListener that automatically inspects every ApplicationContext
 * containing a DataSource before any test method executes, guaranteeing that tests
 * cannot run against the development/production database.
 */
public class DatabaseSafetyTestExecutionListener extends AbstractTestExecutionListener {

    @Override
    public int getOrder() {
        // Run very early in the test execution lifecycle
        return HIGHEST_PRECEDENCE + 50;
    }

    @Override
    public void prepareTestInstance(TestContext testContext) throws Exception {
        checkDataSourceSafety(testContext);
    }

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        checkDataSourceSafety(testContext);
    }

    private void checkDataSourceSafety(TestContext testContext) {
        ApplicationContext applicationContext = testContext.getApplicationContext();
        if (applicationContext != null && applicationContext.containsBean("dataSource")) {
            DataSource dataSource = applicationContext.getBean(DataSource.class);
            DatabaseSafetyGuard.assertSafeTestDatabase(dataSource);
        }
    }
}
