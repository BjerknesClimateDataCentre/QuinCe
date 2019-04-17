package uk.ac.exeter.QuinCe.jobs.files;

import java.sql.Connection;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.exception.ExceptionUtils;

import uk.ac.exeter.QuinCe.data.Dataset.DataSet;
import uk.ac.exeter.QuinCe.data.Dataset.DataSetDB;
import uk.ac.exeter.QuinCe.jobs.InvalidJobParametersException;
import uk.ac.exeter.QuinCe.jobs.Job;
import uk.ac.exeter.QuinCe.jobs.JobFailedException;
import uk.ac.exeter.QuinCe.jobs.JobManager;
import uk.ac.exeter.QuinCe.jobs.JobThread;
import uk.ac.exeter.QuinCe.utils.DatabaseException;
import uk.ac.exeter.QuinCe.utils.DatabaseUtils;
import uk.ac.exeter.QuinCe.utils.MissingParamException;
import uk.ac.exeter.QuinCe.utils.RecordNotFoundException;
import uk.ac.exeter.QuinCe.web.system.ResourceManager;

/**
 * The background job to perform data reduction on a data file.
 *
 * @author Steve Jones
 */
public class DataReductionJob extends Job {

  /**
   * The parameter name for the data set id
   */
  public static final String ID_PARAM = "id";

  /**
   * Name of the job, used for reporting
   */
  private final String jobName = "Data Reduction";

  /**
   * Constructor for a data reduction job to be run on a specific data file.
   * The job record must already have been created in the database.
   *
   * @param resourceManager The QuinCe resource manager
   * @param config The application configuration
   * @param jobId The job's database ID
   * @param parameters The job parameters. These will be ignored.
   * @throws MissingParamException If any constructor parameters are missing
   * @throws InvalidJobParametersException If any of the parameters are invalid. Because parameters are ignored for this job, this exception will not be thrown.
   * @throws DatabaseException If a database error occurs
   * @throws RecordNotFoundException If the job cannot be found in the database
   */
  public DataReductionJob(ResourceManager resourceManager, Properties config, long jobId, Map<String, String> parameters) throws MissingParamException, InvalidJobParametersException, DatabaseException, RecordNotFoundException {
    super(resourceManager, config, jobId, parameters);
  }

  @Override
  protected void execute(JobThread thread) throws JobFailedException {

    Connection conn = null;
    Connection statusConn = null;
    DataSet dataSet = null;

    try {
      conn = dataSource.getConnection();
      conn.setAutoCommit(false);

      dataSet = DataSetDB.getDataSet(conn,
          Long.parseLong(parameters.get(ID_PARAM)));

      // Clear messages before executing job
      dataSet.clearMessages();
      dataSet.setStatus(DataSet.STATUS_DATA_REDUCTION);

      statusConn = dataSource.getConnection();
      DataSetDB.updateDataSet(statusConn, dataSet);
      statusConn.close();

      // If the thread was interrupted, undo everything
      if (thread.isInterrupted()) {
        conn.rollback();

        // Requeue the data reduction job
        JobManager.requeueJob(conn, id);
        conn.commit();
      } else {

        // Set up the Auto QC job
        dataSet.setStatus(DataSet.STATUS_USER_QC);
        DataSetDB.updateDataSet(conn, dataSet);

        conn.commit();
      }

    } catch (Exception e) {
      DatabaseUtils.rollBack(conn);

      try {
        if (dataSet != null
            && dataSet.getId() != DatabaseUtils.NO_DATABASE_RECORD) {
          // Change dataset status to Error, and append an error message
          StringBuffer message = new StringBuffer();
          message.append(getJobName());
          message.append(" - error: ");
          message.append(e.getMessage());
          dataSet.addMessage(message.toString(),
              ExceptionUtils.getStackTrace(e));
          dataSet.setStatus(DataSet.STATUS_ERROR);

          DataSetDB.updateDataSet(conn, dataSet);
          conn.commit();
        }
      } catch (Exception e1) {
        e.printStackTrace();
      }

      throw new JobFailedException(id, e);
    } finally {
      DatabaseUtils.closeConnection(conn, statusConn);
    }
  }

  /**
   * Removes any previously calculated data reduction results from the database
   * @throws JobFailedException If an error occurs
   */
  protected void reset() throws JobFailedException {
  }

  @Override
  protected void validateParameters() throws InvalidJobParametersException {

    String datasetIdString = parameters.get(ID_PARAM);
    if (null == datasetIdString) {
      throw new InvalidJobParametersException(ID_PARAM + "is missing");
    }

    try {
      Long.parseLong(datasetIdString);
    } catch (NumberFormatException e) {
      throw new InvalidJobParametersException(ID_PARAM + "is not numeric");
    }
  }

  @Override
  public String getJobName() {
    return jobName;
  }
}
