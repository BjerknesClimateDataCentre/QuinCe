CREATE INDEX USERQCFLAG ON sensor_values (user_qc_flag);
CREATE INDEX DATASETID_DATE ON sensor_values (dataset_id, date);
CREATE INDEX FILECOLUMN ON sensor_values (file_column);
CREATE INDEX DATASETID_DATE ON measurements (dataset_id, date);


-- ROLLBACK

-- DROP INDEX USERQCFLAG ON sensor_values;
-- DROP INDEX DATASETID_DATE ON sensor_values;
-- DROP INDEX FILECOLUMN ON sensor_values;
-- DROP INDEX DATASETID_DATE ON measurements;
