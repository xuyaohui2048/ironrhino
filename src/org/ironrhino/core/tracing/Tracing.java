package org.ironrhino.core.tracing;

import java.io.Serializable;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.ironrhino.core.util.CheckedCallable;
import org.ironrhino.core.util.CheckedRunnable;
import org.springframework.http.HttpMessage;
import org.springframework.util.ClassUtils;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Tracing {

	private static volatile boolean enabled = ClassUtils.isPresent("io.opentracing.Tracer",
			Tracing.class.getClassLoader());

	private static final ThreadLocal<Throwable> throwable = new ThreadLocal<>();

	static void disable() {
		enabled = false;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static <T> T execute(String operationName, Callable<T> callable, Serializable... tags) throws Exception {
		if (!enabled || shouldSkip(tags))
			return callable.call();
		Span span = buildSpan(operationName, tags);
		try (Scope scope = GlobalTracer.get().activateSpan(span)) {
			return callable.call();
		} catch (Exception ex) {
			logError(ex);
			throw ex;
		} finally {
			span.finish();
		}
	}

	public static void execute(String operationName, Runnable runnable, Serializable... tags) {
		if (!enabled || shouldSkip(tags)) {
			runnable.run();
			return;
		}
		Span span = buildSpan(operationName, tags);
		try (Scope scope = GlobalTracer.get().activateSpan(span)) {
			runnable.run();
		} catch (Exception ex) {
			logError(ex);
			throw ex;
		} finally {
			span.finish();
		}
	}

	public static <T, E extends Throwable> T executeCheckedCallable(String operationName,
			CheckedCallable<T, E> callable, Serializable... tags) throws E {
		if (!enabled || shouldSkip(tags))
			return callable.call();
		Span span = buildSpan(operationName, tags);
		try (Scope scope = GlobalTracer.get().activateSpan(span)) {
			return callable.call();
		} catch (Throwable ex) {
			logError(ex);
			throw ex;
		} finally {
			span.finish();
		}
	}

	public static <E extends Throwable> void executeCheckedRunnable(String operationName, CheckedRunnable<E> runnable,
			Serializable... tags) throws E {
		if (!enabled || shouldSkip(tags)) {
			runnable.run();
			return;
		}
		Span span = buildSpan(operationName, tags);
		try (Scope scope = GlobalTracer.get().activateSpan(span)) {
			runnable.run();
		} catch (Exception ex) {
			logError(ex);
			throw ex;
		} finally {
			span.finish();
		}
	}

	public static <T> Callable<T> wrapAsync(String operationName, Callable<T> callable, Serializable... tags) {
		if (!enabled || shouldSkip(tags))
			return callable;
		ensureReportingActiveSpan();
		Span span = buildSpan(operationName, tags);
		span.setTag("async", true);
		return () -> {
			try (Scope scope = GlobalTracer.get().activateSpan(span)) {
				return callable.call();
			} catch (Exception ex) {
				logError(ex);
				throw ex;
			} finally {
				span.finish();
			}
		};
	}

	public static <T> Runnable wrapAsync(String operationName, Runnable runnable, Serializable... tags) {
		if (!enabled || shouldSkip(tags))
			return runnable;
		ensureReportingActiveSpan();
		Span span = buildSpan(operationName, tags);
		span.setTag("async", true);
		return () -> {
			try (Scope scope = GlobalTracer.get().activateSpan(span)) {
				runnable.run();
			} catch (Exception ex) {
				logError(ex);
				throw ex;
			} finally {
				span.finish();
			}
		};
	}

	public static <T, E extends Throwable> CheckedCallable<T, E> wrapAsyncCheckedCallable(String operationName,
			CheckedCallable<T, E> callable, Serializable... tags) {
		if (!enabled || shouldSkip(tags))
			return callable;
		ensureReportingActiveSpan();
		Span span = buildSpan(operationName, tags);
		span.setTag("async", true);
		return () -> {
			try (Scope scope = GlobalTracer.get().activateSpan(span)) {
				return callable.call();
			} catch (Exception ex) {
				logError(ex);
				throw ex;
			} finally {
				span.finish();
			}
		};
	}

	public static <E extends Throwable> CheckedRunnable<E> wrapAsyncCheckedRunnable(String operationName,
			CheckedRunnable<E> runnable, Serializable... tags) {
		if (!enabled || shouldSkip(tags))
			return runnable;
		ensureReportingActiveSpan();
		Span span = buildSpan(operationName, tags);
		span.setTag("async", true);
		return () -> {
			try (Scope scope = GlobalTracer.get().activateSpan(span)) {
				runnable.run();
			} catch (Exception ex) {
				logError(ex);
				throw ex;
			} finally {
				span.finish();
			}
		};
	}

	public static void logError(Throwable ex) {
		if (enabled) {
			Span span = GlobalTracer.get().activeSpan();
			if (span != null) {
				Tags.SAMPLING_PRIORITY.set(span, 1);
				Tags.ERROR.set(span, true);
				Throwable old = throwable.get();
				if (ex != old) {
					throwable.set(ex);
					Map<String, Object> map = new HashMap<>();
					map.put(Fields.EVENT, "error");
					map.put(Fields.ERROR_OBJECT, ex);
					map.put(Fields.MESSAGE, ex.getMessage());
					span.log(map);
				}
			}
		}
	}

	private static boolean shouldSkip(Serializable... tags) {
		boolean isComponent = false;
		Integer samplingPriority = null;
		for (int i = 0; i < tags.length; i += 2) {
			if (Tags.COMPONENT.getKey().equals(tags[i])) {
				isComponent = true;
			} else if (Tags.SAMPLING_PRIORITY.getKey().equals(tags[i])) {
				Serializable value = tags[i + 1];
				if (value instanceof Integer)
					samplingPriority = (Integer) value;
			}
		}
		return GlobalTracer.get().activeSpan() == null && isComponent
				|| (samplingPriority != null && samplingPriority < 0);
	}

	private static Span buildSpan(String operationName, Serializable... tags) {
		Tracer tracer = GlobalTracer.get();
		Span span = tracer.buildSpan(operationName).start();
		setTags(span, tags);
		return span;
	}

	private static void setTags(Span span, Serializable... tags) {
		if (tags.length > 0) {
			if (tags.length % 2 != 0)
				throw new IllegalArgumentException("Tags should be key value pair");
			for (int i = 0; i < tags.length / 2; i++) {
				String name = String.valueOf(tags[i * 2]);
				Serializable value = tags[i * 2 + 1];
				if (value instanceof Number)
					span.setTag(name, (Number) value);
				else if (value instanceof Boolean)
					span.setTag(name, (Boolean) value);
				else if (value != null)
					span.setTag(name, String.valueOf(value));
			}
		}
	}

	public static void ensureReportingActiveSpan() {
		// ensure current span be reported
		// see also org.ironrhino.core.tracing.DelegatingReporter
		Tracer tracer = GlobalTracer.get();
		Span current = tracer.activeSpan();
		if (current != null)
			Tags.SAMPLING_PRIORITY.set(current, 1);
	}

	public static void setTags(Serializable... tags) {
		if (Tracing.isEnabled()) {
			Tracer tracer = GlobalTracer.get();
			Span span = tracer.activeSpan();
			if (span != null)
				setTags(span, tags);
		}
	}

	public static void inject(HttpMessage httpMessage) throws Exception {
		if (enabled) {
			Tracer tracer = GlobalTracer.get();
			Span span = tracer.activeSpan();
			if (span != null)
				tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new SpringHttpMessageTextMap(httpMessage));
		}
	}

	public static void inject(org.apache.http.HttpMessage httpMessage) {
		if (enabled) {
			Tracer tracer = GlobalTracer.get();
			Span span = tracer.activeSpan();
			if (span != null)
				tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS,
						new HttpComponentsHttpMessageTextMap(httpMessage));
		}
	}

	public static void inject(HttpURLConnection connection) {
		if (enabled) {
			Tracer tracer = GlobalTracer.get();
			Span span = tracer.activeSpan();
			if (span != null)
				tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new HttpURLConnectionTextMap(connection));
		}
	}

}
