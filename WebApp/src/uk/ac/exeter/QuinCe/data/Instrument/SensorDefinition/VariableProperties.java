package uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * The properties for a variable.
 *
 * <p>
 * Instances of this class are built from JSON strings, so there are no
 * traditional constructors - only an empty constructor for the case where the
 * properties stored in the database are empty.
 * </p>
 */
public class VariableProperties {

  private final List<String> coefficients;

  private final String runType;

  protected VariableProperties() {
    this.coefficients = new ArrayList<String>();
    this.runType = null;
  }

  public List<String> getCoefficients() {
    return coefficients;
  }

  public String getRunType() {
    return runType;
  }
}
