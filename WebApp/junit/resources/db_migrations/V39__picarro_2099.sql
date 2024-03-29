-- Revise variable attributes setup
UPDATE variables
    SET attributes = '{"atm_pres_sensor_height": {"name": "Atmospheric Pressure Sensor Height (m)", "type": "NUMBER"}}'
    WHERE name = 'Underway Atmospheric pCO₂';
    
UPDATE variables
    SET attributes = '{"zero_flush": {"name": "Zero Flushing Time (s)", "type": "NUMBER"}}'
    WHERE name = 'CONTROS pCO₂';

-- Add attribute option to variable sensors
ALTER TABLE variable_sensors 
  ADD COLUMN attribute_name VARCHAR(100) NULL AFTER core;
    
ALTER TABLE variable_sensors 
  ADD COLUMN attribute_value VARCHAR(100) NULL AFTER attribute_name;
    
-- New sensor types
INSERT INTO sensor_types (
    name, vargroup, parent, depends_on, depends_question, internal_calibration,
    diagnostic, display_order, column_code, column_heading, units
  ) VALUES (
    'x¹²CO₂ (with standards)', 'CO₂', NULL, NULL, NULL, 1, 0, 710,
    'X12CO2WBDY', '¹²CO₂ Mole Fraction', 'μmol mol-1'
  );

INSERT INTO sensor_types (
    name, vargroup, parent, depends_on, depends_question, internal_calibration,
    diagnostic, display_order, column_code, column_heading, units
  ) VALUES (
    'x¹³CO₂ (with standards)', 'CO₂', NULL, NULL, NULL, 1, 0, 711,
    'X13CO2WBDY', '¹³CO₂ Mole Fraction', 'μmol mol-1'
  );

INSERT INTO sensor_types (
    name, vargroup, parent, depends_on, depends_question, internal_calibration,
    diagnostic, display_order, column_code, column_heading, units
  ) VALUES (
    'x¹²CO₂ + x¹³CO₂ (with standards)', 'CO₂', NULL, NULL, NULL, 1, 0, 712,
    'XCO2WBDY', '¹²CO₂ + ¹³CO₂ Mole Fraction', 'μmol mol-1'
  );

-- Variable
INSERT INTO variables (name, attributes, properties)
  VALUES ('Underway Marine pCO₂ from ¹²CO₂/¹³CO₂',
    '{"cal_gas_type": {"name": "Calibration Gas Type", "type": "ENUM", "values": ["¹²CO₂/¹³CO₂", "Total CO₂"]}}', NULL);

INSERT INTO variables (name, attributes, properties)
  VALUES ('Underway Atmospheric pCO₂ from ¹²CO₂/¹³CO₂',
    '{"atm_pres_sensor_height": {"name": "Atmospheric Pressure Sensor Height (m)", "type": "NUMBER"},
      "cal_gas_type": {"name": "Calibration Gas Type", "type": "ENUM", "values": ["¹²CO₂/¹³CO₂", "Total CO₂"]}}', NULL);

-- Marine variable sensors
-- SST
INSERT INTO variable_sensors (
    variable_id, sensor_type, core, questionable_cascade, bad_cascade,
    export_column_short, export_column_long, export_column_code
  )
  VALUES (
    (SELECT id FROM variables WHERE name = 'Underway Marine pCO₂ from ¹²CO₂/¹³CO₂'),
    (SELECT id FROM sensor_types WHERE column_code = 'TEMPPR01'),
    0, 3, 4, NULL, NULL, NULL
  );

-- Salinity
INSERT INTO variable_sensors (
    variable_id, sensor_type, core, questionable_cascade, bad_cascade,
    export_column_short, export_column_long, export_column_code
  )
  VALUES (
    (SELECT id FROM variables WHERE name = 'Underway Marine pCO₂ from ¹²CO₂/¹³CO₂'),
    (SELECT id FROM sensor_types WHERE column_code = 'PSALPR01'),
    0, 2, 3, NULL, NULL, NULL
  );

-- Equilibrator Temperature
INSERT INTO variable_sensors (
    variable_id, sensor_type, core, questionable_cascade, bad_cascade,
    export_column_short, export_column_long, export_column_code
  )
  VALUES (
    (SELECT id FROM variables WHERE name = 'Underway Marine pCO₂ from ¹²CO₂/¹³CO₂'),
    (SELECT id FROM sensor_types WHERE column_code = 'TEMPEQMN'),
    0, 3, 4, NULL, NULL, NULL
  );

-- Equilibrator Pressure
INSERT INTO variable_sensors (
    variable_id, sensor_type, core, questionable_cascade, bad_cascade,
    export_column_short, export_column_long, export_column_code
  )
  VALUES (
    (SELECT id FROM variables WHERE name = 'Underway Marine pCO₂ from ¹²CO₂/¹³CO₂'),
    (SELECT id FROM sensor_types WHERE column_code = 'PRESSEQ'),
    0, 3, 4, NULL, NULL, NULL
  );

-- xH₂O
INSERT INTO variable_sensors (
    variable_id, sensor_type, core, questionable_cascade, bad_cascade,
    export_column_short, export_column_long, export_column_code
  )
  VALUES (
    (SELECT id FROM variables WHERE name = 'Underway Marine pCO₂ from ¹²CO₂/¹³CO₂'),
    (SELECT id FROM sensor_types WHERE name = 'xH₂O (with standards)'),
    0, 3, 4, NULL, NULL, NULL
  );
  
