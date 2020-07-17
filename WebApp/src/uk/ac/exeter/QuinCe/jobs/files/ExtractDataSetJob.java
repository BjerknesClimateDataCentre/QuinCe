package uk.ac.exeter.QuinCe.jobs.files;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeSet;

import org.apache.commons.lang3.exception.ExceptionUtils;

import uk.ac.exeter.QuinCe.data.Dataset.DataSet;
import uk.ac.exeter.QuinCe.data.Dataset.DataSetDB;
import uk.ac.exeter.QuinCe.data.Dataset.DataSetDataDB;
import uk.ac.exeter.QuinCe.data.Dataset.InvalidDataSetStatusException;
import uk.ac.exeter.QuinCe.data.Dataset.RunTypePeriod;
import uk.ac.exeter.QuinCe.data.Dataset.RunTypePeriods;
import uk.ac.exeter.QuinCe.data.Dataset.SensorValue;
import uk.ac.exeter.QuinCe.data.Dataset.QC.Flag;
import uk.ac.exeter.QuinCe.data.Files.DataFile;
import uk.ac.exeter.QuinCe.data.Files.DataFileDB;
import uk.ac.exeter.QuinCe.data.Instrument.FileDefinition;
import uk.ac.exeter.QuinCe.data.Instrument.Instrument;
import uk.ac.exeter.QuinCe.data.Instrument.Calibration.Calibration;
import uk.ac.exeter.QuinCe.data.Instrument.Calibration.CalibrationSet;
import uk.ac.exeter.QuinCe.data.Instrument.Calibration.SensorCalibrationDB;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.SensorAssignment;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.SensorType;
import uk.ac.exeter.QuinCe.jobs.InvalidJobParametersException;
import uk.ac.exeter.QuinCe.jobs.JobFailedException;
import uk.ac.exeter.QuinCe.jobs.JobManager;
import uk.ac.exeter.QuinCe.jobs.JobThread;
import uk.ac.exeter.QuinCe.utils.DatabaseException;
import uk.ac.exeter.QuinCe.utils.DatabaseUtils;
import uk.ac.exeter.QuinCe.utils.DateTimeUtils;
import uk.ac.exeter.QuinCe.utils.MissingParamException;
import uk.ac.exeter.QuinCe.utils.RecordNotFoundException;
import uk.ac.exeter.QuinCe.web.system.ResourceManager;

/**
 * Job to extract the data for a data set from the uploaded data files
 *
 * @author Steve Jones
 *
 */
public class ExtractDataSetJob extends DataSetJob {

  /**
   * Name of the job, used for reporting
   */
  private final String jobName = "Dataset Extraction";

  /**
   * Initialise the job object so it is ready to run
   *
   * @param resourceManager
   *          The system resource manager
   * @param config
   *          The application configuration
   * @param jobId
   *          The id of the job in the database
   * @param parameters
   *          The job parameters, containing the file ID
   * @throws InvalidJobParametersException
   *           If the parameters are not valid for the job
   * @throws MissingParamException
   *           If any of the parameters are invalid
   * @throws RecordNotFoundException
   *           If the job record cannot be found in the database
   * @throws DatabaseException
   *           If a database error occurs
   */
  public ExtractDataSetJob(ResourceManager resourceManager, Properties config,
    long jobId, Map<String, String> parameters) throws MissingParamException,
    InvalidJobParametersException, DatabaseException, RecordNotFoundException {
    super(resourceManager, config, jobId, parameters);
  }

