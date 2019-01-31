-- Create the sensor_types table, which used to be in a config file
CREATE TABLE sensor_types (
  id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  parent INT NULL,
  depends_on INT NULL,
  depends_question TEXT NULL,
  internal_calibration TINYINT NOT NULL DEFAULT 0,
  diagnostic TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE SENSORTYPENAME (name))
  ENGINE = InnoDB;

-- Intake temp
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration)
  VALUES ('Intake Temperature', NULL, NULL, NULL, 0);

-- Salinity
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration)
  VALUES ('Salinity', NULL, NULL, NULL, 0);

-- Equilibrator Temperature
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration)
  VALUES ('Equilibrator Temperature', NULL, NULL, NULL, 0);

-- Equilibrator Pressure - parent of the two pressure types
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration)
  VALUES ('Equilibrator Pressure', NULL, NULL, NULL, 0);

-- Equilibrator Pressure (absolute)
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration)
  VALUES ('Equilibrator Pressure (absolute)',
    (SELECT id FROM sensor_types WHERE name = 'Equilibrator Pressure'),
    NULL, NULL, 0);

-- Equilibrator Pressure (differential)
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration)
  VALUES ('Equilibrator Pressure (differential)',
    (SELECT id FROM sensor_types WHERE name = 'Equilibrator Pressure'),
    NULL, NULL, 0);

-- Ambient Pressure
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration)
  VALUES ('Ambient Pressure', NULL, NULL, NULL, 0);

-- xH2O in gas
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration)
  VALUES ('xH₂O in gas', NULL, NULL, NULL, 1);

-- CO2 (Licor etc)
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration)
  VALUES ('CO₂ in gas', NULL, NULL, NULL, 1);

-- Atmospheric Pressure
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration)
  VALUES ('Atmospheric Pressure', NULL, NULL, NULL, 0);

-- Equilibrator Pressure (differential) depends on Ambient Pressure
UPDATE sensor_types
  SET depends_on = (SELECT id FROM sensor_types WHERE name = 'Ambient Pressure')
  WHERE name = 'Equilibrator Pressure (differential)';

-- CO₂ sometimes needs to be mathematically dried, which needs xH₂O
UPDATE sensor_types
  SET depends_on = (SELECT id FROM sensor_types WHERE name = 'xH₂O in gas')
  WHERE name = 'CO₂ in gas';

UPDATE sensor_types
  SET depends_question = 'Do values from CO₂ require moisture adjustment?'
  WHERE name = 'CO₂ in gas';

-- Diagnostic sensors
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration, diagnostic)
  VALUES ('Diagnostic Temperature', NULL, NULL, NULL, 0, 1);
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration, diagnostic)
  VALUES ('Diagnostic Pressure', NULL, NULL, NULL, 0, 1);
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration, diagnostic)
  VALUES ('Diagnostic Air Flow', NULL, NULL, NULL, 0, 1);
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration, diagnostic)
  VALUES ('Diagnostic Water Flow', NULL, NULL, NULL, 0, 1);
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration, diagnostic)
  VALUES ('Diagnostic Voltage', NULL, NULL, NULL, 0, 1);
INSERT INTO sensor_types (name, parent, depends_on, depends_question, internal_calibration, diagnostic)
  VALUES ('Diagnostic Misc', NULL, NULL, NULL, 0, 1);

------------------------------
-- Variable definition
CREATE TABLE measured_variables (
  id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE VARNAME_UNIQUE (name)
  ) ENGINE = InnoDB;

INSERT INTO measured_variables (name) VALUES ('Underway Marine pCO₂');

CREATE TABLE variable_sensors (
  variable_id INT NOT NULL,
  sensor_type INT NOT NULL,
  core TINYINT(1) NOT NULL DEFAULT 0,
  questionable_cascade TINYINT(1) NOT NULL,
  bad_cascade TINYINT(1) NOT NULL,
  PRIMARY KEY (variable_id, sensor_type),
  CONSTRAINT VARSENSOR_VARIABLE
    FOREIGN KEY (variable_id)
    REFERENCES measured_variables (id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,
  CONSTRAINT VARSENSOR_SENSOR
    FOREIGN KEY (sensor_type)
    REFERENCES sensor_types (id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT);

INSERT INTO variable_sensors (variable_id, sensor_type, core, questionable_cascade, bad_cascade)
  VALUES (
    (SELECT id FROM measured_variables),
    (SELECT id FROM sensor_types WHERE name = 'Intake Temperature'),
    0, 3, 4);

INSERT INTO variable_sensors (variable_id, sensor_type, core, questionable_cascade, bad_cascade)
  VALUES (
    (SELECT id FROM measured_variables),
    (SELECT id FROM sensor_types WHERE name = 'Salinity'),
    0, 2, 3);

INSERT INTO variable_sensors (variable_id, sensor_type, core, questionable_cascade, bad_cascade)
  VALUES (
    (SELECT id FROM measured_variables),
    (SELECT id FROM sensor_types WHERE name = 'Equilibrator Temperature'),
    0, 3, 4);

INSERT INTO variable_sensors (variable_id, sensor_type, core, questionable_cascade, bad_cascade)
  VALUES (
    (SELECT id FROM measured_variables),
    (SELECT id FROM sensor_types WHERE name = 'Equilibrator Pressure'),
    0, 3, 4);

INSERT INTO variable_sensors (variable_id, sensor_type, core, questionable_cascade, bad_cascade)
  VALUES (
    (SELECT id FROM measured_variables),
    (SELECT id FROM sensor_types WHERE name = 'CO₂ in gas'),
    0, 3, 4);
