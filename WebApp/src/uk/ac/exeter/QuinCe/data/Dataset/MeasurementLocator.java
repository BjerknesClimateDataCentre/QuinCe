package uk.ac.exeter.QuinCe.data.Dataset;

import java.sql.Connection;
import java.util.List;

import uk.ac.exeter.QuinCe.data.Instrument.Instrument;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.Variable;

public abstract class MeasurementLocator {

  /**
   * Get the Measurements from the dataset that apply to the variable(s) that
   * this locator handles.
   *
   * @return The measurements
   */
  public abstract List<Measurement> locateMeasurements(Connection conn,
    Instrument instrument, DataSet dataset) throws MeasurementLocatorException;

  /**
   * Get the {@link MeasurementLocator} for the specified variable.
   *
   * @param variable
   *          The variable
   * @return The measurement locator
   */
  public static MeasurementLocator getMeasurementLocator(Variable variable) {

    MeasurementLocator result = null;

    // If the variable has internal calibrations, it has run types
    if (variable.hasInternalCalibrations()) {
      result = new RunTypeMeasurementLocator();
    } else {
      switch (variable.getName()) {
      case "CONTROS pCO₂": {
        result = new ControsPco2MeasurementLocator();
        break;
      }
      case "Pro Oceanus CO₂ Water": {
        result = new ProOceanusCO2MeasurementLocator();
        break;
      }
      case "ASVCO₂ Water": {
        result = new ASVCO2MeasurementLocator();
        break;
      }
      case "Water Vapour Mixing Ratio": {
        result = new WaterVapourMixingRatioMeasurementLocator();
        break;
      }
      case "Pro Oceanus CO₂ Atmosphere":
      case "ASVCO₂ Atmosphere": {
        // The atmospheric measurements are automatically created by the water
        // measurement locator.
        result = new DummyMeasurementLocator();
        break;
      }
      default: {
        result = new SimpleMeasurementLocator(variable);
      }
      }
    }

    return result;
  }
}