  @Override
  protected void execute(JobThread thread) throws JobFailedException {

    Connection conn = null;

    try {

      conn = dataSource.getConnection();
      reset(conn);

      conn.setAutoCommit(false);

      // Get the new data set from the database
      DataSet dataSet = getDataset(conn);
      dataSet.setStatus(DataSet.STATUS_DATA_EXTRACTION);
      DataSetDB.updateDataSet(conn, dataSet);

      Instrument instrument = getInstrument(conn);

      // Delete any existing NRT dataset, unless we're processing it.
      // The odds are that the new dataset will replace it
      if (instrument.getNrt() && !dataSet.isNrt()) {
        DataSetDB.deleteNrtDataSet(conn, dataSet.getInstrumentId());
      }

      // Reset the data set and all associated data
      reset(conn);
      conn.commit();

      List<DataFile> files = DataFileDB.getDataFiles(conn,
        ResourceManager.getInstance().getConfig(),
        dataSet.getSourceFiles(conn));

      TreeSet<SensorValue> sensorValues = new TreeSet<SensorValue>();

      // We want to store when run types begin and end
      RunTypePeriods runTypePeriods = new RunTypePeriods();

      CalibrationSet sensorCalibrations = SensorCalibrationDB.getInstance()
        .getMostRecentCalibrations(conn, instrument.getDatabaseId(),
          dataSet.getStart());

      // Collect the true start and end times of the dataset based on the
      // actual data
      LocalDateTime realStartTime = null;
      LocalDateTime realEndTime = dataSet.getEnd();

      // Collect the data bounds
      double minLon = Double.MAX_VALUE;
      double maxLon = -Double.MAX_VALUE;
      double minLat = Double.MAX_VALUE;
      double maxLat = -Double.MAX_VALUE;

      for (DataFile file : files) {
        FileDefinition fileDefinition = file.getFileDefinition();

        int currentLine = file.getFirstDataLine();
        while (currentLine < file.getContentLineCount()) {

          List<String> line = file.getLine(currentLine);
          LocalDateTime time = file.getDate(line);

          if ((time.equals(dataSet.getStart())
            || time.isAfter(dataSet.getStart()))
            && (time.isBefore(dataSet.getEnd())
              || time.isEqual(dataSet.getEnd()))) {

            if (null == realStartTime && null != time) {
              realStartTime = time;
            }

            realEndTime = time;

            if (null != fileDefinition.getLongitudeSpecification()) {

              String longitude = file.getLongitude(line);

              sensorValues.add(new SensorValue(dataSet.getId(),
                FileDefinition.LONGITUDE_COLUMN_ID, time, longitude));

              if (null != longitude) {
                try {
                  double lonDouble = Double.parseDouble(longitude);
                  if (lonDouble < minLon) {
                    minLon = lonDouble;
                  }

                  if (lonDouble > maxLon) {
                    maxLon = lonDouble;
                  }
                } catch (NumberFormatException e) {
                  // Ignore it now. QC will pick it up later.
                }
              }
            }

            if (null != fileDefinition.getLatitudeSpecification()) {

              String latitude = file.getLatitude(line);

              sensorValues.add(new SensorValue(dataSet.getId(),
                FileDefinition.LATITUDE_COLUMN_ID, time, latitude));

              if (null != latitude) {
                try {
                  double latDouble = Double.parseDouble(latitude);
                  if (latDouble < minLat) {
                    minLat = latDouble;
                  }

                  if (latDouble > maxLat) {
                    maxLat = latDouble;
                  }
                } catch (NumberFormatException e) {
                  // Ignore it now. QC will pick it up later.
                }
              }
            }

            // Assigned columns
            for (Entry<SensorType, List<SensorAssignment>> entry : instrument
              .getSensorAssignments().entrySet()) {

              for (SensorAssignment assignment : entry.getValue()) {
                if (assignment.getDataFile()
                  .equals(fileDefinition.getFileDescription())) {

                  // For run types, follow all aliases
                  if (entry.getKey().equals(SensorType.RUN_TYPE_SENSOR_TYPE)) {
                    String runType = file.getFileDefinition()
                      .getRunType(line, true).getRunName();

                    sensorValues.add(new SensorValue(dataSet.getId(),
                      assignment.getDatabaseId(), time, runType));

                    runTypePeriods.add(runType, time);
                  } else {

                    // Create the SensorValue object
                    SensorValue value = new SensorValue(dataSet.getId(),
                      assignment.getDatabaseId(), time,
                      file.getStringValue(line, assignment.getColumn(),
                        assignment.getMissingValue()));

                    // Apply calibration if required
                    Calibration sensorCalibration = sensorCalibrations
                      .getTargetCalibration(
                        String.valueOf(assignment.getDatabaseId()));

                    if (null != sensorCalibration) {
                      value.calibrateValue(sensorCalibration);
                    }

                    // Add to storage list
                    sensorValues.add(value);
                  }
                }
              }
            }
          }

          currentLine++;
        }
      }

      // The last run type will cover the rest of time
      runTypePeriods.finish();

      // Now flag all the values that are within the instrument's pre-
      // and post-flushing periods (if they're defined)
      if (instrument.getPreFlushingTime() > 0
        || instrument.getPostFlushingTime() > 0) {

        RunTypePeriod currentPeriod = runTypePeriods.get(0);
        int currentPeriodIndex = 0;

        Iterator<SensorValue> valuesIter = sensorValues.iterator();
        while (valuesIter.hasNext()) {
          SensorValue value = valuesIter.next();

          boolean periodFound = false;

          // Make sure we have the correct run type period
          while (!periodFound) {

            // If we have multiple file definitions, it's possible that
            // timestamps
            // in the file where the run type *isn't* defined will fall between
            // run types.
            //
            // In this case, simply use the next known run type. Otherwise we
            // find
            // the run type that the timestamp is in.
            if (value.getTime().isBefore(currentPeriod.getStart())
              || currentPeriod.encompasses(value.getTime())) {
              periodFound = true;
            } else {
              currentPeriodIndex++;
              currentPeriod = runTypePeriods.get(currentPeriodIndex);
            }
          }

          if (inFlushingPeriod(value.getTime(), currentPeriod, instrument)) {
            value.setUserQC(Flag.FLUSHING, "");
          }
        }
      }

      // Store the remaining values
      if (sensorValues.size() > 0) {
        DataSetDataDB.storeSensorValues(conn, sensorValues);
      }

      // Adjust the Dataset limits to the actual extracted data
      if (null != realStartTime) {
        dataSet.setStart(realStartTime);
      }

      if (null != realEndTime) {
        dataSet.setEnd(realEndTime);
      }

      dataSet.setBounds(minLon, minLat, maxLon, maxLat);

      // Trigger the Auto QC job
      dataSet.setStatus(DataSet.STATUS_AUTO_QC);
      DataSetDB.updateDataSet(conn, dataSet);
      Map<String, String> jobParams = new HashMap<String, String>();
      jobParams.put(AutoQCJob.ID_PARAM,
        String.valueOf(Long.parseLong(parameters.get(ID_PARAM))));
      JobManager.addJob(dataSource, JobManager.getJobOwner(dataSource, id),
        AutoQCJob.class.getCanonicalName(), jobParams);

      conn.commit();
    } catch (Exception e) {
      e.printStackTrace();
      DatabaseUtils.rollBack(conn);
      try {
        // Set the dataset to Error status
        getDataset(conn).setStatus(DataSet.STATUS_ERROR);
        // And add a (friendly) message...
        StringBuffer message = new StringBuffer();
        message.append(getJobName());
        message.append(" - error: ");
        message.append(e.getMessage());
        getDataset(conn).addMessage(message.toString(),
          ExceptionUtils.getStackTrace(e));
        DataSetDB.updateDataSet(conn, getDataset(conn));
        conn.commit();
      } catch (Exception e1) {
        e1.printStackTrace();
      }
      throw new JobFailedException(id, e);
    } finally {
      DatabaseUtils.closeConnection(conn);
    }
  }

