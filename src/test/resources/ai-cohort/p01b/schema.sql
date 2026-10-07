CREATE TABLE patient_follow_up_history (
    id BIGINT PRIMARY KEY,
    patient_basic_info_id BIGINT NOT NULL,
    research_type INT NOT NULL,
    bqpg LONGTEXT,
    follow_up_date DATE,
    followUpDate DATE
) ENGINE=InnoDB
