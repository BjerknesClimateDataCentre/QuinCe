package uk.ac.exeter.QuinCe.data.Dataset;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.sql.DataSource;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import uk.ac.exeter.QuinCe.data.Dataset.DataReduction.DataReducerFactory;
import uk.ac.exeter.QuinCe.data.Dataset.DataReduction.DataReductionException;
import uk.ac.exeter.QuinCe.data.Dataset.DataReduction.DataReductionRecord;
import uk.ac.exeter.QuinCe.data.Dataset.DataReduction.ReadOnlyDataReductionRecord;
import uk.ac.exeter.QuinCe.data.Dataset.QC.Flag;
import uk.ac.exeter.QuinCe.data.Dataset.QC.InvalidFlagException;
import uk.ac.exeter.QuinCe.data.Dataset.QC.Routines.AutoQCResult;
import uk.ac.exeter.QuinCe.data.Dataset.QC.Routines.RoutineException;
import uk.ac.exeter.QuinCe.data.Instrument.Instrument;
import uk.ac.exeter.QuinCe.data.Instrument.InstrumentDB;
import uk.ac.exeter.QuinCe.data.Instrument.InstrumentException;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.InstrumentVariable;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.SensorAssignments;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.SensorType;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.SensorsConfiguration;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.VariableNotFoundException;
import uk.ac.exeter.QuinCe.utils.DatabaseException;
import uk.ac.exeter.QuinCe.utils.DatabaseUtils;
import uk.ac.exeter.QuinCe.utils.DateTimeUtils;
import uk.ac.exeter.QuinCe.utils.MissingParam;
import uk.ac.exeter.QuinCe.utils.MissingParamException;
import uk.ac.exeter.QuinCe.utils.RecordNotFoundException;
import uk.ac.exeter.QuinCe.utils.StringUtils;
import uk.ac.exeter.QuinCe.web.datasets.data.DatasetMeasurementData;
import uk.ac.exeter.QuinCe.web.datasets.data.Field;
import uk.ac.exeter.QuinCe.web.datasets.data.FieldValue;
import uk.ac.exeter.QuinCe.web.datasets.data.MeasurementDataException;
import uk.ac.exeter.QuinCe.web.system.ResourceManager;

/**
 * Class for handling database queries related to dataset data
 *
 * @author Steve Jones
 *
 */
public class DataSetDataDB {

  /**
   * Field Set ID for sensor values
   */
  public static final long SENSORS_FIELDSET = -1;

  /**
   * Field set name for sensor values
   */
  public static final String SENSORS_FIELDSET_NAME = "Sensors";

  /**
   * Field Set ID for diagnostic values
   */
  public static final long DIAGNOSTICS_FIELDSET = -2;

  /**
   * Field set name for diagnostic values
   */
  public static final String DIAGNOSTICS_FIELDSET_NAME = "Diagnostics";

  /**
   * Statement to store a sensor value
   */
  private static final String STORE_NEW_SENSOR_VALUE_STATEMENT = "INSERT INTO "
    + "sensor_values (dataset_id, file_column, date, value, "
    + "auto_qc, user_qc_flag, user_qc_message) "
    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

  private static final String UPDATE_SENSOR_VALUE_STATEMENT = "UPDATE sensor_values "
    + "SET auto_qc=?, user_qc_flag=?, user_qc_message=? WHERE id = ?";

  /**
   * Statement to remove all sensor values for a data set
   */
  private static final String DELETE_SENSOR_VALUES_STATEMENT = "DELETE FROM "
    + "sensor_values WHERE dataset_id = ?";

  private static final String GET_SENSOR_VALUES_FOR_DATASET_QUERY = "SELECT "
    + "id, file_column, date, value, auto_qc, " // 5
    + "user_qc_flag, user_qc_message " // 7
    + "FROM sensor_values WHERE dataset_id = ?";

  private static final String GET_SENSOR_VALUES_FOR_DATASET_NO_FLUSHING_QUERY = "SELECT "
    + "id, file_column, date, value, auto_qc, " // 5
    + "user_qc_flag, user_qc_message " // 7
    + "FROM sensor_values WHERE dataset_id = ? AND user_qc_flag != "
    + Flag.VALUE_FLUSHING;

  /**
   * Statement to store a measurement record
   */
  private static final String STORE_MEASUREMENT_STATEMENT = "INSERT INTO "
    + "measurements (dataset_id, date, run_type) " + "VALUES (?, ?, ?)";

  /**
   * Query to get all measurement records for a dataset
   */
  private static final String GET_MEASUREMENTS_QUERY = "SELECT "
    + "id, date, run_type " + "FROM measurements WHERE dataset_id = ? "
    + "ORDER BY date ASC";

  private static final String GET_MEASUREMENT_TIMES_QUERY = "SELECT "
    + "id, date FROM measurements WHERE dataset_id = ? " + "AND run_type IN "
    + DatabaseUtils.IN_PARAMS_TOKEN + " ORDER BY date ASC";

  /**
   * Statement to store a data reduction result
   */
  private static final String STORE_DATA_REDUCTION_STATEMENT = "INSERT INTO "
    + "data_reduction (measurement_id, variable_id, calculation_values, "
    + "qc_flag, qc_message) VALUES (?, ?, ?, ?, ?)";

  private static final String DELETE_DATA_REDUCTION_STATEMENT = "DELETE FROM "
    + "data_reduction WHERE measurement_id IN "
    + "(SELECT id FROM measurements WHERE dataset_id = ?)";

  private static final String DELETE_MEASUREMENTS_STATEMENT = "DELETE FROM "
    + "measurements WHERE dataset_id = ?";

  private static final String GET_SENSOR_VALUES_BY_DATE_QUERY = "SELECT "
    + "sv.id, sv.file_column, sv.date, sv.value, sv.auto_qc, " // 5
    + "sv.user_qc_flag, sv.user_qc_message, mv.measurement_id " // 8
    + "FROM sensor_values sv LEFT JOIN measurement_values mv "
    + "ON sv.id = mv.sensor_value_id WHERE sv.dataset_id = ? "
    + "AND sv.date IN " + DatabaseUtils.IN_PARAMS_TOKEN + " "
    + "ORDER BY sv.date ASC";