  private boolean inFlushingPeriod(LocalDateTime time,
    RunTypePeriod runTypePeriod, Instrument instrument) {

    boolean result = false;

    if (instrument.getPreFlushingTime() > 0
      && DateTimeUtils.secondsBetween(runTypePeriod.getStart(),
        time) <= instrument.getPreFlushingTime()) {

      result = true;
    } else if (instrument.getPostFlushingTime() > 0
      && DateTimeUtils.secondsBetween(time,
        runTypePeriod.getEnd()) <= instrument.getPostFlushingTime()) {

      result = true;
    }

    return result;
  }

  @Override
  public String getJobName() {
    return jobName;
  }

  /**
   * Reset the data set processing.
   *
   * Delete all related records and reset the status
   *
   * @throws MissingParamException
   *           If any of the parameters are invalid
   * @throws InvalidDataSetStatusException
   *           If the method sets an invalid data set status
   * @throws DatabaseException
   *           If a database error occurs
   * @throws RecordNotFoundException
   *           If the record don't exist
   */
  protected void reset(Connection conn) throws JobFailedException {

    try {
      DataSetDataDB.deleteDataReduction(conn, getDataset(conn).getId());
      DataSetDataDB.deleteMeasurements(conn, getDataset(conn).getId());
      DataSetDataDB.deleteSensorValues(conn, getDataset(conn).getId());
      DataSetDB.setDatasetStatus(conn, getDataset(conn).getId(),
        DataSet.STATUS_WAITING);
    } catch (Exception e) {
      throw new JobFailedException(id, "Error while resetting dataset", e);
    }
  }
}
