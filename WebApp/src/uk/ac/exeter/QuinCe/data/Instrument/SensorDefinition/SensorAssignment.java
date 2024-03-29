package uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition;

import uk.ac.exeter.QuinCe.data.Dataset.ColumnHeading;
import uk.ac.exeter.QuinCe.utils.DatabaseUtils;

/**
 * Records the data file and column number that have been assigned a particular
 * sensor role
 */
public class SensorAssignment implements Comparable<SensorAssignment> {

  /**
   * The database ID of this sensor assignment
   */
  private long databaseId = DatabaseUtils.NO_DATABASE_RECORD;

  /**
   * The data file
   */
  private String dataFile;

  /**
   * The column number (zero-based)
   */
  private final int column;

  /**
   * The {@link SensorType} of this assignment
   */
  private final SensorType sensorType;

  /**
   * The name of the sensor
   */
  private final String sensorName;

  /**
   * The answer to the Depends Question
   *
   * @see SensorType#getDependsQuestion
   */
  private boolean dependsQuestionAnswer = false;

  /**
   * Indicates whether this is a primary or fallback sensor
   */
  private boolean primary = true;

  /**
   * The String that indicates a missing value
   */
  private String missingValue = null;

  /**
   * Simple constructor
   *
   * @param dataFile
   *          The data file
   * @param column
   *          The column number
   * @param sensorName
   *          The name of the sensor
   * @param postCalibrated
   *          Specifies whether or not values should be calibrated by QuinCe
   * @param primary
   *          Specifies whether this is a primary or fallback sensor
   * @param dependsQuestionAnswer
   *          The answer to the Depends Question
   * @param missingValue
   *          The missing value String
   * @throws SensorAssignmentException
   */
  public SensorAssignment(String dataFile, int column, SensorType sensorType,
    String sensorName, boolean primary, boolean dependsQuestionAnswer,
    String missingValue) throws SensorAssignmentException {

    this.dataFile = dataFile;
    this.column = column;

    if (null == sensorType) {
      throw new SensorAssignmentException("SensorType cannot be null");
    }
    this.sensorType = sensorType;
    this.sensorName = sensorName;
    this.primary = primary;
    this.dependsQuestionAnswer = dependsQuestionAnswer;
    setMissingValue(missingValue);
  }

  /**
   * Simple constructor
   *
   * @param databaseId
   *          The assignment's datbaase ID
   * @param dataFile
   *          The data file
   * @param fileColumn
   *          The column number in the file
   * @param databaseColumn
   *          The column where the sensor's data will be stored in the database
   * @param sensorName
   *          The name of the sensor
   * @param postCalibrated
   *          Specifies whether or not values should be calibrated by QuinCe
   * @param primary
   *          Specifies whether this is a primary or fallback sensor
   * @param dependsQuestionAnswer
   *          The answer to the Depends Question
   * @param missingValue
   *          The missing value String
   */
  public SensorAssignment(long databaseId, String dataFile, int fileColumn,
    SensorType sensorType, String sensorName, boolean primary,
    boolean dependsQuestionAnswer, String missingValue) {

    this.databaseId = databaseId;
    this.dataFile = dataFile;
    this.column = fileColumn;
    this.sensorType = sensorType;
    this.sensorName = sensorName;
    this.primary = primary;
    this.dependsQuestionAnswer = dependsQuestionAnswer;
    setMissingValue(missingValue);
  }

  /**
   * Get the database ID of this sensor assignment
   *
   * @return The assignment's database ID
   */
  public long getDatabaseId() {
    return databaseId;
  }

  /**
   * Set the database ID of this assignment
   *
   * @param databaseId
   *          The database ID
   */
  public void setDatabaseId(long databaseId) {
    this.databaseId = databaseId;
  }

  /**
   * Get the data file
   *
   * @return The data file
   */
  public String getDataFile() {
    return dataFile;
  }

  /**
   * Get the column number
   *
   * @return The column number
   */
  public int getColumn() {
    return column;
  }

  /**
   * Get the name of the sensor
   *
   * @return The sensor name
   */
  public String getSensorName() {
    String result = "";

    if (null != sensorName) {
      result = sensorName;
    }

    return result;
  }

  /**
   * Set the answer to the Depends Question
   *
   * @param dependsQuestionAnswer
   *          The answer
   * @see SensorType#getDependsQuestion()
   */
  public void setDependsQuestionAnswer(boolean dependsQuestionAnswer) {
    this.dependsQuestionAnswer = dependsQuestionAnswer;
  }

  /**
   * Get the answer to the Depends Question
   *
   * @return The answer
   * @see SensorType#getDependsQuestion()
   */
  public boolean getDependsQuestionAnswer() {
    return dependsQuestionAnswer;
  }

  /**
   * Determines whether or not this is a primary sensor
   *
   * @return {@code true} if this is a primary sensor; {@code false} if it is a
   *         fallback sensor
   */
  public boolean isPrimary() {
    return primary;
  }

  /**
   * Get the missing value String
   *
   * @return The missing value String
   */
  public String getMissingValue() {
    return missingValue;
  }

  /**
   * Set the missing value String
   *
   * @param missingValue
   *          The missing value String
   */
  public void setMissingValue(String missingValue) {
    if (null == missingValue) {
      this.missingValue = "";
    } else {
      this.missingValue = missingValue;
    }
  }

  /**
   * Get the human-readable string describing an assignment to a data file and
   * named sensor
   *
   * @param dataFile
   *          The data file name
   * @param sensorName
   *          The sensor name
   * @return The description
   */
  public static String getTarget(String dataFile, String sensorName) {
    return dataFile + ": " + sensorName;
  }

  /**
   * Get the human-readable string describing this assignment
   *
   * @return The assignment description
   */
  public String getTarget() {
    return getTarget(getDataFile(), getSensorName());
  }

  @Override
  public String toString() {
    return "ID " + databaseId + ": " + sensorName;
  }

  public ColumnHeading getColumnHeading() {
    return new ColumnHeading(databaseId, sensorName, sensorType.getLongName(),
      sensorType.getCodeName(), sensorType.getUnits(), true, false);
  }

  public SensorType getSensorType() {
    return sensorType;
  }

  protected void setDataFile(String dataFile) {
    this.dataFile = dataFile;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + column;
    result = prime * result + ((dataFile == null) ? 0 : dataFile.hashCode());
    result = prime * result
      + ((sensorType == null) ? 0 : sensorType.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    SensorAssignment other = (SensorAssignment) obj;
    if (column != other.column)
      return false;
    if (dataFile == null) {
      if (other.dataFile != null)
        return false;
    } else if (!dataFile.equals(other.dataFile))
      return false;
    if (sensorType == null) {
      if (other.sensorType != null)
        return false;
    } else if (!sensorType.equals(other.sensorType))
      return false;
    return true;
  }

  @Override
  public int compareTo(SensorAssignment o) {
    int result = dataFile.compareTo(o.dataFile);
    if (result == 0) {
      result = column - o.column;
    }
    if (result == 0) {
      result = sensorName.compareTo(o.sensorName);
    }
    return result;
  }

  /**
   * Determines whether or not this assignment is from the same file and column
   * as the specified assignment.
   *
   * @param o
   *          The assignment to be compared.
   * @return {@code true} if the assignment is from the same file and column;
   *         {@code false} otherwise
   */
  public boolean matches(SensorAssignment o) {
    return dataFile.equals(o.dataFile) && column == o.column;
  }
}
