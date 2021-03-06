package org.ironrhino.core.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.expression.ExpressionException;
import org.springframework.expression.spel.InternalParseException;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ExpressionUtils {

	public static Object evalExpression(String expression, Map<String, ?> context) {
		if (StringUtils.isBlank(expression))
			return expression;
		if (expression.indexOf(';') > 0 || expression.indexOf('[') > -1)
			// SPEL doesn't supports ';' as delimiter
			// SPEL use '{}' instead of '[]' as inline list
			return ExpressionEngine.MVEL.evalExpression(expression, context);
		try {
			return ExpressionEngine.SPEL.evalExpression(expression, context);
		} catch (ExpressionException | InternalParseException e) {
			return ExpressionEngine.MVEL.evalExpression(expression, context);
		} catch (IllegalStateException e) {
			String message = e.getMessage();
			// org.springframework.expression.spel.standard.Tokenizer.process(Tokenizer.java)
			if (message == null || !message.startsWith("Cannot handle ("))
				throw e;
			return ExpressionEngine.MVEL.evalExpression(expression, context);
		}
	}

	public static Object eval(String template, Map<String, ?> context) {
		if (StringUtils.isBlank(template))
			return template;
		int start = template.indexOf('{');
		int end = template.indexOf('}');
		int index1 = template.indexOf(';');
		int index2 = template.indexOf('[');
		if (index1 > start && index1 < end || index2 > start && index2 < end || template.contains("@{")
				|| template.contains("@if{"))
			return ExpressionEngine.MVEL.eval(template, context);
		try {
			return ExpressionEngine.SPEL.eval(template, context);
		} catch (ExpressionException | InternalParseException e) {
			return ExpressionEngine.MVEL.eval(template, context);
		} catch (IllegalStateException e) {
			String message = e.getMessage();
			if (message == null || !message.startsWith("Cannot handle ("))
				throw e;
			return ExpressionEngine.MVEL.eval(template, context);
		}
	}

	public static String evalString(String template, Map<String, ?> context) {
		Object obj = eval(template, context);
		if (obj == null)
			return null;
		return obj.toString();
	}

	public static boolean evalBoolean(String template, Map<String, ?> context, boolean defaultValue) {
		if (StringUtils.isBlank(template))
			return defaultValue;
		Object obj = eval(template, context);
		if (obj == null)
			return defaultValue;
		if (obj instanceof Boolean)
			return (Boolean) obj;
		return Boolean.parseBoolean(obj.toString());
	}

	public static int evalInt(String template, Map<String, ?> context, int defaultValue) {
		if (StringUtils.isBlank(template))
			return defaultValue;
		Object obj = eval(template, context);
		if (obj == null)
			return defaultValue;
		if (obj instanceof Integer)
			return (Integer) obj;
		return Integer.parseInt(obj.toString());
	}

	public static long evalLong(String template, Map<String, ?> context, long defaultValue) {
		if (StringUtils.isBlank(template))
			return defaultValue;
		Object obj = eval(template, context);
		if (obj == null)
			return defaultValue;
		if (obj instanceof Long)
			return (Long) obj;
		return Long.parseLong(obj.toString());
	}

	public static double evalDouble(String template, Map<String, ?> context, double defaultValue) {
		if (StringUtils.isBlank(template))
			return defaultValue;
		Object obj = eval(template, context);
		if (obj == null)
			return defaultValue;
		if (obj instanceof Double)
			return (Double) obj;
		return Double.parseDouble(obj.toString());
	}

	@SuppressWarnings("unchecked")
	public static <T> List<T> evalList(String template, Map<String, ?> context) {
		Object obj = eval(template, context);
		if (obj == null)
			return null;
		if (obj instanceof List)
			return (List<T>) obj;
		return (List<T>) Arrays.asList(obj.toString().split("\\s*,\\s*"));
	}

}
