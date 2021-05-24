package uk.ac.exeter.QuinCe.data.Dataset;

import java.time.LocalDateTime;
import java.util.ArrayList;

@SuppressWarnings("serial")
public class RunTypePeriods extends ArrayList<RunTypePeriod> {

  boolean finished = false;

  public RunTypePeriods() {
    super();
  }

  public void add(String runType, LocalDateTime time) throws DataSetException {

    if (finished) {
      throw new DataSetException("RunTypePeriods is finished");
    }

    if (size() == 0) {
      add(new RunTypePeriod(runType, time));
    } else {
      if (time.isBefore(getLastTime()) || time.equals(getLastTime())) {
        throw new DataSetException(
          "Added time must be after last period end time");
      }

      RunTypePeriod currentPeriod = get(size() - 1);
      if (!currentPeriod.getRunType().equals(runType)) {
        add(new RunTypePeriod(runType, time));
      } else {
        currentPeriod.setEnd(time);
      }
    }
  }

  /**
   * Signal that the last run time has been registered
   */
  public void finish() {
    if (size() > 0) {
      get(size() - 1).setEnd(LocalDateTime.MAX);
    }

    finished = true;
  }

  public boolean contains(LocalDateTime time) {
    boolean result = false;

    for (RunTypePeriod period : this) {
      if (period.encompasses(time)) {
        result = true;
        break;
      }
    }

    return result;
  }

  private LocalDateTime getLastTime() {
    LocalDateTime result = null;

    if (size() > 0) {
      result = get(size() - 1).getEnd();
    }

    return result;
  }
}
