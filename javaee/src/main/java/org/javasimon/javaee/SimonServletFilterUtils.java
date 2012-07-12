package org.javasimon.javaee;

import org.javasimon.Manager;
import org.javasimon.Stopwatch;
import org.javasimon.javaee.reqreporter.DefaultRequestReporter;
import org.javasimon.javaee.reqreporter.RequestReporter;
import org.javasimon.source.MonitorSource;
import org.javasimon.utils.Replacer;
import org.javasimon.utils.SimonUtils;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationTargetException;

/**
 * Various supporting utility methods for {@link SimonServletFilter}.
 *
 * @author virgo47@gmail.com
 */
public class SimonServletFilterUtils {
	/**
	 * Regex replacer for any number of slashes or dots for a single dot.
	 */
	private static final Replacer TO_DOT_PATTERN = new Replacer("[/.]+", ".");

	/**
	 * Creates new replacer for unallowed characters in the URL. This inverts character group for name pattern
	 * ({@link SimonUtils#NAME_PATTERN_CHAR_CLASS_CONTENT}) and replaces its dot with slash too (dots are to be
	 * replaced, slashs preserved in this step of URL processing).
	 *
	 * @param replacement replacement string (for every unallowed character)
	 * @return compiled pattern matching characters to remove from the URL
	 */
	public static Replacer createUnallowedCharsReplacer(String replacement) {
		return new Replacer("[^" + SimonUtils.NAME_PATTERN_CHAR_CLASS_CONTENT.replace('.', '/') + "]+", replacement);
	}

	/**
	 * Returns Simon name for the specified request (local name without any configured prefix). By default dots and all non-simon-name
	 * compliant characters are removed first, then all slashes are switched to dots (repeating slashes make one dot).
	 *
	 * @param uri request URI
	 * @param unallowedCharacterReplacer replacer for characters that are not allowed in Simon name
	 * @return local part of the Simon name for the request URI (without prefix)
	 */
	public static String getSimonName(String uri, Replacer unallowedCharacterReplacer) {
		if (uri.startsWith("/")) {
			uri = uri.substring(1);
		}
		String name = unallowedCharacterReplacer.process(uri);
		name = TO_DOT_PATTERN.process(name);
		return name;
	}

	/**
	 * Create and initialize the stopwatch source depending on the filter init parameters. Both
	 * monitor source class ({@link SimonServletFilter#INIT_PARAM_STOPWATCH_SOURCE_CLASS} and whether
	 * to cache results ({@link SimonServletFilter#INIT_PARAM_STOPWATCH_SOURCE_CACHE}) can be adjusted.
	 *
	 * @param filterConfig Filter configuration
	 * @return Stopwatch source
	 */
	protected static MonitorSource<HttpServletRequest, Stopwatch> initStopwatchSource(FilterConfig filterConfig, Manager manager) {
		String stopwatchSourceClass = filterConfig.getInitParameter(SimonServletFilter.INIT_PARAM_STOPWATCH_SOURCE_CLASS);
		MonitorSource<HttpServletRequest, Stopwatch> stopwatchSource = createMonitorSource(stopwatchSourceClass, manager);

		injectSimonPrefixIntoMonitorSource(filterConfig, stopwatchSource);

		String cache = filterConfig.getInitParameter(SimonServletFilter.INIT_PARAM_STOPWATCH_SOURCE_CACHE);
		stopwatchSource = wrapMonitorSourceWithCacheIfNeeded(stopwatchSource, cache);

		return stopwatchSource;
	}

	private static MonitorSource<HttpServletRequest, Stopwatch> createMonitorSource(String stopwatchSourceClass, Manager manager) {
		if (stopwatchSourceClass == null) {
			return new HttpStopwatchSource(manager);
		} else {
			return createMonitorForSourceSpecifiedClass(stopwatchSourceClass, manager);
		}
	}