  private static final String GET_SENSOR_VALUES_BY_SENSOR_QUERY = "SELECT "
    + "sv.id, sv.file_column, sv.date, sv.value, sv.auto_qc, " // 5
    + "sv.user_qc_flag, sv.user_qc_message, mv.measurement_id " // 8
    + "FROM sensor_values sv LEFT JOIN measurement_values mv "
    + "ON sv.id = mv.sensor_value_id WHERE sv.dataset_id = ? "
    + "AND sv.file_column IN " + DatabaseUtils.IN_PARAMS_TOKEN + " "
    + "ORDER BY sv.date ASC";

  private static final String GET_SENSOR_VALUES_FOR_COLUMNS_QUERY = "SELECT "
    + "id, file_column, date, value, auto_qc, " // 5
    + "user_qc_flag, user_qc_message " // 8
    + "FROM sensor_values WHERE dataset_id = ? AND file_column IN "
    + DatabaseUtils.IN_PARAMS_TOKEN + "ORDER BY date";

  private static final String GET_SENSOR_VALUE_DATES_QUERY = "SELECT DISTINCT "
    + "date FROM sensor_values WHERE dataset_id = ? ORDER BY date ASC";

  private static final String GET_SENSOR_VALUE_DATES_FOR_COLUMNS_QUERY = "SELECT "
    + "DISTINCT date FROM sensor_values WHERE dataset_id = ? AND FILE_COLUMN IN "
    + DatabaseUtils.IN_PARAMS_TOKEN + " ORDER BY date ASC";

  private static final String GET_REQUIRED_FLAGS_QUERY = "SELECT "
    + "COUNT(*) FROM sensor_values WHERE dataset_id = ? "
    + "AND user_qc_flag = " + Flag.VALUE_NEEDED;

  private static final String GET_DATA_REDUCTION_DATE_FILTER_QUERY = "SELECT "
    + "m.date, dr.variable_id, dr.calculation_values, dr.qc_flag, dr.qc_message "
    + "FROM measurements m INNER JOIN data_reduction dr "
    + "ON (m.id = dr.measurement_id) WHERE m.dataset_id = ? " + "AND m.date IN "
    + DatabaseUtils.IN_PARAMS_TOKEN + " ORDER BY m.date ASC";

  private static final String GET_DATA_REDUCTION_QUERY_OLD = "SELECT "
    + "m.date, dr.calculation_values, qc_flag "
    + "FROM measurements m INNER JOIN data_reduction dr "
    + "ON (m.id = dr.measurement_id) WHERE m.dataset_id = ? "
    + "AND dr.variable_id = ? ORDER BY m.date ASC";

  private static final String GET_DATA_REDUCTION_QUERY = "SELECT "
    + "dr.measurement_id, dr.variable_id, dr.calculation_values, "
    + "dr.qc_flag, dr.qc_message FROM data_reduction dr INNER JOIN "
    + "measurements m ON dr.measurement_id = m.id WHERE m.dataset_id = ? "
    + "ORDER BY dr.measurement_id ASC";

  private static final String SET_QC_STATEMENT = "UPDATE sensor_values SET "
    + "user_qc_flag = ?, user_qc_message = ? " + "WHERE id = ?";

  private static final String GET_RECORD_COUNT_QUERY = "SELECT "
    + "COUNT(DISTINCT(date)) FROM sensor_values WHERE dataset_id = ?";

  private static final String GET_RUN_TYPES_QUERY = "SELECT "
    + "date, value FROM sensor_values "
    + " WHERE dataset_id = ? AND file_column IN "
    + DatabaseUtils.IN_PARAMS_TOKEN + " ORDER BY date ASC";

  private static final String STORE_MEASUREMENT_VALUE_STATEMENT = "INSERT INTO "
    + "measurement_values (measurement_id, file_column_id, "
    + "prior, post) VALUES (?, ?, ?, ?)";

  private static final String DELETE_MEASUREMENT_VALUES_STATEMENT = "DELETE "
    + "FROM measurement_values WHERE measurement_id IN "
    + "(SELECT id FROM measurements WHERE dataset_id = ?)";

  /**
   * Take a list of fields, and return those which come from the dataset data.
   * Any others will come from calculation data and will be left alone.
   *
   * @param conn
   *          A database connection
   * @param dataSet
   *          The data set to which the fields belong
   * @param originalFields
   *          The list of fields
   * @return The fields that come from dataset data
   * @throws DatabaseException
   *           If a database error occurs
   * @throws MissingParamException
   *           If any required parameters are missing
   * @throws RecordNotFoundException
   *           If the dataset or its instrument do not exist
   * @throws InstrumentException
   *           If the instrument details cannot be retrieved
   */
  public static List<String> extractDatasetFields(Connection conn,
    DataSet dataSet, List<String> originalFields) throws MissingParamException,
    DatabaseException, RecordNotFoundException, InstrumentException {

    List<String> datasetFields = new ArrayList<String>();

    ResourceManager resourceManager = ResourceManager.getInstance();
    SensorsConfiguration sensorConfig = resourceManager
      .getSensorsConfiguration();

    Instrument instrument = InstrumentDB.getInstrument(conn,
      dataSet.getInstrumentId(), resourceManager.getSensorsConfiguration(),
      resourceManager.getRunTypeCategoryConfiguration());

    SensorAssignments sensorAssignments = instrument.getSensorAssignments();

    for (String originalField : originalFields) {

      switch (originalField) {
      case "id":
      case "date": {
        datasetFields.add(originalField);
        break;
      }
      default: {
        // Sensor value columns
        for (SensorType sensorType : sensorConfig.getSensorTypes()) {
          if (sensorAssignments.getAssignmentCount(sensorType) > 0) {
            if (originalField.equals(sensorType.getDatabaseFieldName())) {
              // TODO Eventually this will use the sensor name as the label, and
              // the sensor type as the group
              datasetFields.add(originalField);
              break;
            }
          }
        }

        break;
      }
      }
    }

    return datasetFields;
  }

