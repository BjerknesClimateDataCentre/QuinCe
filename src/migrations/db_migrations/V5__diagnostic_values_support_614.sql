# Add a diagnostic column to the file_column table
ALTER TABLE `file_column` ADD `diagnostic` BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE `diagnostic`.`diagnostic_data` (
  `file_definition_id` INT NOT NULL,
  `file_column` SMALLINT(3) NOT NULL,
  `measurement_id` INT NOT NULL,
  `value` DOUBLE NULL,
  PRIMARY KEY (`file_definition_id`, `file_column`, `measurement_id`),
  INDEX `DIAGNOSTIC_DATASETDATA_idx` (`measurement_id` ASC),
  CONSTRAINT `DIAGNOSTIC_FILECOLUMN`
    FOREIGN KEY (`file_definition_id` , `file_column`)
    REFERENCES `diagnostic`.`file_column` (`file_definition_id` , `file_column`)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,
  CONSTRAINT `DIAGNOSTIC_DATASETDATA`
    FOREIGN KEY (`measurement_id`)
    REFERENCES `diagnostic`.`dataset_data` (`id`)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT);
