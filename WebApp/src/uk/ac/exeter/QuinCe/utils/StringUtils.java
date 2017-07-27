package uk.ac.exeter.QuinCe.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Various miscellaneous string utilities
 * @author Steve Jones
 *
 */
public class StringUtils {

	/**
	 * Converts a list of values to a single string,
	 * with a semi-colon delimiter.
	 * 
	 * <b>Note that this does not handle semi-colons within the values themselves.</b>
	 * 
	 * @param list The list to be converted
	 * @return The converted list
	 */
	public static String listToDelimited(List<?> list) {
		return listToDelimited(list, ";", null);
	}
	
	/**
	 * Converts a list of values to a single string,
	 * with a specified delimiter.
	 * 
	 * <b>Note that this does not handle the case where the delimiter is found within the values themselves.</b>
	 * 
	 * @param list The list to be converted
	 * @param delimiter The delimiter to use
	 * @return The converted list
	 */
	public static String listToDelimited(List<?> list, String delimiter) {
		return listToDelimited(list, delimiter, null);
	}
	
	/**
	 * Convert a list to a delimited string. Each value can optionally be surrounded
	 * with a character.
	 * @param list The list
	 * @param delimiter The delimiter
	 * @param surrounder The string to surround each value
	 * @return The delimited string
	 */
	public static String listToDelimited(List<?> list, String delimiter, String surrounder) {
		
		String result = null;
		
		if (null != list) {
			StringBuilder buildResult = new StringBuilder();
			for (int i = 0; i < list.size(); i++) {
				
				if (null != surrounder) {
					buildResult.append(surrounder);
					buildResult.append(list.get(i).toString().replace(surrounder, "\\" + surrounder));
					buildResult.append(surrounder);
				} else {
					buildResult.append(list.get(i).toString());
				}

				if (i < (list.size() - 1)) {
					buildResult.append(delimiter);

				}
			}
			result = buildResult.toString();
		}
		
		return result;
	}
	
	/**
	 * Converts a String containing values separated by semi-colon delimiters
	 * into a list of String values
	 * 
	 * <b>Note that this does not handle semi-colons within the values themselves.</b>
	 * 
	 * @param values The String to be converted
	 * @return A list of String values
	 */
	public static List<String> delimitedToList(String values) {
		
		List<String> result = null;
		
		if (null != values) {
			String delimiter = ";";
			result = Arrays.asList(values.split(delimiter, 0));
		}
		
		return result;
	}
	
	/**
	 * Convert a delimited list of integers into a list of integers
	 * @param values The list
	 * @return The list as integers
	 */
	public static List<Integer> delimitedToIntegerList(String values) {
		
		List<Integer> result = null;
		
		if (values != null) {
			List<String> stringList = delimitedToList(values);
			result = new ArrayList<Integer>(stringList.size());

			for (String item: stringList) {
				result.add(Integer.parseInt(item));
			}
		}
		
		return result;
	}
	
	/**
	 * Convert a delimited list of doubles into a list of integers
	 * @param values The list
	 * @return The list as doubles
	 */
	public static List<Double> delimitedToDoubleList(String values) {
		
		List<Double> result = null;
		
		if (values != null) {
			List<String> stringList = delimitedToList(values.replaceAll(",", ";"));
			result = new ArrayList<Double>(stringList.size());

			for (String item: stringList) {
				result.add(Double.parseDouble(item));
			}
		}
		
		return result;
	}
	
	/**
	 * Extract the stack trace from an Exception (or other
	 * Throwable) as a String.
	 * @param e The error
	 * @return The stack trace
	 */
	public static String stackTraceToString(Throwable e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		return sw.toString();
	}
	
	/**
	 * Determines whether or not a line is a comment, signified by it starting with {@code #} or {@code !} or {@code //}
	 * @param line The line to be checked
	 * @return {@code true} if the line is a comment; {@code false} otherwise.
	 */
	public static boolean isComment(String line) {
		String trimmedLine = line.trim();
		return trimmedLine.length() == 0 || trimmedLine.charAt(0) == '#' || trimmedLine.charAt(0) == '!' || trimmedLine.startsWith("//", 0);
	}
	
	/**
	 * Trims all items in a list of strings. A string that starts with a
	 * single backslash has that backslash removed.
	 * @param source The strings to be converted 
	 * @return The converted strings
	 */
	public static List<String> trimList(List<String> source) {
		
		List<String> result = new ArrayList<String>(source.size());
		
		for (int i = 0; i < source.size(); i++) {
			String trimmedValue = source.get(i).trim();
			if (trimmedValue.startsWith("\\")) {
				trimmedValue = trimmedValue.substring(1);
			}
			
			result.add(trimmedValue);
		}
		
		return result;
	}
	
	/**
	 * Determines whether or not a String value contains a number
	 * @param value The String value
	 * @return {@code true} if the String value is numeric; {@code false} if it is not
	 */
	public static boolean isNumeric(String value) {
		boolean result = true;
		
		if (null == value) {
			result = false;
		} else {
			try {
				Double doubleValue = new Double(value);
				if (doubleValue.isNaN()) {
					result = false;
				}
			} catch (NumberFormatException e) {
				result = false;
			}
		}
		
		return result;
	}
	
	/**
	 * Convert a String-to-String lookup map into a String.
	 * <p>
	 *   Each map entry is converted to a {@code key=value} pair.
	 *   Each entry is separated by a semi-colon.
	 * </p>
	 * <p>
	 *   <b>Note:</b> There is no handling of {@code =} or {@code ;}
	 *   in the keys or values.
	 * </p>
	 * @param map The Map to be converted
	 * @return The String representation of the Map
	 */
	public static String mapToDelimited(Map<String, String> map) {
		
		StringBuilder result = new StringBuilder();
		
		int counter = 0;
		for (Map.Entry<String, String> entry : map.entrySet()) {
			counter++;
			result.append(entry.getKey());
			result.append('=');
			result.append(entry.getValue());
			
			if (counter < map.size()) {
				result.append(';');
			}
		}
		
		return result.toString();
	}
	
	/**
	 * Convert a semi-colon-delimited list of {@code key=value} pairs
	 * into a Map.
	 * @param values The String
	 * @return The Map
	 * @throws StringFormatException If the String is not formatted correctly
	 */
	public static Map<String,String> delimitedToMap(String values) throws StringFormatException {
		
		Map<String, String> result = new HashMap<String, String>();
		
		for (String entry : values.split(";", 0)) {
			
			String[] entrySplit = entry.split("=", 0);
			if (entrySplit.length != 2) {
				throw new StringFormatException("Invalid map format", entry);
			} else {
				result.put(entrySplit[0], entrySplit[1]);
			}
		}
		
		return result;
	}
}
