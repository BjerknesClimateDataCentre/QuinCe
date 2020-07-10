package uk.ac.exeter.QuinCe.data.Dataset.DataReduction;

import java.sql.Connection;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import uk.ac.exeter.QuinCe.data.Instrument.Instrument;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.InstrumentVariable;

/**
 * Factory class for Data Reducers
 *
 * @author Steve Jones
 *
 */
public class DataReducerFactory {

  /**
   * Get the Data Reducer for a given variable and initialise it
   *
   * @param variable
   *          The variable
   * @return The Data Reducer
   * @throws DataReductionException
   *           If the reducer cannot be retreived
   */
  public static DataReducer getReducer(Connection conn, Instrument instrument,
    InstrumentVariable variable, Map<String, Float> variableAttributes)
    throws DataReductionException {

    DataReducer reducer;

    try {
      switch (variable.getName()) {
      case "Underway Marine pCO₂": {
        reducer = new UnderwayMarinePco2Reducer(variable, variableAttributes);
        break;
      }
      case "Underway Atmospheric pCO₂": {
        reducer = new UnderwayAtmosphericPco2Reducer(variable,
          variableAttributes);
        break;
      }
      case "SailDrone Marine CO₂ NRT": {
        reducer = new SaildroneMarinePco2Reducer(variable, variableAttributes);
        break;
      }
      case "SailDrone Atmospheric CO₂ NRT": {
        reducer = new SaildroneAtmosphericPco2Reducer(variable,
          variableAttributes);
        break;
      }
      case "Soderman": {
        reducer = new NoReductionReducer(variable, nrt, variableAttributes,
          allMeasurements, groupedSensorValues, calibrationSet);
        break;
      }
      default: {
        throw new DataReductionException(
          "Cannot find reducer for variable " + variable.getName());
      }
      }
    } catch (Exception e) {
      throw new DataReductionException("Cannot initialise data reducer", e);
    }

    return reducer;
  }

  private static DataReducer getSkeletonReducer(InstrumentVariable variable)
    throws DataReductionException {

    DataReducer reducer = null;

    switch (variable.getName()) {
    case "Underway Marine pCO₂": {
      reducer = new UnderwayMarinePco2Reducer(variable, null);
      break;
    }
    case "Underway Atmospheric pCO₂": {
      reducer = new UnderwayAtmosphericPco2Reducer(variable, null);
      break;
    }
    case "SailDrone Marine CO₂ NRT": {
      reducer = new SaildroneMarinePco2Reducer(variable, null);
      break;
    }
    case "SailDrone Atmospheric CO₂ NRT": {
      reducer = new SaildroneAtmosphericPco2Reducer(variable, null);
      break;
    }
    case "Soderman": {
      reducer = new NoReductionReducer(variable, false, null, null, null, null);
      break;
    }
    default: {
      throw new DataReductionException(
        "Cannot find reducer for variable " + variable.getName());
    }
    }

    return reducer;
  }

  /**
   * Get the calculation parameters for a given data reducer with their IDs
   *
   * @param variable
   *          The variable for the data reducer
   * @return The calculation parameter names
   * @throws DataReductionException
   *           If the variable does not have a reducer
   */
  public static LinkedHashMap<String, Long> getCalculationParameters(
    InstrumentVariable variable) throws DataReductionException {

    DataReducer reducer = getSkeletonReducer(variable);
    List<String> parameterNames = reducer.getCalculationParameterNames();

    LinkedHashMap<String, Long> result = new LinkedHashMap<String, Long>();
    int i = -1;
    for (String name : parameterNames) {
      i++;
      result.put(name, makeId(variable, i));
    }

    return result;
  }

  public static TreeMap<Long, CalculationParameter> getCalculationParameters(
    InstrumentVariable variable, boolean includeCalculationColumns)
    throws DataReductionException {

    DataReducer reducer = getSkeletonReducer(variable);

    TreeMap<Long, CalculationParameter> result = new TreeMap<Long, CalculationParameter>();

    List<CalculationParameter> parameters = reducer.getCalculationParameters();
    for (int i = 0; i < parameters.size(); i++) {
      long id = makeId(variable, i);

      CalculationParameter parameter = parameters.get(i);
      if (includeCalculationColumns || parameter.isResult()) {
        result.put(id, parameter);
      }
    }

    return result;
  }

  public static Map<InstrumentVariable, List<CalculationParameter>> getCalculationParameters(
    Collection<InstrumentVariable> variables) throws DataReductionException {

    Map<InstrumentVariable, List<CalculationParameter>> result = new HashMap<InstrumentVariable, List<CalculationParameter>>();

    for (InstrumentVariable variable : variables) {
      DataReducer reducer = getSkeletonReducer(variable);
      result.put(variable, reducer.getCalculationParameters());
    }

    return result;
  }

  private static long makeId(InstrumentVariable variable, int sequence) {
    return variable.getId() * 10000 + sequence;
  }
}
