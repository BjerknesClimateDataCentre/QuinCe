package uk.ac.exeter.QuinCe.web.datasets.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

@SuppressWarnings("serial")
public class FieldSets extends LinkedHashMap<FieldSet, List<Field>> {

  private Field rowIdField;

  protected Map<String, Field> fieldsByName;

  protected Map<Long, Field> fieldsById;

  private long defaultFieldSet = FieldSet.BASE_ID;

  private List<Field> unusedFields = new ArrayList<Field>();

  public FieldSets(String rowIdName) {
    super();
    fieldsByName = new HashMap<String, Field>();
    fieldsById = new HashMap<Long, Field>();

    addFieldSet(FieldSet.BASE_FIELD_SET);

    // Add the row ID field to the base field set
    rowIdField = new Field(FieldSet.BASE_FIELD_SET, Field.ROWID_FIELD_ID,
      rowIdName);
    addField(rowIdField);
  }

  public FieldSets() {
    super();
    fieldsByName = new HashMap<String, Field>();
    fieldsById = new HashMap<Long, Field>();

    addFieldSet(FieldSet.BASE_FIELD_SET);
  }

  public FieldSets(Field initialField) {
    super();
    fieldsByName = new HashMap<String, Field>();
    fieldsById = new HashMap<Long, Field>();

    addFieldSet(FieldSet.BASE_FIELD_SET);

    // Add the row ID field to the base field set
    rowIdField = new Field(FieldSet.BASE_FIELD_SET, Field.ROWID_FIELD_ID,
      initialField.getBaseName());
    addField(initialField);
  }

  /**
   * Get all the field sets with their fields, optionally excluding the base
   * field set
   *
   * @param excludeBase
   * @return
   */
  @SuppressWarnings("unchecked")
  public LinkedHashMap<FieldSet, List<Field>> getFieldSets(
    boolean excludeBase) {
    LinkedHashMap<FieldSet, List<Field>> result = this;

    if (excludeBase) {
      result = (LinkedHashMap<FieldSet, List<Field>>) clone();
      result.remove(FieldSet.BASE_FIELD_SET);
    }

    return result;
  }

  /**
   * Generate a new map of fields to field values
   *
   * @return
   */
  public LinkedHashMap<Field, FieldValue> generateFieldValuesMap() {

    LinkedHashMap<Field, FieldValue> result = new LinkedHashMap<Field, FieldValue>();

    for (List<Field> fields : values()) {
      for (Field field : fields) {
        if (!field.equals(rowIdField)) {
          result.put(field, null);
        }
      }
    }

    return result;
  }

  /**
   * Get the Field with the specified ID
   *
   * @param fieldId
   *          The ID
   * @return The field, or null
   */
  public Field getField(long fieldId) {
    return fieldsById.get(fieldId);
  }

  /**
   * See if this field set contains the field with the specified ID
   *
   * @param fieldId
   *          The field ID
   * @return {@code true} if the field exists; {@code false} if it does not
   */
  public boolean containsField(long fieldId) {
    return fieldsById.containsKey(fieldId);
  }

  /**
   * Get a Field using its name
   *
   * @param fieldName
   * @return
   */
  public Field getField(String fieldName) {
    return fieldsByName.get(fieldName);
  }

  /**
   * Get the row ID field.
   *
   * @return
   */
  public Field getRowIdField() {
    return rowIdField;
  }

  public void addField(Field field) {
    addField(field, false);
  }

  public void addField(Field field, boolean unused) {

    if (unused) {
      unusedFields.add(field);
    } else {
      if (!containsKey(field.getFieldSet())) {
        addFieldSet(field.getFieldSet());
      }

      get(field.getFieldSet()).add(field);
    }

    fieldsByName.put(field.getBaseName(), field);
    fieldsById.put(field.getId(), field);
  }

  public FieldSet addFieldSet(long id, String name) {
    return addFieldSet(id, name, false);
  }

