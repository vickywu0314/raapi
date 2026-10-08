ALTER TABLE patient_basic_info
 ADD follow_cycle INT DEFAULT 3,
 ADD create_date DATETIME, ADD createDate DATETIME,
 ADD happen_date DATETIME, ADD happenDate DATETIME,
 ADD smoke INT, ADD smoke_years INT, ADD smokeYears INT,
 ADD smoke_count_by_day INT, ADD smokeCountByDay INT,
 ADD smoke_stop INT, ADD smokeStop INT,
 ADD drink INT, ADD drink_years INT, ADD drinkYears INT,
 ADD drink_count_by_day INT, ADD drinkCountByDay INT,
 ADD acr_eular_score INT, ADD acrEularScore INT,
 ADD mobile VARCHAR(30), ADD nation VARCHAR(30), ADD marry INT,
 ADD height VARCHAR(30), ADD weight VARCHAR(30), ADD waistline VARCHAR(30),
 ADD xl VARCHAR(30), ADD xy VARCHAR(30), ADD xy_h VARCHAR(30), ADD xy_l VARCHAR(30),
 ADD allergy INT, ADD gms VARCHAR(2000), ADD jzs VARCHAR(2000);
ALTER TABLE patient_relation_doctor ADD reason VARCHAR(200), ADD other_miss_reason VARCHAR(200), ADD note VARCHAR(200);
ALTER TABLE patient_follow_up_history ADD doctor_id BIGINT;
CREATE TABLE `user` (id BIGINT PRIMARY KEY, name VARCHAR(100)) ENGINE=InnoDB;
