CREATE TABLE IF NOT EXISTS app_user (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(40) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 display_name VARCHAR(60) NOT NULL,
 role VARCHAR(16) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 must_change BOOLEAN NOT NULL DEFAULT TRUE,
 credential_version BIGINT NOT NULL DEFAULT 0,
 failed_attempts INT NOT NULL DEFAULT 0,
 locked_until VARCHAR(19),
 created_at VARCHAR(19) NOT NULL
);
CREATE TABLE IF NOT EXISTS student (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 student_no VARCHAR(40) NOT NULL UNIQUE,
 name VARCHAR(60) NOT NULL,
 college VARCHAR(100) NOT NULL DEFAULT '',
 major VARCHAR(100) NOT NULL DEFAULT '',
 class_name VARCHAR(80) NOT NULL,
 grade VARCHAR(20) NOT NULL DEFAULT '',
 phone VARCHAR(30) NOT NULL DEFAULT '',
 teacher_id BIGINT NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0,
 created_at VARCHAR(19) NOT NULL,
 updated_at VARCHAR(19) NOT NULL,
 FOREIGN KEY (teacher_id) REFERENCES app_user(id)
);
CREATE TABLE IF NOT EXISTS record_template (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(80) NOT NULL,
 title VARCHAR(120) NOT NULL,
 body TEXT NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0,
 created_at VARCHAR(19) NOT NULL
);
CREATE TABLE IF NOT EXISTS talk_record (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 record_no VARCHAR(40) NOT NULL UNIQUE,
 student_id BIGINT NOT NULL,
 teacher_id BIGINT NOT NULL,
 template_id BIGINT NOT NULL,
 topic VARCHAR(120) NOT NULL,
 category VARCHAR(20) NOT NULL,
 occurred_at VARCHAR(19) NOT NULL,
 duration_minutes INT NOT NULL,
 place VARCHAR(120) NOT NULL,
 mode VARCHAR(20) NOT NULL,
 background TEXT NOT NULL,
 student_statement TEXT NOT NULL,
 teacher_advice TEXT NOT NULL,
 agreement TEXT NOT NULL,
 followup_date VARCHAR(10),
 content TEXT NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 version BIGINT NOT NULL DEFAULT 0,
 snapshot_student_name VARCHAR(60),
 snapshot_student_no VARCHAR(40),
 snapshot_class_name VARCHAR(80),
 snapshot_teacher_name VARCHAR(60),
 snapshot_template_name VARCHAR(80),
 snapshot_title VARCHAR(120),
 content_hash VARCHAR(64),
 archived_at VARCHAR(19),
 followup_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
 created_at VARCHAR(19) NOT NULL,
 updated_at VARCHAR(19) NOT NULL,
 FOREIGN KEY (student_id) REFERENCES student(id),
 FOREIGN KEY (teacher_id) REFERENCES app_user(id),
 FOREIGN KEY (template_id) REFERENCES record_template(id)
);
CREATE TABLE IF NOT EXISTS followup (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 record_id BIGINT NOT NULL,
 teacher_id BIGINT NOT NULL,
 content TEXT NOT NULL,
 next_date VARCHAR(10),
 resolved BOOLEAN NOT NULL,
 created_at VARCHAR(19) NOT NULL,
 FOREIGN KEY (record_id) REFERENCES talk_record(id),
 FOREIGN KEY (teacher_id) REFERENCES app_user(id)
);
CREATE TABLE IF NOT EXISTS audit_log (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 actor_id BIGINT,
 actor_name VARCHAR(60) NOT NULL,
 action VARCHAR(50) NOT NULL,
 target_type VARCHAR(30) NOT NULL,
 target_id BIGINT,
 detail VARCHAR(300) NOT NULL DEFAULT '',
 created_at VARCHAR(19) NOT NULL
);
