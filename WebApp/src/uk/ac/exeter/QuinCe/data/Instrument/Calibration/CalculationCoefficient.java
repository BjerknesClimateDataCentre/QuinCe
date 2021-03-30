package uk.ac.exeter.QuinCe.data.Instrument.Calibration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import uk.ac.exeter.QuinCe.data.Dataset.DataReduction.Calculators;
import uk.ac.exeter.QuinCe.data.Instrument.Instrument;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.Variable;

public class CalculationCoefficient extends Calibration {

  private static List<String> valueNames;

  static {
    valueNames = new ArrayList<String>();
    valueNames.add("Value");
  }

  /**
   * Create a calibration object
   *
   * @param instrumentId
   *          The instrument to which the calibration target belongs
   * @param target
   *          The calibration target (most likely a sensor)
   */
  public CalculationCoefficient(Instrument instrument, String target) {
    super(instrument,
      CalculationCoefficientDB.CALCULATION_COEFFICIENT_CALIBRATION_TYPE,
      target);
  }

  /**
   * Create a calibration object with no target set
   *
   * @param instrumentId
   *          The instrument to which the calibration target belongs
   */
  public CalculationCoefficient(Instrument instrument) {
    super(instrument,
      CalculationCoefficientDB.CALCULATION_COEFFICIENT_CALIBRATION_TYPE);
  }

  /**
   * Construct a complete sensor calibration object.
   *
   * @param id
   *          The calibration's database ID
   * @param instrumentId
   *          The instrument ID
   * @param target
   *          The target sensor
   * @param deploymentDate
   *          The deployment date
   * @param coefficients
   *          The calibration coefficients
   * @throws CalibrationException
   *           If the calibration details are invalid
   */
  public CalculationCoefficient(long id, Instrument instrument, String target,
    LocalDateTime deploymentDate, List<Double> coefficients) {

    super(id, instrument,
      CalculationCoefficientDB.CALCULATION_COEFFICIENT_CALIBRATION_TYPE,
      target);
    setDeploymentDate(deploymentDate);
    setCoefficients(coefficients);
  }

  @Override
  public List<String> getCoefficientNames() {
    return valueNames;
  }

  @Override
  protected String buildHumanReadableCoefficients() {
    String result = "Not set";

    if (null != coefficients) {
      result = String.valueOf(coefficients.get(0).getValue());
    }

    return result;
  }

  @Override
  public boolean coefficientsValid() {
    return true;
  }

  @Override
  public Double calibrateValue(Double rawValue) {
    return rawValue;
  }

  public static CalculationCoefficient getCoefficient(
    CalibrationSet calibrationSet, Variable variable, String coefficient) {

    return (CalculationCoefficient) calibrationSet
      .getTargetCalibration(getCoeffecientName(variable, coefficient));
  }

  public static String getCoeffecientName(Variable variable,
    String coefficient) {
    return variable.getId() + "." + coefficient;
  }

  public Double getValue() {
    return getCoefficient("Value");
  }

  public static Double interpolate(CalculationCoefficient x0,
    CalculationCoefficient y0, CalculationCoefficient x1,
    CalculationCoefficient y1, Double x) {

    Double x0Value = null == x0 ? null : x0.getValue();
    Double y0Value = null == y0 ? null : y0.getValue();
    Double x1Value = null == x1 ? null : x1.getValue();
    Double y1Value = null == y1 ? null : y1.getValue();

    return Calculators.interpolate(x0Value, y0Value, x1Value, y1Value, x);
  }
}
