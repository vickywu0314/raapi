INSERT INTO patient_basic_info (id,name) VALUES (1,'synthetic-same-name'),(2,'synthetic-same-name'),(3,'synthetic-same-name'),(4,'synthetic-same-name'),(5,'synthetic-same-name'),(6,'synthetic-same-name'),(7,'synthetic-same-name');
INSERT INTO patient_relation_doctor (doctor_id,patient_id,research_type,miss) VALUES (101,1,0,0),(101,1,1,0),(202,1,5,0),(101,2,6,0),(202,2,2,0),(101,3,3,1),(202,4,4,0),(101,5,7,0),(101,6,2,0),(101,7,4,0),(101,8,0,0);
INSERT INTO patient_follow_up_history (id,patient_basic_info_id,research_type,follow_up_date,followUpDate,bqpg) VALUES
(10,1,0,'2026-10-07',NULL,'{"result":{"crpScore":9}}'),
(11,1,1,'2026-10-07',NULL,'{"result":{"crpScore":2.295}}'),
(30,3,3,NULL,'2026-10-06','{"result":{"crpScore":"2.705"}}'),
(50,5,7,'2026-10-07',NULL,'{"result":{"crpScore":4.105}}'),
(60,6,2,'2026-10-07',NULL,'{"result":{"esrScore":8}}'),
(70,7,4,'2026-10-07',NULL,'{"result":{"crpScore":0}}');
