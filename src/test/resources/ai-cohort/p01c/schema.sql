CREATE TABLE patient_basic_info (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL) ENGINE=InnoDB;
CREATE TABLE patient_relation_doctor (id BIGINT PRIMARY KEY AUTO_INCREMENT, doctor_id BIGINT NOT NULL, patient_id BIGINT NOT NULL, research_type INT DEFAULT 0, miss INT DEFAULT 0) ENGINE=InnoDB;
CREATE TABLE patient_follow_up_history (id BIGINT PRIMARY KEY, patient_basic_info_id BIGINT NOT NULL, research_type INT DEFAULT 0, follow_up_date DATETIME NULL, followUpDate DATETIME NULL, bqpg VARCHAR(4000) NULL, fzjc VARCHAR(2000) NULL) ENGINE=InnoDB;
