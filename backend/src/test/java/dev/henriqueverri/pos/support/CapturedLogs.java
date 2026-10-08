package dev.henriqueverri.pos.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

/**
 * Every log event of the application while open, in memory, with its MDC and key-value fields as
 * data. Tests assert on fields and context rather than on rendered text.
 */
public final class CapturedLogs implements AutoCloseable {

  private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
  private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();
  private final AppenderBase<ILoggingEvent> appender =
      new AppenderBase<>() {
        @Override
        protected void append(ILoggingEvent event) {
          // Copies the MDC now: it is read lazily and is cleared when the work ends.
          event.prepareForDeferredProcessing();
          events.add(event);
        }
      };

  private CapturedLogs() {}

  public static CapturedLogs start() {
    CapturedLogs logs = new CapturedLogs();
    logs.appender.start();
    logs.root.addAppender(logs.appender);
    return logs;
  }

  public List<ILoggingEvent> events() {
    return List.copyOf(events);
  }

  public List<ILoggingEvent> from(Class<?> logger) {
    return events.stream().filter(e -> e.getLoggerName().equals(logger.getName())).toList();
  }

  public static Map<String, Object> fields(ILoggingEvent event) {
    Map<String, Object> fields = new LinkedHashMap<>();
    List<KeyValuePair> pairs = event.getKeyValuePairs();
    if (pairs != null) {
      pairs.forEach(pair -> fields.put(pair.key, pair.value));
    }
    return fields;
  }

  /** Everything an appender could write: message, MDC, fields and exceptions, as one string. */
  public String rendered() {
    StringBuilder out = new StringBuilder();
    for (ILoggingEvent event : events) {
      out.append(event.getFormattedMessage())
          .append(' ')
          .append(event.getMDCPropertyMap())
          .append(' ')
          .append(fields(event));
      for (IThrowableProxy error = event.getThrowableProxy();
          error != null;
          error = error.getCause()) {
        out.append(' ').append(error.getClassName()).append(": ").append(error.getMessage());
        for (StackTraceElementProxy frame : error.getStackTraceElementProxyArray()) {
          out.append(' ').append(frame.getSTEAsString());
        }
      }
      out.append('\n');
    }
    return out.toString();
  }

  @Override
  public void close() {
    root.detachAppender(appender);
    appender.stop();
  }
}
