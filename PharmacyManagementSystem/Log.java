package PharmacyManagementSystem;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

enum Level {
    Trace,
    Debug,
    Info,
    Warning,
    Error,
    TUI, // Terminal UI
    Audit,
}

public class Log {
    public static final Level LOG_LEVEL = Level.Trace;
    private static LocalDate log_date;
    private static FileWriter writer;
    private static File file;

    public static void init() {
        try {
            log_date = LocalDate.now();
            file = new File(log_date + "ActivityLog.log");
            file.createNewFile();
            writer = new FileWriter(file, true);
        } catch (Exception e) {
            Log.error("Exception in logger init: " + e);
        }
    }

    public static void clean() {
        try {
            writer.close();
        } catch (Exception e) {
            Log.error("Exception in logger clean: " + e);
        }
    }

    public static void logStack() {
        System.out.println(levelPrefix(Level.Trace) + "Stack Trace: " + getCaller());
    }

    public static void trace(String message) {
        log(Level.Trace, true, message);
    }

    public static void debug(String message) {
        log(Level.Debug, true, message);
    }

    public static void info(String message) {
        log(Level.Info, true, message);
    }

    public static void warning(String message) {
        log(Level.Warning, true, message);
    }

    public static void error(String message) {
        log(Level.Error, true, message);
    }

    public static void tui(String message) {
        log(Level.TUI, false, "\t" + message);
    }

    public static void audit(String message) {
        String text =
                message
                        + " - "
                        + LocalDateTime.now()
                        + " - Initiated by user: "
                        + Backend.get().getLoggedIn();
        writeFile(text);
        log(Level.Audit, true, text);
    }

    public static void auditAnonymous(String message) {
        log(Level.Audit, true, message);
    }

    private static void checkDate() {
        try {
            if (!log_date.equals(LocalDate.now())) {
                writer.close();
                log_date = LocalDate.now();
                file = new File(log_date + "ActivityLog.log");
                file.createNewFile();
                writer = new FileWriter(file, true);
            }
        } catch (Exception e) {
            Log.error("Exception in logger checkDate: " + e);
        }
    }

    private static void writeFile(String message) {
        try {
            writer.write(message + "\n");
            checkDate();
        } catch (Exception e) {
            Log.error("Exception in logger writeFile: " + e);
        }
    }

    private static void log(Level log_level, boolean show_level, String message) {
        if (log_level.ordinal() >= LOG_LEVEL.ordinal()) {
            System.out.println((show_level ? levelPrefix(log_level) : "") + message);
        }
    }

    private static String getCaller() {
        return StackWalker.getInstance()
                .walk(
                        frames ->
                                frames.skip(1)
                                        .map(frame -> frame.getMethodName())
                                        .collect(Collectors.joining("->")));
    }

    private static String levelPrefix(Level log_level) {
        switch (log_level) {
            case Trace:
                return "[TRACE]\t";
            case Debug:
                return "[DEBUG]\t";
            case Info:
                return "[INFO]\t";
            case Warning:
                return "[WARNING]\t";
            case Error:
                return "[ERROR]\t";
            case TUI:
                return "[TUI]\t";
            case Audit:
                return "[AUDIT]\t";
            default:
                throw new IllegalArgumentException("Invalid levelPrefix log level: " + log_level);
        }
    }
}