-- x¹²CO₂
INSERT INTO variable_sensors (
    variable_id, sensor_type, core, questionable_cascade, bad_cascade,
    export_column_short, export_column_long, export_column_code
  )
  VALUES (
    (SELECT id FROM variables WHERE name = 'Underway Marine pCO₂ from ¹²CO₂/¹³CO₂'),
    (SELECT id FROM sensor_types WHERE column_code = 'X12CO2WBDY'),
    1, 3, 4, NULL, NULL, NULL
  );

-- x¹³CO₂
INSERT INTO variable_sensors (
    variable_id, sensor_type, core, questionable_cascade, bad_cascade,
    export_column_short, export_column_long, export_column_code
  )
  VALUES (
    (SELECT id FROM variables WHERE name = 'Underway Marine pCO₂ from ¹²CO₂/¹³CO₂'),
    (SELECT id FROM sensor_types WHERE column_code = 'X13CO2WBDY'),
    0, 3, 4, NULL, NULL, NULL
  );

-- x¹²CO₂ + x¹³CO₂
INSERT INTO variable_sensors (
    variable_id, sensor_type, core, attribute_name, attribute_value,
    questionable_cascade, bad_cascade, export_column_short,
    export_column_long, export_column_code
  )
  VALUES (
    (SELECT id FROM variables WHERE name = 'Underway Marine pCO₂ from ¹²CO₂/¹³CO₂'),
    (SELECT id FROM sensor_types WHERE name = 'x¹²CO₂ + x¹³CO₂ (with standards)'),
    0, 'cal_gas_type', 'Total CO₂', 3, 4, NULL, NULL, NULL
  );

-- Atmospheric variable sensors
-- SST
INSERT INTO variable_sensors (
    variable_id, sensor_type, core, questionable_cascade, bad_cascade,
    export_column_short, export_column_long, export_column_code
  )
  VALUES (
    (SELECT id FROM variables WHERE name = 'Underway Atmospheric pCO₂ from ¹²CO₂/¹³CO₂'),
    (SELECT id FROM sensor_types WHERE column_code = 'TEMPPR01'),
    0, 3, 4, NULL, NULL, NULL
  );

-- Salinity
INSERT INTO variable_sensors (
    variable_id, sensor_type, core, questionable_cascade, bad_cascade,
    export_column_short, export_column_long, export_column_code
  )
  VALUES (
    (SELECT id FROM variables WHERE name = 'Underway Atmospheric pCO₂ from ¹²CO₂/¹³CO₂'),
    (SELECT id FROM sensor_types WHERE column_code = 'PSALPR01'),
    0, 2, 3, NULL, NULL, NULL
  );

-- xH₂O
INSERT INTO variable_sensors (
    variable_id, sensor_type, core, questionable_cascade, bad_cascade,
    export_column_short, export_column_long, export_column_code
  )
  VALUES (
    (SELECT id FROM variables WHERE name = 'Underway Atmospheric pCO₂ from ¹²CO₂/¹³CO₂'),
    (SELECT id FROM sensor_types WHERE name = 'xH₂O (with standards)'),
    0, 3, 4, NULL, NULL, NULL
  );
  
-- x¹²CO₂
INSERT INTO variable_sensors (
    variable_id, sensor_type, core, questionable_cascade, bad_cascade,
    export_column_short, export_column_long, export_column_code
  )
  VALUES (
    (SELECT id FROM variables WHERE name = 'Underway Atmospheric pCO₂ from ¹²CO₂/¹³CO₂'),
    (SELECT id FROM sensor_types WHERE column_code = 'X12CO2WBDY'),
    1, 3, 4, NULL, NULL, NULL
  );

-- x¹³CO₂
INSERT INTO variable_sensors (
    variable_id, sensor_type, core, questionable_cascade, bad_cascade,
    export_column_short, export_column_long, export_column_code
  )
  VALUES (
    (SELECT id FROM variables WHERE name = 'Underway Atmospheric pCO₂ from ¹²CO₂/¹³CO₂'),
    (SELECT id FROM sensor_types WHERE column_code = 'X13CO2WBDY'),
    0, 3, 4, NULL, NULL, NULL
  );

-- x¹²CO₂ + x¹³CO₂
INSERT INTO variable_sensors (
    variable_id, sensor_type, core, attribute_name, attribute_value,
    questionable_cascade, bad_cascade, export_column_short,
    export_column_long, export_column_code
  )
  VALUES (
    (SELECT id FROM variables WHERE name = 'Underway Atmospheric pCO₂ from ¹²CO₂/¹³CO₂'),
    (SELECT id FROM sensor_types WHERE name = 'x¹²CO₂ + x¹³CO₂ (with standards)'),
    0, 'cal_gas_type', 'Total CO₂', 3, 4, NULL, NULL, NULL
  );

-- Atmospheric Pressure
INSERT INTO variable_sensors (
    variable_id, sensor_type, core, questionable_cascade, bad_cascade,
    export_column_short, export_column_long, export_column_code
  )
  VALUES (
    (SELECT id FROM variables WHERE name = 'Underway Atmospheric pCO₂ from ¹²CO₂/¹³CO₂'),
    (SELECT id FROM sensor_types WHERE column_code = 'CAPHZZ01'),
    0, 3, 4, NULL, NULL, NULL
  );
