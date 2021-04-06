package uk.ac.exeter.QuinCe.data.Instrument.Calibration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import uk.ac.exeter.QuinCe.data.Instrument.Instrument;

/**
 * A calibration that applies up to a fifth-order polynomial adjustment
 *
 * @author Steve Jones
 *
 */
public class PolynomialSensorCalibration extends SensorCalibration {

  /**
   * The name of the Intercept coefficient
   */
  private static final String INTERCEPT_NAME = "Intercept";

  /**
   * Contains the labels for the polynomial curve parameters (constructed in the
   * {@code static} block)
   */
  private static List<String> valueNames;

  static {
    valueNames = new ArrayList<String>(5);
    valueNames.add("x⁵");
    valueNames.add("x⁴");
    valueNames.add("x³");
    valueNames.add("x²");
    valueNames.add("x");
    valueNames.add(INTERCEPT_NAME);
  }

  /**
   * Create a calibration object
   *
   * @param instrumentId
   *          The instrument to which the calibration target belongs
   * @param target
   *          The calibration target (most likely a sensor)
   */
  public PolynomialSensorCalibration(Instrument instrument, String target) {
    super(instrument, target);
  }

  /**
   * Create a calibration object with no target set
   *
   * @param instrumentId
   *          The instrument to which the calibration target belongs
   */
  public PolynomialSensorCalibration(Instrument instrument) {
    super(instrument);
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
  public PolynomialSensorCalibration(long id, Instrument instrument,
    String target, LocalDateTime deploymentDate, List<String> coefficients) {
    super(id, instrument, target, deploymentDate, coefficients);
  }

  @Override
  public List<String> getCoefficientNames() {
    return valueNames;
  }

  @Override
  protected String buildHumanReadableCoefficients() {

    StringBuilder result = new StringBuilder();

    for (CalibrationCoefficient coefficient : coefficients) {
      appendCoefficient(result, coefficient);
    }

    return result.toString();
  }

  /**
   * Add a coefficient to the human readable coefficients string
   *
   * @param string
   *          The string being constructed
   * @param coefficient
   *          The coefficient
   */
  private void appendCoefficient(StringBuilder string,
    CalibrationCoefficient coefficient) {

    String name = coefficient.getName();
    double value = Double.parseDouble(coefficient.getValue());

    if (string.length() == 0 && value != 0) {
      string.append(value);

      if (!name.equals(INTERCEPT_NAME)) {
        string.append(name);
      }
    } else if (value != 0) {
      if (value > 0) {
        string.append(" + ");
      } else if (value < 0) {
        string.append(" - ");
      }

      string.append(Math.abs(value));
      if (!name.equals(INTERCEPT_NAME)) {
        string.append(name);
      }
    }
  }

  @Override
  public boolean coefficientsValid() {
    return (null != coefficients && coefficients.size() == 6);
  }

  @Override
  protected void initialiseCoefficients() {
    coefficients = new ArrayList<CalibrationCoefficient>(
      getCoefficientNames().size());

    for (String name : getCoefficientNames()) {

      if (name.equals("x")) {
        coefficients.add(new CalibrationCoefficient(name, "1.0"));
      } else {
        coefficients.add(new CalibrationCoefficient(name));
      }
    }
  }

  @Override
  public Double calibrateValue(Double rawValue) {
    int power = 0;
    Double calibratedValue = 0d;

    // The coefficients are stored from highest to lowest power, so iterate in
    // reverse list order
    ListIterator<CalibrationCoefficient> iterator = coefficients
      .listIterator(coefficients.size());
    while (iterator.hasPrevious()) {
      calibratedValue += iterator.previous().getDoubleValue()
        * Math.pow(rawValue, power);
      power++;
    }
    return calibratedValue;
  }
}