  /**
   * Determine whether or not a given field is a dataset-level field
   *
   * @param conn
   *          A database connection
   * @param dataset
   *          The dataset to which the field belongs
   * @param field
   *          The field name
   * @return {@code true} if the field is a dataset field; {@code false} if it
   *         is not
   * @throws DatabaseException
   *           If a database error occurs
   * @throws MissingParamException
   *           If any required parameters are missing
   * @throws RecordNotFoundException
   *           If the dataset or its instrument do not exist
   * @throws InstrumentException
   *           If the instrument details cannot be retrieved
   */
  public static boolean isDatasetField(Connection conn, DataSet dataset,
    String field) throws MissingParamException, DatabaseException,
    RecordNotFoundException, InstrumentException {

    List<String> fieldList = new ArrayList<String>(1);
    fieldList.add(field);

    List<String> detectedDatasetField = extractDatasetFields(conn, dataset,
      fieldList);

    return (detectedDatasetField.size() > 0);
  }

  /**
   * Store a set of sensor values in the database.
   *
   * Values will only be stored if their {@code dirty} flag is set.
   *
   * If a sensor value has a database ID, it will be updated. Otherwise it will
   * be stored as a new record. Note that the new records will not be given an
   * ID; they must be re-read from the database afterwards.
   *
   * @param conn
   *          A database connection
   * @param sensorValues
   *          The sensor values
   * @throws DatabaseException
   *           If a database error occurs
   * @throws MissingParamException
   *           If any required parameters are missing
   */
  public static void storeSensorValues(Connection conn,
    Collection<SensorValue> sensorValues)
    throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(conn, "conn");
    MissingParam.checkMissing(sensorValues, "sensorValues");

    PreparedStatement addStmt = null;
    PreparedStatement updateStmt = null;

    try {
      addStmt = conn.prepareStatement(STORE_NEW_SENSOR_VALUE_STATEMENT);
      updateStmt = conn.prepareStatement(UPDATE_SENSOR_VALUE_STATEMENT);

      for (SensorValue value : sensorValues) {
        if (value.isDirty()) {

          if (!value.isInDatabase()) {
            addStmt.setLong(1, value.getDatasetId());
            addStmt.setLong(2, value.getColumnId());
            addStmt.setLong(3, DateTimeUtils.dateToLong(value.getTime()));
            if (null == value.getValue()) {
              addStmt.setNull(4, Types.VARCHAR);
            } else {
              addStmt.setString(4, value.getValue());
            }

            addStmt.setString(5, value.getAutoQcResult().toJson());
            addStmt.setInt(6, value.getUserQCFlag().getFlagValue());
            addStmt.setString(7, value.getUserQCMessage());

            addStmt.addBatch();
          } else {
            updateStmt.setString(1, value.getAutoQcResult().toJson());
            updateStmt.setInt(2, value.getUserQCFlag().getFlagValue());
            updateStmt.setString(3, value.getUserQCMessage());
            updateStmt.setLong(4, value.getId());

            updateStmt.addBatch();
          }
        }
      }

      addStmt.executeBatch();
      updateStmt.executeBatch();
    } catch (SQLException e) {
      throw new DatabaseException("Error storing sensor values", e);
    } finally {
      DatabaseUtils.closeStatements(addStmt, updateStmt);
    }

