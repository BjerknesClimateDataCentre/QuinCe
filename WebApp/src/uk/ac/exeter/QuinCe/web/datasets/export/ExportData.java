package uk.ac.exeter.QuinCe.web.datasets.export;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.sql.DataSource;

import uk.ac.exeter.QuinCe.data.Dataset.DataSet;
import uk.ac.exeter.QuinCe.data.Dataset.DataSetDataDB;
import uk.ac.exeter.QuinCe.data.Dataset.DataReduction.CalculationParameter;
import uk.ac.exeter.QuinCe.data.Dataset.DataReduction.DataReducerFactory;
import uk.ac.exeter.QuinCe.data.Dataset.QC.Flag;
import uk.ac.exeter.QuinCe.data.Export.ColumnHeader;
import uk.ac.exeter.QuinCe.data.Export.ExportOption;
import uk.ac.exeter.QuinCe.data.Instrument.Instrument;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.InstrumentVariable;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.SensorAssignment;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.SensorAssignments;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.SensorType;
import uk.ac.exeter.QuinCe.utils.MissingParamException;
import uk.ac.exeter.QuinCe.utils.RecordNotFoundException;
import uk.ac.exeter.QuinCe.web.datasets.data.Field;
import uk.ac.exeter.QuinCe.web.datasets.data.FieldSet;
import uk.ac.exeter.QuinCe.web.datasets.data.FieldSets;
import uk.ac.exeter.QuinCe.web.datasets.data.FieldValue;
import uk.ac.exeter.QuinCe.web.datasets.data.MeasurementDataException;
import uk.ac.exeter.QuinCe.web.datasets.plotPage.ManualQC.ManualQCPageData;

@SuppressWarnings("serial")
public class ExportData extends ManualQCPageData {

  // TODO I don't like the way this is put together, mostly due to the problem
  // of using CombinedFieldValue objects and the related hoops in getting
  // the generics to work. Maybe the whole thing needs to be split out
  // into an interface at the top level?

  /**
   * Lookup table for sensor types using SensorType ID
   */
  private Map<Long, ExportField> sensorTypeFields = new HashMap<Long, ExportField>();

  /**
   * Lookup table for variable columns
   */
  private Map<Long, ExportField> variableFields = new HashMap<Long, ExportField>();

  /**
   * The fixed Depth field & value
   */
  // TODO Replace this. See issue #1284

  private ExportField depthField;

  private FieldValue depthFieldValue;

  public ExportData(DataSource dataSource, Instrument instrument,
    DataSet dataSet, ExportOption exportOption) throws Exception {

    super(instrument, new FieldSets(), dataSet);
    buildFieldSets(dataSource, instrument, exportOption);

    depthFieldValue = new FieldValue(-1L, new Double(instrument.getDepth()),
      Flag.GOOD, false, "", false);
  }

