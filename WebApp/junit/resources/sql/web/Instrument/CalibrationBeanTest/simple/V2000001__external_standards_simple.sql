-- A simple set of datasets and standards for basic testing

-- 2019-06-03T00:00:00 to 2019-06-05T00:00:00
INSERT INTO dataset (id, instrument_id, name, start, end, status, status_date)
  VALUES (1001, 1000000, 'A', 1559520000000, 1559692800000, 4, 0);

-- 2019-06-01T00:00:00
INSERT INTO calibration (id, instrument_id, type, target, deployment_date, coefficients, class)
  VALUES (1001, 1000000, 'EXTERNAL_STANDARD', 'TARGET1', 1559347200000, '{"xCO₂ (with standards)":"200.0","xH₂O (with standards)":"0.0"}', 'ExternalStandard');

-- 2019-06-01T00:00:00
INSERT INTO calibration (id, instrument_id, type, target, deployment_date, coefficients, class)
  VALUES (1002, 1000000, 'EXTERNAL_STANDARD', 'TARGET2', 1559347200000, '{"xCO₂ (with standards)":"400.0","xH₂O (with standards)":"0.0"}', 'ExternalStandard');

-- 2019-06-01T00:00:00
INSERT INTO calibration (id, instrument_id, type, target, deployment_date, coefficients, class)
  VALUES (1003, 1000000, 'EXTERNAL_STANDARD', 'TARGET3', 1559347200000, '{"xCO₂ (with standards)":"500.0","xH₂O (with standards)":"0.0"}', 'ExternalStandard');
