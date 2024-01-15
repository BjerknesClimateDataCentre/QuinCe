package uk.ac.exeter.QuinCe.data.Dataset.DataReduction;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import uk.ac.exeter.QuinCe.data.Dataset.Measurement;
import uk.ac.exeter.QuinCe.data.Instrument.Instrument;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.SensorType;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.Variable;

/**
 * Data Reduction class for underway atmospheric pCO₂ measurements taken by
 * equilibrator-based systems.
 */
public class UnderwayAtmosphericPco2Reducer extends DataReducer {

  /**
   * The reducer's calculation parameters.
   */
  private static List<CalculationParameter> calculationParameters = null;

  /**
   * Basic {@link DataReducer} constructor.
   *
   * @param variable
   *          The {@link Variable} being processed.
   * @param properties
   *          The variable properties.
   */
  public UnderwayAtmosphericPco2Reducer(Variable variable,
    Map<String, Properties> properties) {

    super(variable, properties);
  }

  @Override
  public void doCalculation(Instrument instrument, Measurement measurement,
    DataReductionRecord record, Connection conn) throws DataReductionException {

    try {
      // We use equilibrator temperature as the presumed most realistic gas
      // temperature
      Double intakeTemperature = measurement
        .getMeasurementValue("Intake Temperature").getCalculatedValue();
      Double salinity = measurement.getMeasurementValue("Salinity")
        .getCalculatedValue();
      Double atmosphericPressure = measurement
        .getMeasurementValue("Atmospheric Pressure").getCalculatedValue();
      Double co2InGas = measurement.getMeasurementValue(getXCO2Parameter())
        .getCalculatedValue();

      Double seaLevelPressure = Calculators.calcSeaLevelPressure(
        atmosphericPressure, intakeTemperature,
        getFloatProperty("atm_pres_sensor_height"));

      Calculator calculator = new Calculator(intakeTemperature, salinity,
        seaLevelPressure, co2InGas);

      record.put("Sea Level Pressure", seaLevelPressure);
      record.put("pH₂O", calculator.pH2O);
      record.put("xCO₂", co2InGas);
      record.put("pCO₂", calculator.pCO2);
      record.put("fCO₂", calculator.fCO2);
    } catch (Exception e) {
      throw new DataReductionException(e);
    }
  }

  @Override
  public List<CalculationParameter> getCalculationParameters() {
    if (null == calculationParameters) {
      calculationParameters = new ArrayList<CalculationParameter>(5);

      calculationParameters.add(new CalculationParameter(makeParameterId(0),
        "Sea Level Pressure", "Sea Level Pressure", "CAPASS01", "hPa", false));

      calculationParameters.add(new CalculationParameter(makeParameterId(1),
        "pH₂O", "Atmosphere Water Vapour Pressure", "CPVPZZ01", "hPa", false));

      calculationParameters.add(new CalculationParameter(makeParameterId(2),
        "xCO₂", "xCO₂ In Atmosphere", "XCO2DRAT", "μmol mol⁻¹", true));

      calculationParameters.add(new CalculationParameter(makeParameterId(3),
        "pCO₂", "pCO₂ In Atmosphere", "ACO2XXXX", "μatm", true));

      calculationParameters.add(new CalculationParameter(makeParameterId(4),
        "fCO₂", "fCO₂ In Atmosphere", "FCO2WTAT", "μatm", true));
    }

    return calculationParameters;
  }

  /**
   * Get the name of the {@link SensorType} to use to get the base xCO₂ value
   * for the calculation.
   *
   * @return The xCO₂ {@link SensorType} to use.
   */
  protected String getXCO2Parameter() {
    return "xCO₂ (with standards)";
  }

  /**
   * Class that takes in the required parameters and calculates pCO₂ and fCO₂.
   */
  class Calculator {

    /**
     * Measured intake temperature.
     */
    private final Double intakeTemperature;

    /**
     * Measured salinity.
     */
    private final Double salinity;

    /**
     * Atmospheric pressure at sea level.
     *
     * <p>
     * Must be created in advance using
     * {@link Calculators#calcSeaLevelPressure(Double, Double, Float)}.
     * </p>
     */
    private final Double seaLevelPressure;

    /**
     * The CO₂ value measured by the gas analyser.
     */
    private final Double co2InGas;

    /**
     * The calculated water vapour pressure.
     */
    protected Double pH2O = null;

    /**
     * The calculated pCO₂.
     */
    protected Double pCO2 = null;

    /**
     * The calculated fCO₂.
     */
    protected Double fCO2 = null;

    /**
     * Initialise the calculator with the required measured values.
     *
     * @param intakeTemperature
     *          Intake temperature.
     * @param salinity
     *          Salinity.
     * @param seaLevel
     *          Pressure Sea level pressure. Must be created in advance using
     *          {@link Calculators#calcSeaLevelPressure(Double, Double, Float)}.
     * @param co2InGas
     *          The CO₂ value measured by the gas analyser.
     */
    protected Calculator(Double intakeTemperature, Double salinity,
      Double seaLevelPressure, Double co2InGas) {

      this.intakeTemperature = intakeTemperature;
      this.salinity = salinity;
      this.seaLevelPressure = seaLevelPressure;
      this.co2InGas = co2InGas;
      calculate();
    }

    /**
     * Perform the calculation to produce {@link #pH2O}, {@link #pCO2} and
     * {@link #fCO2}.
     */
    protected void calculate() {
      pH2O = Calculators.calcPH2O(salinity, intakeTemperature);
      pCO2 = Calculators.calcpCO2TEWet(co2InGas, seaLevelPressure, pH2O);
      fCO2 = Calculators.calcfCO2(pCO2, co2InGas, seaLevelPressure,
        intakeTemperature);
    }
  }
}
