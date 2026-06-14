-- DevSecOps Portal schema (M2). 5 tables mapping to roles + the approval gate.
-- Idempotent: safe to re-run.

USE portal;

CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,          -- BCrypt hash, never plain text
  role ENUM('DEVELOPER','SECURITY','OPERATIONS') NOT NULL
);

CREATE TABLE IF NOT EXISTS commits (
  id INT AUTO_INCREMENT PRIMARY KEY,
  commit_hash VARCHAR(60) UNIQUE NOT NULL,
  author VARCHAR(100),                          -- matches users.username for "my commits"
  message TEXT,
  branch VARCHAR(100),
  repo VARCHAR(150),
  gitea_url VARCHAR(500),                        -- deep link back to Gitea
  committed_at DATETIME
);

CREATE TABLE IF NOT EXISTS scan_results (
  id INT AUTO_INCREMENT PRIMARY KEY,
  commit_id INT,
  scan_type ENUM('SAST','SCA','DAST') NOT NULL,
  tool VARCHAR(50),                              -- SonarQube / DependencyCheck / ZAP
  critical INT DEFAULT 0,
  high INT DEFAULT 0,
  medium INT DEFAULT 0,
  low INT DEFAULT 0,
  report_url VARCHAR(500),                        -- link to full SonarQube/ZAP report
  scanned_at DATETIME,
  FOREIGN KEY (commit_id) REFERENCES commits(id),
  UNIQUE KEY uq_commit_scan (commit_id, scan_type)  -- idempotency: one row per (commit, type)
);

CREATE TABLE IF NOT EXISTS deployment_approvals (
  id INT AUTO_INCREMENT PRIMARY KEY,
  commit_id INT UNIQUE,
  decision ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'PENDING',
  security_user_id INT,
  comment TEXT,
  decided_at DATETIME,
  FOREIGN KEY (commit_id) REFERENCES commits(id),
  FOREIGN KEY (security_user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS deployments (
  id INT AUTO_INCREMENT PRIMARY KEY,
  commit_id INT,
  status ENUM('NOT_DEPLOYED','DEPLOYED','FAILED','ROLLED_BACK') DEFAULT 'NOT_DEPLOYED',
  environment VARCHAR(50),
  ops_user_id INT,
  deployed_at DATETIME,
  FOREIGN KEY (commit_id) REFERENCES commits(id),
  FOREIGN KEY (ops_user_id) REFERENCES users(id)
);