	private static void injectSimonPrefixIntoMonitorSource(FilterConfig filterConfig, MonitorSource<HttpServletRequest, Stopwatch> stopwatchSource) {
		String simonPrefix = filterConfig.getInitParameter(SimonServletFilter.INIT_PARAM_PREFIX);
		if (simonPrefix != null) {
			if (stopwatchSource instanceof HttpStopwatchSource) {
				HttpStopwatchSource httpStopwatchSource = (HttpStopwatchSource) stopwatchSource;
				httpStopwatchSource.setPrefix(simonPrefix);
			} else {
				throw new IllegalArgumentException("Prefix init param is only compatible with HttpStopwatchSource");
			}
		}
	}

	private static MonitorSource<HttpServletRequest, Stopwatch> wrapMonitorSourceWithCacheIfNeeded(MonitorSource<HttpServletRequest, Stopwatch> stopwatchSource, String cache) {
		if (cache != null && Boolean.parseBoolean(cache)) {
			stopwatchSource = HttpStopwatchSource.newCacheStopwatchSource(stopwatchSource);
		}
		return stopwatchSource;
	}

	private static MonitorSource<HttpServletRequest, Stopwatch> createMonitorForSourceSpecifiedClass(String stopwatchSourceClass, Manager manager) {
		try {
			Class<?> monitorClass = Class.forName(stopwatchSourceClass);
			return  monitorSourceNewInstance(manager, monitorClass);
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException("Invalid Stopwatch source class name", e);
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("Invalid Stopwatch source class name", e);
		} catch (InstantiationException e) {
			throw new IllegalArgumentException("Invalid Stopwatch source class name", e);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("Invalid Stopwatch source class name", e);
		}
	}

	@SuppressWarnings("unchecked")
	private static MonitorSource<HttpServletRequest, Stopwatch> monitorSourceNewInstance(Manager manager, Class<?> monitorClass) throws InstantiationException, IllegalAccessException {
		MonitorSource<HttpServletRequest, Stopwatch> stopwatchSource = null;
		try {
			stopwatchSource = (MonitorSource<HttpServletRequest, Stopwatch>) monitorClass.getConstructor(Manager.class).newInstance(manager);
		} catch (NoSuchMethodException e) {
			// safe to ignore here - we'll try default constructor + setter
		} catch (InvocationTargetException e) {
			// safe to ignore here
		}
		if (stopwatchSource == null) {
			stopwatchSource = (MonitorSource<HttpServletRequest, Stopwatch>) monitorClass.newInstance();
			try {
				monitorClass.getMethod("setManager", Manager.class).invoke(stopwatchSource, manager);
			} catch (NoSuchMethodException e) {
				throw new IllegalArgumentException("Stopwatch source class must have public constructor or public setter with Manager argument (used class " + monitorClass.getName() + ")", e);
			} catch (InvocationTargetException e) {
				throw new IllegalArgumentException("Stopwatch source class must have public constructor or public setter with Manager argument (used class " + monitorClass.getName() + ")", e);
			}
		}
		return stopwatchSource;
	}

	/**
	 * Returns RequestReporter for the class specified for context parameter {@link SimonServletFilter#INIT_PARAM_REQUEST_REPORTER_CLASS}.
	 */
	public static RequestReporter initRequestReporter(FilterConfig filterConfig) {
		String className = filterConfig.getInitParameter(SimonServletFilter.INIT_PARAM_REQUEST_REPORTER_CLASS);

		if (className == null) {
			return new DefaultRequestReporter();
		} else {
			try {
				return (RequestReporter) Class.forName(className).newInstance();
			} catch (ClassNotFoundException classNotFoundException) {
				throw new IllegalArgumentException("Invalid Request reporter class name", classNotFoundException);
			} catch (InstantiationException instantiationException) {
				throw new IllegalArgumentException("Invalid Request reporter class name", instantiationException);
			} catch (IllegalAccessException illegalAccessException) {
				throw new IllegalArgumentException("Invalid Request reporter class name", illegalAccessException);
			}
		}
	}
}
