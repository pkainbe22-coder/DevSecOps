-- Runs first on MySQL first boot. Creates both databases + the portal app user.
CREATE DATABASE IF NOT EXISTS portal CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS gitea CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- App user for the portal (password should match PORTAL_DB_PASSWORD in .env).
CREATE USER IF NOT EXISTS 'portal'@'%' IDENTIFIED BY 'portalpass';
GRANT ALL PRIVILEGES ON portal.* TO 'portal'@'%';
FLUSH PRIVILEGES;

USE portal;
