package uk.ac.exeter.QuinCe.web.Instrument.newInstrument;

import uk.ac.exeter.QuinCe.data.Instrument.FileDefinition;
import uk.ac.exeter.QuinCe.data.Instrument.InstrumentFileSet;
import uk.ac.exeter.QuinCe.data.Instrument.DataFormats.DateTimeSpecificationException;
import uk.ac.exeter.QuinCe.data.Instrument.SensorDefinition.SensorAssignments;

/**
 * Class to define a set of InstrumentFiles when creating an instrument
 *
 * @author Steve Jones
 *
 */
public class NewInstrumentFileSet extends InstrumentFileSet {

  /**
   * The serial version UID
   */
  private static final long serialVersionUID = 3353458532623379331L;

  /**
   * Simple constructor to create an empty set
   */
  protected NewInstrumentFileSet() {
    super();
  }

  @Override
  public boolean add(FileDefinition file) {
    boolean result = false;

    if (file instanceof FileDefinitionBuilder) {
      result = super.add(file);
    }

    return result;
  }

  @Override
  public FileDefinitionBuilder get(int index) {
    return (FileDefinitionBuilder) super.get(index);
  }

  protected FileDefinitionBuilder getByDescription(String fileDescription) {
    FileDefinitionBuilder result = null;

    for (FileDefinition file : this) {
      if (file.getFileDescription().equals(fileDescription)) {
        result = (FileDefinitionBuilder) file;
      }
    }

    return result;
  }

  /**
   * Get the time and position column assignments for all files related to this
   * instrument.
   *
   * <p>
   * The assignments are encoded as a JSON string in the following format:
   * </p>
   *
   * <pre>
   * [
   * ]
   * </pre>
   *
   * @return The time and position assignments
   * @throws DateTimeSpecificationException
   *           If an error occurs while generating the date/time string
   */
  public String getFileSpecificAssignments(SensorAssignments sensorAssignments)
    throws DateTimeSpecificationException {
    StringBuilder json = new StringBuilder();

    json.append('[');

    int count = 0;
    for (FileDefinition file : this) {
      count++;

      json.append('{');
      json.append("\"longitude\":");
      json.append(file.getLongitudeSpecification().getJsonString());
      json.append(",\"latitude\":");
      json.append(file.getLatitudeSpecification().getJsonString());
      json.append(",\"dateTime\":");
      json.append(file.getDateTimeSpecification().getJsonString());
      json.append(",\"runTypeColRequired\":");
      json.append(sensorAssignments.runTypeRequired(file.getFileDescription()));
      json.append(",\"runTypeCol\":");
      json.append(file.getRunTypeColumns());
      json.append('}');

      if (count < size()) {
        json.append(',');
      }
    }

    json.append(']');

    return json.toString();
  }
}