  /**
   * Create the field sets for the data
   *
   * @param dataSource
   * @param instrument
   * @param exportOption
   * @return
   * @throws Exception
   */
  private void buildFieldSets(DataSource dataSource, Instrument instrument,
    ExportOption exportOption) throws Exception {

    ExportField lonField = new ExportField(FieldSet.BASE_FIELD_SET,
      SensorType.LONGITUDE_ID, SensorType.LONGITUDE_SENSOR_TYPE, false, false,
      exportOption);
    fieldSets.addField(lonField);
    sensorTypeFields.put(SensorType.LONGITUDE_SENSOR_TYPE.getId(), lonField);

    ExportField latField = new ExportField(FieldSet.BASE_FIELD_SET,
      SensorType.LATITUDE_ID, SensorType.LATITUDE_SENSOR_TYPE, false, false,
      exportOption);
    fieldSets.addField(latField);
    sensorTypeFields.put(SensorType.LATITUDE_SENSOR_TYPE.getId(), latField);

    // TODO Depth is fixed for now. Will fix this when variable parameter
    // support is fixed
    // (Issue #1284)
    ColumnHeader depthHeader = new ColumnHeader("Depth", "ADEPZZ01", "m");
    depthField = new ExportField(FieldSet.BASE_FIELD_SET,
      depthHeader.hashCode(), depthHeader, false, false, exportOption);
    fieldSets.addField(depthField);

    // Sensors
    SensorAssignments sensors = instrument.getSensorAssignments();
    List<InstrumentVariable> variables = instrument.getVariables();

    // For sensors, the fields are each sensor type.
    Map<SensorType, Long> exportSensorTypes = new HashMap<SensorType, Long>();

    if (exportOption.includeAllSensors()) {
      for (Map.Entry<SensorType, List<SensorAssignment>> entry : sensors
        .entrySet()) {
        if (entry.getValue().size() > 0) {
          exportSensorTypes.put(entry.getKey(),
            entry.getValue().get(0).getDatabaseId());
        }
      }
    } else {
      // Only use sensor types required by the instrument's variables
      for (InstrumentVariable variable : variables) {
        for (SensorType sensorType : variable.getAllSensorTypes()) {
          exportSensorTypes.put(sensorType,
            sensors.get(sensorType).get(0).getDatabaseId());
        }
      }
    }

    FieldSet sensorsFieldSet = fieldSets.addFieldSet(
      DataSetDataDB.SENSORS_FIELDSET, DataSetDataDB.SENSORS_FIELDSET_NAME);

    for (Map.Entry<SensorType, Long> entry : exportSensorTypes.entrySet()) {
      if (!entry.getKey().equals(SensorType.RUN_TYPE_SENSOR_TYPE)) {
        SensorType sensorType = entry.getKey();
        ExportField exportField = new ExportField(sensorsFieldSet,
          entry.getValue(), sensorType, sensorType.isDiagnostic(),
          !sensorType.isDiagnostic(), exportOption);

        fieldSets.addField(exportField);
        sensorTypeFields.put(sensorType.getId(), exportField);
      }
    }

    // Now the fields for each variable
    for (InstrumentVariable variable : variables) {

      FieldSet varFieldSet = fieldSets.addFieldSet(variable.getId(),
        variable.getName());
      TreeMap<Long, CalculationParameter> parameters = DataReducerFactory
        .getCalculationParameters(variable, true);

      for (Map.Entry<Long, CalculationParameter> entry : parameters
        .entrySet()) {

        ExportField field = new ExportField(varFieldSet, entry.getKey(),
          entry.getValue().getColumnHeader(), !entry.getValue().isResult(),
          entry.getValue().isResult(), exportOption);

        fieldSets.addField(field, (!exportOption.includeCalculationColumns()
          && !entry.getValue().isResult()));
        variableFields.put(entry.getKey(), field);
      }
    }
  }

  @Override
  public void filterAndAddValues(String runType, LocalDateTime time,
    Map<Field, FieldValue> values)
    throws MeasurementDataException, MissingParamException {

    try {
      Map<Field, CombinedFieldValue> valuesToAdd = new HashMap<Field, CombinedFieldValue>();

      for (Map.Entry<Field, FieldValue> entry : values.entrySet()) {

        SensorType sensorType = instrument.getSensorAssignments()
          .getSensorTypeForDBColumn(entry.getKey().getId());
        if (sensorTypeFields.containsKey(sensorType.getId())) {

          Field field = sensorTypeFields.get(sensorType.getId());

          // We don't keep internal calibration values
          if (!sensorType.hasInternalCalibration()
            || measurementRunTypes.contains(runType)) {
            valuesToAdd.put(field,
              addSensorValue(valuesToAdd.get(field), entry.getValue()));
          }
        }
      }

      addValues(time, valuesToAdd);
    } catch (RecordNotFoundException e) {
      throw new MeasurementDataException("Failed to look up sensor type", e);
    }
  }

  private CombinedFieldValue addSensorValue(CombinedFieldValue existingValue,
    FieldValue value) {

    CombinedFieldValue result = existingValue;

    // Combine all values for a given sensor type
    if (null == existingValue) {
      result = new CombinedFieldValue(value);
    } else {
      result.addValue(value);
    }

    return result;
  }

  @Override
  public void addValues(LocalDateTime rowId,
    Map<Field, ? extends FieldValue> values) throws MissingParamException {
    super.addValues(rowId, values);

    // Add the DEPTH attribute
    addValue(rowId, depthField, depthFieldValue);
  }

  /**
   * Post-process the data before it's finally exported. This instance does no
   * post-processing, but extending classes can override it as needed.
   */
  public void postProcess() {
    // NOOP
  }
}