  public FieldSet addFieldSet(long id, String name, boolean defaultFieldSet) {
    FieldSet fieldSet = new FieldSet(id, name);
    addFieldSet(fieldSet);

    // If the size is 2, this is the first 'real' field set to be added,
    // so it has to be the default regardless of what the caller might want.
    if (size() == 2 || defaultFieldSet) {
      this.defaultFieldSet = id;
    }

    return fieldSet;
  }

  private void addFieldSet(FieldSet fieldSet) {
    put(fieldSet, new ArrayList<Field>());
  }

  public FieldSet getFieldSet(long fieldSetId) {
    FieldSet result = null;

    for (FieldSet fieldSet : keySet()) {
      if (fieldSet.getId() == fieldSetId) {
        result = fieldSet;
        break;
      }
    }

    return result;
  }

  public List<Long> getFieldIds() {
    List<Long> result = new ArrayList<Long>();

    for (FieldSet fieldSet : this.keySet()) {
      result.addAll(getFieldIds(fieldSet));
    }

    return result;
  }

  public List<Field> getFields() {
    List<Field> result = new ArrayList<Field>();

    for (List<Field> setFields : this.values()) {
      result.addAll(setFields);
    }

    return result;
  }

  public List<Long> getFieldIds(FieldSet fieldSet) {
    List<Long> result = new ArrayList<Long>(get(fieldSet).size());

    for (Field field : get(fieldSet)) {
      result.add(field.getId());
    }

    return result;
  }

  public List<String> getTableHeadingsList() {
    List<String> headings = new ArrayList<String>();

    for (List<Field> fields : values()) {
      for (Field field : fields) {
        headings.add(field.getFullName());
      }
    }

    return headings;
  }

  private List<Long> getTableIDsList() {
    List<Long> headings = new ArrayList<Long>();

    for (List<Field> fields : values()) {
      for (Field field : fields) {
        headings.add(field.getId());
      }
    }

    return headings;
  }

  public String getTableHeadings() {
    return new Gson().toJson(getTableHeadingsList());
  }

  public String getTableHeadingIDs() {
    return new Gson().toJson(getTableIDsList());
  }

  public int getColumnIndex(long fieldId) {

    boolean found = false;
    int currentColumn = -1;

    for (List<Field> fieldSetFields : values()) {

      for (int i = 0; i < fieldSetFields.size(); i++) {
        currentColumn++;
        if (fieldSetFields.get(i).getId() == fieldId) {
          found = true;
          break;
        }
      }

      if (found) {
        break;
      }
    }

    return (found ? currentColumn : -1);
  }

  public LinkedHashMap<Long, List<Integer>> getColumnIndexes() {
    LinkedHashMap<Long, List<Integer>> columnIndexes = new LinkedHashMap<Long, List<Integer>>();

    int currentColumn = -1;

    for (FieldSet fieldSet : keySet()) {
      // Add the field set to the map
      columnIndexes.put(fieldSet.getId(), new ArrayList<Integer>());

      for (int i = 0; i < get(fieldSet).size(); i++) {
        columnIndexes.get(fieldSet.getId()).add(++currentColumn);
      }
    }

    return columnIndexes;
  }

  /**
   * Get a JSON map listing the column indexes in each field set. The column IDs
   * must match the columns generated by getTableHeadings
   *
   * @return
   */
  public String getFieldSetIds() {
    return new Gson().toJson(getColumnIndexes());
  }

  /**
   * Get the total number of fields
   *
   * @return
   */
  public int getFieldCount() {
    int result = 0;

    for (List<Field> fieldList : values()) {
      result += fieldList.size();
    }

    return result;
  }

  public Field getField(int columnIndex) {
    return getField(getTableHeadingsList().get(columnIndex));
  }

  public long getDefaultFieldSet() {
    return defaultFieldSet;
  }

  public boolean isUnused(Field field) {
    return unusedFields.contains(field);
  }
}
