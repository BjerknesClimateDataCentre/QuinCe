package uk.ac.exeter.QuinCe.data.Dataset.QC.DataReduction;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

import uk.ac.exeter.QuinCe.data.Dataset.DatasetSensorValues;
import uk.ac.exeter.QuinCe.data.Dataset.Measurement;
import uk.ac.exeter.QuinCe.data.Dataset.MeasurementValue;
import uk.ac.exeter.QuinCe.data.Dataset.SensorValue;
import uk.ac.exeter.QuinCe.data.Dataset.DataReduction.ReadOnlyDataReductionRecord;
import uk.ac.exeter.QuinCe.data.Dataset.QC.Routine;
import uk.ac.exeter.QuinCe.data.Dataset.QC.RoutineException;
import uk.ac.exeter.QuinCe.data.Dataset.QC.RoutineFlag;
import uk.ac.exeter.QuinCe.data.Dataset.QC.SensorValues.FlaggedItems;
import uk.ac.exeter.QuinCe.data.Instrument.Instrument;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.SensorType;

/**
 * Base class for QC routines to be run during data reduction
 *
 * @author Steve Jones
 *
 */
public abstract class DataReductionQCRoutine implements Routine {

  /**
   * The routine settings
   */
  protected DataReductionQCRoutineSettings settings = null;

  /**
   * Basic constructor.
   *
   * @param settings
   *          The routine settings
   */
  protected DataReductionQCRoutine() {
    super();
  }

  protected void applySettings(DataReductionQCRoutineSettings settings) {
    this.settings = settings;
  }

  protected void flagSensors(Instrument instrument, Measurement measurement,
    ReadOnlyDataReductionRecord dataReductionRecord,
    DatasetSensorValues allSensorValues, RoutineFlag flag,
    FlaggedItems flaggedItems) throws RoutineException {

    try {

      List<SensorValue> valuesToFlag = new ArrayList<SensorValue>();

      for (SensorType sensorType : measurement
        .getMeasurementValueSensorTypes()) {

        MeasurementValue measurementValue = measurement
          .getMeasurementValue(sensorType);

        valuesToFlag.addAll(measurementValue.getSensorValueIds().stream()
          .map(allSensorValues::getById).collect(Collectors.toList()));
      }

      if (SensorValue.allUserQCNeeded(valuesToFlag)) {

        for (SensorValue value : valuesToFlag) {
          value.addAutoQCFlag(flag);
          flaggedItems.add(value);
        }

        dataReductionRecord.setQc(flag, getShortMessage());
        flaggedItems.add(dataReductionRecord);
      }
    } catch (Exception e) {
      throw new RoutineException("Error while setting QC flags", e);
    }
  }

  public void qc(Instrument instrument,
    TreeMap<Measurement, ReadOnlyDataReductionRecord> dataReductionRecords,
    DatasetSensorValues allSensorValues, FlaggedItems flaggedItems)
    throws RoutineException {

    if (null == settings) {
      throw new RoutineException("Settings not initialised");
    }

    qcAction(instrument, dataReductionRecords, allSensorValues, flaggedItems);
  }

  protected abstract void qcAction(Instrument instrument,
    TreeMap<Measurement, ReadOnlyDataReductionRecord> dataReductionRecords,
    DatasetSensorValues allSensorValues, FlaggedItems flaggedItems)
    throws RoutineException;

  @Override
  public String toString() {
    return this.getClass().getSimpleName();
  }

  @Override
  public String getName() {
    return DataReductionQCRoutinesConfiguration.getRoutineName(this);
  }
}
