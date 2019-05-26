package uk.ac.exeter.QuinCe.web.PlotPage;

public class Field {

  public static final long ROWID_FIELD_ID = 0L;

  private long id;

  private String name;

  public Field(long sensorId, String name) {
    this.id = sensorId;
    this.name = name;
  }
  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }
}
