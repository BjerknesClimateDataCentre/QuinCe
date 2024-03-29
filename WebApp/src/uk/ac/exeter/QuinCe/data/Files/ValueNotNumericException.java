package uk.ac.exeter.QuinCe.data.Files;

/**
 * Exception for non-numeric values found in data files
 */
public class ValueNotNumericException extends Exception {

  /**
   * The serial version UID
   */
  private static final long serialVersionUID = 3410974190721406299L;

  /**
   * Simple constructor - no message, no cause
   */
  public ValueNotNumericException() {
    super("Value is not numeric");
  }

}
