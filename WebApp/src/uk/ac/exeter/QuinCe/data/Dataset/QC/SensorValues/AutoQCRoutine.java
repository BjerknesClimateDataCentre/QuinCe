package uk.ac.exeter.QuinCe.data.Dataset.QC.SensorValues;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import uk.ac.exeter.QuinCe.data.Dataset.RunTypePeriod;
import uk.ac.exeter.QuinCe.data.Dataset.RunTypePeriods;
import uk.ac.exeter.QuinCe.data.Dataset.SensorValue;
import uk.ac.exeter.QuinCe.data.Dataset.QC.Flag;
import uk.ac.exeter.QuinCe.data.Dataset.QC.RoutineException;
import uk.ac.exeter.QuinCe.data.Dataset.QC.RoutineFlag;
import uk.ac.exeter.QuinCe.utils.RecordNotFoundException;
import uk.ac.exeter.QuinCe.web.system.ResourceManager;

/**
 * The base class for a QC routine. These classes will be called to check the
 * data after it's been read and processed for missing/ out of range values.
 */
public abstract class AutoQCRoutine extends AbstractAutoQCRoutine {

  /**
   * Basic constructor
   *
   * @param parameters
   *          The parameters
   * @throws RoutineException
   *           If the parameters are invalid
   */
  protected AutoQCRoutine() {
  }

  protected void setParameters(List<String> parameters)
    throws RoutineException {
    this.parameters = parameters;
    validateParameters();
  }

  /**
   * Add a QC flag to a value. The QC message is generated by calling
   * {@link #getShortMessage()} or {@link #getShortMessage()}.
   *
   * @param value
   *          The value
   * @param flag
   *          The flag
   */
  protected void addFlag(SensorValue value, Flag flag, String requiredValue,
    String actualValue) throws RoutineException {

    try {
      value
        .addAutoQCFlag(new RoutineFlag(this, flag, requiredValue, actualValue));
    } catch (RecordNotFoundException e) {
      throw new RoutineException("Sensor Value ID is not stored in database");
    }
  }

  /**
   * Add a QC flag to a value. The QC message is generated by calling
   * {@link #getShortMessage()} or {@link #getShortMessage()}.
   *
   * @param value
   *          The value
   * @param flag
   *          The flag
   */
  protected void addFlag(SensorValue value, Flag flag, Double requiredValue,
    Double actualValue) throws RoutineException {
    addFlag(value, flag, String.valueOf(requiredValue),
      String.valueOf(actualValue));
  }

  /**
   * Add a QC flag to a value. The QC message is generated by calling
   * {@link #getShortMessage()} or {@link #getShortMessage()}.
   *
   * @param value
   *          The value
   * @param flag
   *          The flag
   */
  protected void addFlag(SensorValue value, Flag flag, Double requiredValue,
    long actualValue) throws RoutineException {
    addFlag(value, flag, String.valueOf(requiredValue),
      String.valueOf(actualValue));
  }

  /**
   * Add a QC flag to a value. The QC message is generated by calling
   * {@link #getShortMessage()} or {@link #getShortMessage()}.
   *
   * @param value
   *          The value
   * @param flag
   *          The flag
   */
  protected void addFlag(SensorValue value, Flag flag, String requiredValue,
    Double actualValue) throws RoutineException {
    addFlag(value, flag, requiredValue, String.valueOf(actualValue));
  }

  /**
   * Validate the parameters
   *
   * @throws RoutineException
   *           If the parameters are invalid
   */
  protected abstract void validateParameters() throws RoutineException;

  /**
   * Perform the QC on the specified values.
   *
   * <p>
   * The method ensures that the Routine is correctly configured and then calls
   * {@link #qcAction(List)} to perform the actual QC.
   * </p>
   *
   * @param values
   *          The values to be QCed.
   * @throws RoutineException
   *           If the Routine is not configured correctly, or fails during
   *           processing.
   */
  public void qc(List<SensorValue> values, RunTypePeriods runTypePeriods)
    throws RoutineException {
    checkSetup();

    if (!sensorType.isRunTypeAware()) {
      qcAction(values);
    } else {
      Map<String, List<SensorValue>> valuesByRunType = getValuesByRunType(
        values, runTypePeriods);
      for (List<SensorValue> valuesGroup : valuesByRunType.values()) {
        qcAction(valuesGroup);
      }
    }
  }

  private Map<String, List<SensorValue>> getValuesByRunType(
    List<SensorValue> values, RunTypePeriods runTypePeriods) {

    Map<String, List<SensorValue>> result = new HashMap<String, List<SensorValue>>();
    runTypePeriods.getRunTypeNames()
      .forEach(r -> result.put(r, new ArrayList<SensorValue>()));

    int currentPeriodIndex = 0;
    RunTypePeriod currentPeriod = runTypePeriods.get(currentPeriodIndex);

    for (SensorValue value : values) {
      while (!currentPeriod.encompasses(value.getTime())) {
        currentPeriodIndex++;
        currentPeriod = runTypePeriods.get(currentPeriodIndex);
      }

      result.get(currentPeriod.getRunType()).add(value);
    }

    return result;
  }

  /**
   * Perform the QC
   *
   * @param values
   *          The values to be QCed
   */
  protected abstract void qcAction(List<SensorValue> values)
    throws RoutineException;

  /**
   * Filter a list of {@link SensorValue} objects to remove any NaN values.
   *
   * @param values
   *          The values to be filtered.
   * @return The filtered list.
   */
  protected List<SensorValue> filterMissingValues(List<SensorValue> values) {
    return values.stream().filter(x -> !x.isNaN()).collect(Collectors.toList());
  }

  public abstract String getShortMessage();

  public abstract String getLongMessage(RoutineFlag flag);

  @Override
  public String getName() {
    return ResourceManager.getInstance().getQCRoutinesConfiguration()
      .getRoutineName(this);
  }
}
