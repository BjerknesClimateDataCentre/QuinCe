package uk.ac.exeter.QuinCe.web.Instrument;

import java.time.LocalDateTime;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;

import uk.ac.exeter.QuinCe.data.Dataset.DataSet;
import uk.ac.exeter.QuinCe.data.Instrument.Calibration.Calibration;
import uk.ac.exeter.QuinCe.data.Instrument.Calibration.CalibrationDB;
import uk.ac.exeter.QuinCe.data.Instrument.Calibration.ExternalStandardDB;
import uk.ac.exeter.QuinCe.data.Instrument.Calibration.ExternalStandardFactory;

/**
 * Bean for external standards
 */
@ManagedBean
@SessionScoped
public class ExternalStandardsBean extends CalibrationBean {

  /**
   * The navigation string for the external standards list
   */
  private static final String NAV_LIST = "external_standards";

  /**
   * Constructor
   */
  public ExternalStandardsBean() {
    super();
  }

  @Override
  protected String getPageNavigation() {
    return NAV_LIST;
  }

  @Override
  protected CalibrationDB getDbInstance() {
    return ExternalStandardDB.getInstance();
  }

  @Override
  protected String getCalibrationType() {
    return ExternalStandardDB.EXTERNAL_STANDARD_CALIBRATION_TYPE;
  }

  @Override
  public String getHumanReadableCalibrationType() {
    return "Gas Standards";
  }

  @Override
  protected Calibration initNewCalibration(LocalDateTime date)
    throws Exception {
    return ExternalStandardFactory.getExternalStandard(instrument, date);
  }

  @Override
  public String getTargetLabel() {
    return "Standard";
  }

  @Override
  public String getCoefficientsLabel() {
    return "Concentration";
  }

  @Override
  protected int getReprocessStatus() {
    return DataSet.STATUS_SENSOR_QC;
  }

  @Override
  public String getCalibrationName() {
    return "Standard";
  }
}