    // Clear the dirty flag on all the sensor values
    SensorValue.clearDirtyFlag(sensorValues);
  }

  /**
   * Remove all sensor values for a dataset
   *
   * @param conn
   *          A database connection
   * @param datasetId
   *          The dataset's database ID
   * @throws DatabaseException
   *           If a database error occurs
   * @throws MissingParamException
   *           If any required parameters are missing
   */
  public static void deleteSensorValues(Connection conn, long datasetId)
    throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(conn, "conn");
    MissingParam.checkZeroPositive(datasetId, "datasetId");

    PreparedStatement stmt = null;

    try {
      stmt = conn.prepareStatement(DELETE_SENSOR_VALUES_STATEMENT);

      stmt.setLong(1, datasetId);
      stmt.execute();
    } catch (SQLException e) {
      throw new DatabaseException("Error storing sensor values", e);
    } finally {
      DatabaseUtils.closeStatements(stmt);
    }
  }

  /**
   * Get all the sensor values for a dataset grouped by their column in the
   * source data file(s)
   *
   * @param conn
   *          A database connection
   * @param datasetId
   *          The database ID of the dataset whose values are to be retrieved
   * @return The values
   * @throws RecordNotFoundException
   *           If the instrument configuration does not match the values
   * @throws DatabaseException
   *           If a database error occurs
   * @throws MissingParamException
   *           If any required parameters are missing
   */
  public static DatasetSensorValues getSensorValues(Connection conn,
    Instrument instrument, long datasetId, boolean ignoreFlushing)
    throws RecordNotFoundException, DatabaseException, MissingParamException {

    MissingParam.checkMissing(conn, "conn");
    MissingParam.checkZeroPositive(datasetId, "datasetId");

    DatasetSensorValues values = new DatasetSensorValues(instrument);

    String query = ignoreFlushing
      ? GET_SENSOR_VALUES_FOR_DATASET_NO_FLUSHING_QUERY
      : GET_SENSOR_VALUES_FOR_DATASET_QUERY;

    try (PreparedStatement stmt = conn.prepareStatement(query)) {

      stmt.setLong(1, datasetId);

      try (ResultSet records = stmt.executeQuery()) {

        while (records.next()) {
          values.add(sensorValueFromResultSet(records, datasetId));
        }
      }

    } catch (Exception e) {
      throw new DatabaseException("Error while retrieving sensor values", e);
    }

    return values;
  }

  /**
   * Build a SensorValue object from a ResultSet
   *
   * @param record
   *          The ResultSet
   * @param datasetId
   *          The ID of the value's parent dataset
   * @return The SensorValue
   * @throws SQLException
   *           If any values cannot be read
   * @throws InvalidFlagException
   *           If the stored Flag value is invalid
   */
  private static SensorValue sensorValueFromResultSet(ResultSet record,
    long datasetId) throws SQLException, InvalidFlagException {

    long valueId = record.getLong(1);
    long fileColumnId = record.getLong(2);
    LocalDateTime time = DateTimeUtils.longToDate(record.getLong(3));
    String value = record.getString(4);
    AutoQCResult autoQC = AutoQCResult.buildFromJson(record.getString(5));
    Flag userQCFlag = new Flag(record.getInt(6));
    String userQCMessage = record.getString(7);

    return new SensorValue(valueId, datasetId, fileColumnId, time, value,
      autoQC, userQCFlag, userQCMessage);
  }

  /**
   * Store a set of measurements in the database. The resulting database IDs are
   * added to the Measurement objects
   *
   * @param conn
   *          A database connection
   * @param measurements
   *          The measurements to be stored
   * @throws DatabaseException
   *           If a database error occurs
   * @throws MissingParamException
   *           If any required parameters are missing
   */
  public static void storeMeasurements(Connection conn,
    List<Measurement> measurements)
    throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(conn, "conn");
    MissingParam.checkMissing(measurements, "measurements");

    PreparedStatement stmt = null;
    ResultSet createdKeys = null;

    try {

      stmt = conn.prepareStatement(STORE_MEASUREMENT_STATEMENT,
        Statement.RETURN_GENERATED_KEYS);

      // Batch up all the measurements
      for (Measurement measurement : measurements) {
        stmt.setLong(1, measurement.getDatasetId());
        stmt.setLong(2, DateTimeUtils.dateToLong(measurement.getTime()));
        stmt.setString(3, measurement.getRunType());
        stmt.addBatch();
      }

      // Store them, and get the keys back
      stmt.executeBatch();
      createdKeys = stmt.getGeneratedKeys();
      int currentMeasurement = -1;
      while (createdKeys.next()) {
        currentMeasurement++;
        measurements.get(currentMeasurement)
          .setDatabaseId(createdKeys.getLong(1));
      }
    } catch (Exception e) {
      throw new DatabaseException("Error while storing measurements", e);
    } finally {
      DatabaseUtils.closeResultSets(createdKeys);
      DatabaseUtils.closeStatements(stmt);
    }
  }

  /**
   * Get the number of measurements in a dataset
   *
   * @param conn
   * @param datasetId
   * @return
   * @throws MissingParamException
   * @throws DatabaseException
   */
  public static int getRecordCount(Connection conn, long datasetId)
    throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(conn, "conn");
    MissingParam.checkZeroPositive(datasetId, "datasetId");

    int result = -1;

    PreparedStatement stmt = null;
    ResultSet count = null;

    try {
      stmt = conn.prepareStatement(GET_RECORD_COUNT_QUERY);
      stmt.setLong(1, datasetId);

      count = stmt.executeQuery();
      if (count.next()) {
        result = count.getInt(1);
      }
    } catch (SQLException e) {
      throw new DatabaseException("Error while getting measurement count", e);
    } finally {
      DatabaseUtils.closeResultSets(count);
      DatabaseUtils.closeStatements(stmt);
    }

    return result;
  }

  /**
   * Get the set of measurements for a dataset, grouped by run type and ordered
   * by date
   *
   * @param conn
   *          A database connection
   * @param instrument
   *          The instrument to which the dataset belongs
   * @param datasetId
   *          The database ID of the dataset
   * @return The measurements
   * @throws DatabaseException
   *           If a database error occurs
   * @throws MissingParamException
   *           If any required parameters are missing
   */
  public static Map<String, ArrayList<Measurement>> getMeasurementsByRunType(
    Connection conn, Instrument instrument, long datasetId)
    throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(conn, "conn");
    MissingParam.checkZeroPositive(datasetId, "datasetId");

    PreparedStatement stmt = null;
    ResultSet records = null;

    Map<String, ArrayList<Measurement>> measurements = new HashMap<String, ArrayList<Measurement>>();

    try {

      stmt = conn.prepareStatement(GET_MEASUREMENTS_QUERY);
      stmt.setLong(1, datasetId);

      records = stmt.executeQuery();
      while (records.next()) {
        long id = records.getLong(1);
        // We already have the dataset id
        LocalDateTime time = DateTimeUtils.longToDate(records.getLong(2));
        String runType = records.getString(3);

        if (!measurements.containsKey(runType)) {
          measurements.put(runType, new ArrayList<Measurement>());
        }

        measurements.get(runType)
          .add(new Measurement(id, datasetId, time, runType));
      }

    } catch (Exception e) {
      throw new DatabaseException("Error while retrieving measurements", e);
    } finally {
      DatabaseUtils.closeResultSets(records);
      DatabaseUtils.closeStatements(stmt);
    }

    return measurements;
  }

  /**
   * Get the set of measurements for a dataset ordered by date
   *
   * @param conn
   *          A database connection
   * @param instrument
   *          The instrument to which the dataset belongs
   * @param datasetId
   *          The database ID of the dataset
   * @return The measurements
   * @throws DatabaseException
   *           If a database error occurs
   * @throws MissingParamException
   *           If any required parameters are missing
   */
  public static List<Measurement> getMeasurements(Connection conn,
    long datasetId) throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(conn, "conn");
    MissingParam.checkZeroPositive(datasetId, "datasetId");

    PreparedStatement stmt = null;
    ResultSet records = null;

    List<Measurement> measurements = new ArrayList<Measurement>();

    try {

      stmt = conn.prepareStatement(GET_MEASUREMENTS_QUERY);
      stmt.setLong(1, datasetId);

      records = stmt.executeQuery();
      while (records.next()) {
        long id = records.getLong(1);
        // We already have the dataset id
        LocalDateTime time = DateTimeUtils.longToDate(records.getLong(2));
        String runType = records.getString(3);

        measurements.add(new Measurement(id, datasetId, time, runType));
      }

    } catch (Exception e) {
      throw new DatabaseException("Error while retrieving measurements", e);
    } finally {
      DatabaseUtils.closeResultSets(records);
      DatabaseUtils.closeStatements(stmt);
    }

    return measurements;
  }

  /**
   * Store the results of data reduction in the database
   *
   * @param conn
   *          A database connection
   * @param values
   *          The calculation values for the data reduction, as extracted from
   *          the sensor values
   * @param dataReductionRecords
   *          The data reduction calculations
   * @throws DatabaseException
   *           If the data cannot be stored
   */
  public static void storeDataReduction(Connection conn,
    List<DataReductionRecord> dataReductionRecords) throws DatabaseException {

    try (PreparedStatement dataReductionStmt = conn
      .prepareStatement(STORE_DATA_REDUCTION_STATEMENT)) {
      for (DataReductionRecord dataReduction : dataReductionRecords) {
        dataReductionStmt.setLong(1, dataReduction.getMeasurementId());
        dataReductionStmt.setLong(2, dataReduction.getVariableId());
        dataReductionStmt.setString(3, dataReduction.getCalculationJson());
        dataReductionStmt.setInt(4, dataReduction.getQCFlag().getFlagValue());
        dataReductionStmt.setString(5, StringUtils
          .collectionToDelimited(dataReduction.getQCMessages(), ";"));

        dataReductionStmt.addBatch();
      }

      dataReductionStmt.executeBatch();

    } catch (SQLException e) {
      throw new DatabaseException("Error while storing data reduction", e);
    }

  }

  /**
   * Remove all measurement details from a data set, ready for them to be
   * recalculated
   *
   * @param dataSource
   *          A data source
   * @param datasetId
   *          The database ID of the data set
   * @throws DatabaseException
   *           If a database error occurs
   * @throws MissingParamException
   *           If any required parameters are missing
   */
  public static void deleteMeasurements(DataSource dataSource, long datasetId)
    throws DatabaseException, MissingParamException {

    MissingParam.checkMissing(dataSource, "dataSource");
    MissingParam.checkZeroPositive(datasetId, "datasetId");

    Connection conn = null;

    try {
      conn = dataSource.getConnection();
      deleteMeasurements(conn, datasetId);
    } catch (SQLException e) {
      throw new DatabaseException("Error while deleting measurements", e);
    } finally {
      DatabaseUtils.closeConnection(conn);
    }
  }

  /**
   * Remove all measurement details from a data set, ready for them to be
   * recalculated
   *
   * @param dataSource
   *          A data source
   * @param datasetId
   *          The database ID of the data set
   * @throws DatabaseException
   *           If a database error occurs
   * @throws MissingParamException
   *           If any required parameters are missing
   */
  public static void deleteMeasurements(Connection conn, long datasetId)
    throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(conn, "conn");
    MissingParam.checkZeroPositive(datasetId, "datasetId");

    PreparedStatement delDataReductionStmt = null;
    PreparedStatement delMeasurementsStmt = null;

    try {
      conn.setAutoCommit(false);

      delDataReductionStmt = conn
        .prepareStatement(DELETE_DATA_REDUCTION_STATEMENT);
      delDataReductionStmt.setLong(1, datasetId);
      delDataReductionStmt.execute();

      delMeasurementsStmt = conn
        .prepareStatement(DELETE_MEASUREMENTS_STATEMENT);
      delMeasurementsStmt.setLong(1, datasetId);
      delMeasurementsStmt.execute();

      conn.commit();
    } catch (SQLException e) {
      throw new DatabaseException("Error while deleting measurements", e);
    } finally {
      DatabaseUtils.closeStatements(delMeasurementsStmt, delDataReductionStmt);
    }
  }

  /**
   * Get all the sensor values for the given columns that occur between the
   * specified dates (inclusive).
   *
   * @param conn
   *          A database connection
   * @param start
   *          The start date
   * @param end
   *          The end date
   * @param columnIds
   *          The column IDs
   * @return
   * @throws MissingParamException
   *           If any required parameters are missing
   * @throws DatabaseException
   *           If a database error occurs
   */
  public static List<SensorValue> getSensorValuesForColumns(Connection conn,
    long datasetId, List<Long> columnIds)
    throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(conn, "conn");
    MissingParam.checkPositive(datasetId, "datasetId");
    MissingParam.checkMissing(columnIds, "columnIds", false);

    List<SensorValue> result = new ArrayList<SensorValue>();

    String sql = DatabaseUtils.makeInStatementSql(
      GET_SENSOR_VALUES_FOR_COLUMNS_QUERY, columnIds.size());
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, datasetId);
      for (int i = 0; i < columnIds.size(); i++) {
        stmt.setLong(i + 2, columnIds.get(i));
      }

      try (ResultSet records = stmt.executeQuery()) {
        while (records.next()) {
          result.add(sensorValueFromResultSet(records, datasetId));
        }
      }

    } catch (SQLException | InvalidFlagException e) {
      throw new DatabaseException("Error getting sensor values", e);
    }

    return result;
  }

  /**
   * Get the unique list of dates for which sensor values have been recorded for
   * a given dataset. This ignores any values recorded during flushing times.
   *
   * @param dataSource
   *          A data source
   * @param datasetId
   *          The dataset's database ID
   * @return The sensor value dates
   * @throws MissingParamException
   *           If any required parameters are missing
   * @throws DatabaseException
   *           If a database error occurs
   */
  public static List<LocalDateTime> getSensorValueDates(DataSource dataSource,
    long datasetId) throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(dataSource, "dataSource");
    MissingParam.checkZeroPositive(datasetId, "dataSetId");

    List<LocalDateTime> times = new ArrayList<LocalDateTime>();

    try (Connection conn = dataSource.getConnection();
      PreparedStatement stmt = conn
        .prepareStatement(GET_SENSOR_VALUE_DATES_QUERY);) {

      stmt.setLong(1, datasetId);

      try (ResultSet records = stmt.executeQuery();) {
        while (records.next()) {
          times.add(DateTimeUtils.longToDate(records.getLong(1)));
        }
      }
    } catch (SQLException e) {
      throw new DatabaseException("Error while getting sensor value dates", e);
    }

    return times;
  }

  public static int getFlagsRequired(DataSource dataSource, long datasetId)
    throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(dataSource, "dataSource");
    MissingParam.checkZeroPositive(datasetId, "dataSetId");

    int result = 0;

    try (Connection conn = dataSource.getConnection();
      PreparedStatement stmt = conn
        .prepareStatement(GET_REQUIRED_FLAGS_QUERY);) {

      stmt.setLong(1, datasetId);

      try (ResultSet records = stmt.executeQuery();) {
        records.next();
        result = records.getInt(1);
      }
    } catch (SQLException e) {
      throw new DatabaseException("Error while getting flag info", e);
    }

    return result;
  }

  public static void setQC(DataSource dataSource, List<FieldValue> updateValues)
    throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(dataSource, "dataSource");
    MissingParam.checkMissing(updateValues, "updateValues", true);

    if (updateValues.size() > 0) {
      Connection conn = null;
      PreparedStatement stmt = null;

      try {

        conn = dataSource.getConnection();
        stmt = conn.prepareStatement(SET_QC_STATEMENT);

        for (FieldValue value : updateValues) {
          stmt.setInt(1, value.getQcFlag().getFlagValue());
          stmt.setString(2, value.getQcComment());
          stmt.setLong(3, value.getValueId());

          stmt.addBatch();
        }

        stmt.executeBatch();

      } catch (SQLException e) {
        throw new DatabaseException("Error updating QC values", e);
      } finally {
        DatabaseUtils.closeStatements(stmt);
        DatabaseUtils.closeConnection(conn);
      }
    }
  }

  @Deprecated
  public static void loadMeasurementData(DataSource dataSource,
    DatasetMeasurementData output, List<LocalDateTime> times)
    throws DatabaseException, MissingParamException, MeasurementDataException,
    RecordNotFoundException, RoutineException, InvalidFlagException {

    MissingParam.checkMissing(dataSource, "dataSource");
    MissingParam.checkMissing(output, "output");
    MissingParam.checkMissing(times, "times", true);

    if (times.size() > 0) {
      try (Connection conn = dataSource.getConnection();) {
        loadQCSensorValuesByTime(conn, output, times);
        loadDataReductionData(conn, output, times);
      } catch (Exception e) {
        throw new DatabaseException("Error while loading measurement data", e);
      }
    }
  }

  @Deprecated
  public static void loadQCSensorValuesByTime(DataSource dataSource,
    DatasetMeasurementData output, List<LocalDateTime> times)
    throws MissingParamException, DatabaseException, MeasurementDataException,
    RecordNotFoundException, RoutineException, InvalidFlagException {

    try (Connection conn = dataSource.getConnection()) {
      loadQCSensorValuesByTime(conn, output, times);
    } catch (SQLException e) {
      throw new DatabaseException("Error while getting sensor values", e);
    }
  }

  @Deprecated
  public static void loadQCSensorValuesByTime(Connection conn,
    DatasetMeasurementData output, List<LocalDateTime> times)
    throws MissingParamException, DatabaseException, MeasurementDataException,
    RecordNotFoundException, RoutineException, InvalidFlagException {

    MissingParam.checkMissing(conn, "conn");
    MissingParam.checkMissing(output, "output");
    MissingParam.checkMissing(times, "times", false);

    // Get the Run Type column IDs
    List<Long> runTypeColumns = output.getInstrument().getSensorAssignments()
      .getRunTypeColumnIDs();

    String sensorValuesSQL = DatabaseUtils
      .makeInStatementSql(GET_SENSOR_VALUES_BY_DATE_QUERY, times.size());

    try (PreparedStatement stmt = conn.prepareStatement(sensorValuesSQL)) {
      stmt.setLong(1, output.getDatasetId());

      // Add dates starting at parameter index 2
      for (int i = 0; i < times.size(); i++) {
        stmt.setLong(i + 2, DateTimeUtils.dateToLong(times.get(i)));
      }

      readQCSensorValues(output, stmt, runTypeColumns);

    } catch (SQLException e) {
      throw new DatabaseException("Error while getting sensor values", e);
    }
  }

  @Deprecated
  public static void loadQCSensorValuesByField(DataSource dataSource,
    DatasetMeasurementData output, List<Field> fields)
    throws MissingParamException, DatabaseException, MeasurementDataException,
    RecordNotFoundException, RoutineException, InvalidFlagException {

    try (Connection conn = dataSource.getConnection()) {
      loadQCSensorValuesByField(conn, output, fields);
    } catch (SQLException e) {
      throw new DatabaseException("Error while getting sensor values", e);
    }
  }

  @Deprecated
  public static void loadQCSensorValuesByField(Connection conn,
    DatasetMeasurementData output, List<Field> fields)
    throws MissingParamException, DatabaseException, MeasurementDataException,
    RecordNotFoundException, RoutineException, InvalidFlagException {

    MissingParam.checkMissing(conn, "conn");
    MissingParam.checkMissing(output, "output");
    MissingParam.checkMissing(fields, "fields", false);

    // Get the Run Type column IDs
    List<Long> runTypeColumns = output.getInstrument().getSensorAssignments()
      .getRunTypeColumnIDs();

    // Build the list of search columns. This is the provided list plus the run
    // type columns
    List<Long> searchFields = new ArrayList<Long>(runTypeColumns);
    fields.forEach(f -> searchFields.add(f.getId()));

    String sensorValuesSQL = DatabaseUtils.makeInStatementSql(
      GET_SENSOR_VALUES_BY_SENSOR_QUERY, searchFields.size());

    try (PreparedStatement stmt = conn.prepareStatement(sensorValuesSQL)) {
      stmt.setLong(1, output.getDatasetId());

      // Add dates starting at parameter index 2
      for (int i = 0; i < searchFields.size(); i++) {
        stmt.setLong(i + 2, searchFields.get(i));
      }

      readQCSensorValues(output, stmt, runTypeColumns);
    } catch (SQLException e) {
      throw new DatabaseException("Error while getting sensor values", e);
    }

  }

  @Deprecated
  private static void readQCSensorValues(DatasetMeasurementData output,
    PreparedStatement stmt, List<Long> runTypeColumns)
    throws SQLException, MissingParamException, MeasurementDataException,
    RecordNotFoundException, RoutineException, InvalidFlagException {

    try (ResultSet records = stmt.executeQuery()) {

      // We collect together all the sensor values for a given date. Then we
      // check them all together and add them to the output
      String currentRunType = null;
      LocalDateTime currentTime = LocalDateTime.MIN;
      Map<Field, FieldValue> currentDateValues = new HashMap<Field, FieldValue>();

      // Loop through all the sensor value records
      while (records.next()) {

        // Loop through all the sensor value records while (records.next()) {
        LocalDateTime time = DateTimeUtils.longToDate(records.getLong(3));

        // If the time has changed, process the current set of collected //
        // values
        if (!time.isEqual(currentTime)) {
          if (!currentTime.isEqual(LocalDateTime.MIN)) {
            output.filterAndAddValues(currentRunType, currentTime,
              currentDateValues);
          }

          currentTime = time;
          currentDateValues = new HashMap<Field, FieldValue>();
        }

        // Process the current record

        // See if this is a Run Type
        long fileColumn = records.getLong(2);
        if (runTypeColumns.contains(fileColumn)) {
          currentRunType = records.getString(4);
        } else {
          // This is a sensor value
          SensorType sensorType = output.getInstrument().getSensorAssignments()
            .getSensorTypeForDBColumn(fileColumn);

          FieldValue value = makeSensorFieldValue(records, sensorType);

          // We only need to bother adding the field if the output is expecting
          // to see it
          Field field = output.getFieldSets().getField(fileColumn);
          if (null != field) {
            currentDateValues.put(output.getFieldSets().getField(fileColumn),
              value);
          }
        }
      }

      // Store the last set of values
      output.filterAndAddValues(currentRunType, currentTime, currentDateValues);
    }
  }

  @Deprecated
  private static FieldValue makeSensorFieldValue(ResultSet record,
    SensorType sensorType)
    throws SQLException, RoutineException, InvalidFlagException {

    long valueId = record.getLong(1);
    long fileColumn = record.getLong(2);
    Double sensorValue = StringUtils.doubleFromString(record.getString(4));
    AutoQCResult autoQC = AutoQCResult.buildFromJson(record.getString(5));
    Flag userQCFlag = new Flag(record.getInt(6));
    String qcComment = record.getString(7);

    // See if this value has been used in the data set
    // Position and diagnostics are always marked as used
    // Get the measurement ID from the ResultSet. If it was null,
    // then it isn't used in the dataset for a measurement
    record.getLong(8);
    boolean used = !record.wasNull();

    boolean ghost = userQCFlag.equals(Flag.FLUSHING);

    // Position and diagnostics are always marked as used
    if (!used && fileColumn < 0 || sensorType.isDiagnostic()) {
      used = true;
    }

    return new FieldValue(valueId, sensorValue, autoQC, userQCFlag, qcComment,
      used, ghost);
  }

  @Deprecated
  private static void loadDataReductionData(Connection conn,
    DatasetMeasurementData output, List<LocalDateTime> times)
    throws MissingParamException, DatabaseException, InvalidFlagException,
    RoutineException, VariableNotFoundException, DataReductionException {

    SensorsConfiguration sensorConfig = ResourceManager.getInstance()
      .getSensorsConfiguration();

    try {
      String sensorValuesSQL = DatabaseUtils
        .makeInStatementSql(GET_DATA_REDUCTION_DATE_FILTER_QUERY, times.size());

      try (PreparedStatement sensorValuesStmt = conn
        .prepareStatement(sensorValuesSQL)) {

        sensorValuesStmt.setLong(1, output.getDatasetId());

        // Add dates starting at parameter index 2
        for (int i = 0; i < times.size(); i++) {
          sensorValuesStmt.setLong(i + 2,
            DateTimeUtils.dateToLong(times.get(i)));
        }

        try (ResultSet records = sensorValuesStmt.executeQuery();) {

          while (records.next()) {

            LocalDateTime time = DateTimeUtils.longToDate(records.getLong(1));
            long variableId = records.getLong(2);
            String valuesJson = records.getString(3);
            Flag qcFlag = new Flag(records.getInt(4));
            String qcComment = records.getString(5);

            Type mapType = new TypeToken<HashMap<String, Double>>() {
            }.getType();
            Map<String, Double> values = new Gson().fromJson(valuesJson,
              mapType);

            LinkedHashMap<String, Long> reductionParameters = DataReducerFactory
              .getCalculationParameters(
                sensorConfig.getInstrumentVariable(variableId));

            for (Map.Entry<String, Long> entry : reductionParameters
              .entrySet()) {

              FieldValue columnValue = new FieldValue(entry.getValue(),
                values.get(entry.getKey()), new AutoQCResult(), qcFlag,
                qcComment, true);

              output.addValue(time, reductionParameters.get(entry.getKey()),
                columnValue);
            }
          }
        }
      }
    } catch (SQLException e) {
      throw new DatabaseException("Error getting sensor data", e);
    }
  }

  public static Map<Long, Map<InstrumentVariable, DataReductionRecord>> getDataReductionData(
    Connection conn, Instrument instrument, DataSet dataSet)
    throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(conn, "conn");
    MissingParam.checkMissing(instrument, "instrument");
    MissingParam.checkMissing(dataSet, "dataSet");

    Map<Long, Map<InstrumentVariable, DataReductionRecord>> result = new HashMap<Long, Map<InstrumentVariable, DataReductionRecord>>();

    try (PreparedStatement stmt = conn
      .prepareStatement(GET_DATA_REDUCTION_QUERY)) {

      stmt.setLong(1, dataSet.getId());

      long currentMeasurement = -1L;
      try (ResultSet records = stmt.executeQuery()) {

        while (records.next()) {

          long measurementId = records.getLong(1);
          long variableId = records.getLong(2);

          String calculationValuesJson = records.getString(3);
          Type mapType = new TypeToken<HashMap<String, Double>>() {
          }.getType();
          Map<String, Double> calculationValues = new Gson()
            .fromJson(calculationValuesJson, mapType);

          Flag qcFlag = new Flag(records.getInt(4));
          String qcMessage = records.getString(5);

          DataReductionRecord record = ReadOnlyDataReductionRecord.makeRecord(
            measurementId, variableId, calculationValues, qcFlag, qcMessage);

          if (measurementId != currentMeasurement) {
            result.put(measurementId,
              new HashMap<InstrumentVariable, DataReductionRecord>());
            currentMeasurement = measurementId;
          }

          result.get(currentMeasurement).put(instrument.getVariable(variableId),
            record);

        }

      }

    } catch (Exception e) {
      throw new DatabaseException("Error while retrieving data reduction data",
        e);
    }

    return result;
  }

  @Deprecated
  public static void loadDataReductionData(DataSource dataSource,
    DatasetMeasurementData output, InstrumentVariable variable, Field field)
    throws MissingParamException, DatabaseException, InvalidFlagException {

    MissingParam.checkMissing(dataSource, "dataSource");
    MissingParam.checkMissing(output, "output");
    MissingParam.checkMissing(variable, "variable");
    MissingParam.checkMissing(field, "field");

    try (Connection conn = dataSource.getConnection();
      PreparedStatement stmt = conn
        .prepareStatement(GET_DATA_REDUCTION_QUERY_OLD)) {

      stmt.setLong(1, output.getDatasetId());
      stmt.setLong(2, variable.getId());

      try (ResultSet records = stmt.executeQuery()) {

        LinkedHashMap<String, Long> reductionParameters = DataReducerFactory
          .getCalculationParameters(variable);

        while (records.next()) {
          LocalDateTime time = DateTimeUtils.longToDate(records.getLong(1));
          String valuesJson = records.getString(2);
          Type mapType = new TypeToken<HashMap<String, Double>>() {
          }.getType();
          Map<String, Double> values = new Gson().fromJson(valuesJson, mapType);
          Flag qcFlag = new Flag(records.getInt(3));

          FieldValue value = new FieldValue(
            reductionParameters.get(field.getBaseName()),
            values.get(field.getBaseName()), new AutoQCResult(), qcFlag, null,
            true);

          output.addValue(time, field, value);
        }
      }
    } catch (Exception e) {
      throw new DatabaseException("Error getting data reduction data", e);
    }
  }

  public static Map<LocalDateTime, Long> getMeasurementTimes(Connection conn,
    long datasetId, List<String> runTypes)
    throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(conn, "conn");
    MissingParam.checkPositive(datasetId, "datasetId");
    MissingParam.checkMissing(runTypes, "runTypes", false);

    TreeMap<LocalDateTime, Long> result = new TreeMap<LocalDateTime, Long>();

    String sql = DatabaseUtils.makeInStatementSql(GET_MEASUREMENT_TIMES_QUERY,
      runTypes.size());

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setLong(1, datasetId);

      for (int i = 0; i < runTypes.size(); i++) {
        stmt.setString(i + 2, runTypes.get(i));
      }

      try (ResultSet records = stmt.executeQuery()) {
        while (records.next()) {
          long id = records.getLong(1);
          LocalDateTime date = DateTimeUtils.longToDate(records.getLong(2));

          result.put(date, id);
        }
      }

    } catch (SQLException e) {
      throw new DatabaseException("Error getting measurement times", e);
    }

    return result;
  }

  public static RunTypePeriods getRunTypePeriods(DataSource dataSource,
    Instrument instrument, DataSet dataSet, List<String> allowedRunTypes)
    throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(dataSource, "dataSource");
    MissingParam.checkMissing(instrument, "instrument");
    MissingParam.checkMissing(dataSet, "dataSet");
    MissingParam.checkMissing(allowedRunTypes, "allowedRunTypes", false);

    RunTypePeriods result = new RunTypePeriods();

    List<Long> runTypeColumnIds = instrument.getSensorAssignments()
      .getRunTypeColumnIDs();

    String sensorValuesSQL = DatabaseUtils
      .makeInStatementSql(GET_RUN_TYPES_QUERY, runTypeColumnIds.size());

    try (Connection conn = dataSource.getConnection();
      PreparedStatement stmt = conn.prepareStatement(sensorValuesSQL)) {

      stmt.setLong(1, dataSet.getId());

      int currentParam = 2;
      for (long column : runTypeColumnIds) {
        stmt.setLong(currentParam, column);
        currentParam++;
      }

      try (ResultSet records = stmt.executeQuery()) {
        while (records.next()) {
          result.add(records.getString(2),
            DateTimeUtils.longToDate(records.getLong(1)));
        }
      }
    } catch (SQLException e) {
      throw new DatabaseException("Error while getting run type periods", e);
    }

    return result;
  }

  public static void storeMeasurementValues(Connection conn,
    Collection<? extends Collection<MeasurementValue>> values)
    throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(conn, "conn");
    MissingParam.checkMissing(values, "values", false);

    try (PreparedStatement stmt = conn
      .prepareStatement(STORE_MEASUREMENT_VALUE_STATEMENT)) {

      for (Collection<MeasurementValue> outer : values) {

        for (MeasurementValue value : outer) {

          stmt.setLong(1, value.getMeasurementId());
          stmt.setLong(2, value.getColumnId());
          stmt.setLong(3, value.getPrior());
          if (null == value.getPost()) {
            stmt.setNull(4, Types.BIGINT);
          } else {
            stmt.setLong(4, value.getPost());
          }

          stmt.addBatch();
        }
      }

      stmt.executeBatch();
    } catch (SQLException e) {
      throw new DatabaseException("Error while storing measurement values", e);
    }
  }

  public static void deleteDataReduction(Connection conn, long datasetId)
    throws MissingParamException, DatabaseException {

    MissingParam.checkMissing(conn, "conn");
    MissingParam.checkPositive(datasetId, "datasetId");

    try (
      PreparedStatement mvStmt = conn
        .prepareStatement(DELETE_MEASUREMENT_VALUES_STATEMENT);

      PreparedStatement drStmt = conn
        .prepareStatement(DELETE_DATA_REDUCTION_STATEMENT);) {

      mvStmt.setLong(1, datasetId);
      mvStmt.execute();

      drStmt.setLong(1, datasetId);
      drStmt.execute();

    } catch (SQLException e) {
      throw new DatabaseException("Error while deleting measurement values", e);
    }
  }
}
