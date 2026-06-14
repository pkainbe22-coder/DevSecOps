package com.portal.auth;

import com.portal.dao.UserDao;
import com.portal.util.Passwords;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * On startup, idempotently seeds one user per role with BCrypt-hashed passwords
 * (M2). Re-running is safe — existing usernames are left untouched.
 *
 * Dev defaults (override with env vars; CHANGE IN PRODUCTION):
 *   dev / dev123  (DEVELOPER)
 *   sec / sec123  (SECURITY)
 *   ops / ops123  (OPERATIONS)
 */
@WebListener
public class SeedListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            UserDao dao = new UserDao();
            seed(dao, "SEED_DEV_USER", "dev", "SEED_DEV_PASS", "dev123", "DEVELOPER");
            seed(dao, "SEED_SEC_USER", "sec", "SEED_SEC_PASS", "sec123", "SECURITY");
            seed(dao, "SEED_OPS_USER", "ops", "SEED_OPS_PASS", "ops123", "OPERATIONS");
        } catch (Exception e) {
            // Don't crash the app if the DB isn't up yet; log and continue.
            sce.getServletContext().log("Seed skipped: " + e.getMessage());
        }
    }

    private void seed(UserDao dao, String userEnv, String userDef,
                      String passEnv, String passDef, String role) {
        String username = com.portal.util.Env.get(userEnv, userDef);
        String password = com.portal.util.Env.get(passEnv, passDef);
        boolean inserted = dao.insertIfAbsent(username, Passwords.hash(password), role);
        if (inserted) {
            // Note: only the username/role are logged — never the password.
            System.out.println("[seed] created " + role + " user: " + username);
        }
    }
}
