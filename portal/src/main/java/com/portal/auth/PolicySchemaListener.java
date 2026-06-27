package com.portal.auth;

import com.portal.util.Db;
import com.portal.util.Env;
import com.portal.util.PolicySchema;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.sql.Connection;

/**
 * Applies the Policy-as-Code schema migration on startup for normal (MySQL) deployments.
 * In DEMO_MODE the embedded-H2 DemoBootstrap owns schema creation and calls PolicySchema
 * itself, so this listener stands down to keep that path deterministic.
 */
@WebListener
public class PolicySchemaListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        if ("true".equalsIgnoreCase(Env.get("DEMO_MODE", "false"))) return;
        try (Connection c = Db.getConnection()) {
            PolicySchema.apply(c);
            System.out.println("[policy] schema migration applied");
        } catch (Exception e) {
            // Don't crash the app if the DB isn't up yet; log and continue.
            sce.getServletContext().log("[policy] schema migration skipped: " + e.getMessage());
        }
    }
}
