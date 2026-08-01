package edu.university.plis.student.client;

import edu.university.plis.shared.model.StudentEventType;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public final class VisibleActivityMonitor implements AutoCloseable {
    private static final Set<String> BROWSERS = Set.of(
            "chrome", "msedge", "firefox", "brave", "opera", "vivaldi", "safari");
    private static final Set<String> AI_TOOLS = Set.of(
            "chatgpt", "copilot", "claude", "gemini", "cursor");

    private final BiConsumer<StudentEventType, String> recorder;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "plis-visible-activity-monitor");
        thread.setDaemon(true);
        return thread;
    });
    private Set<String> previousBrowsers = Set.of();
    private Set<String> previousAiTools = Set.of();

    public VisibleActivityMonitor(BiConsumer<StudentEventType, String> recorder) {
        this.recorder = recorder;
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::sample, 0, 5, TimeUnit.SECONDS);
    }

    private void sample() {
        try {
            Set<String> processes = new TreeSet<>();
            try (Stream<ProcessHandle> handles = ProcessHandle.allProcesses()) {
                handles.forEach(process -> process.info().command()
                        .map(this::safeProcessName)
                        .filter(name -> !name.isBlank())
                        .ifPresent(processes::add));
            }
            Set<String> browsers = matching(processes, BROWSERS);
            Set<String> aiTools = matching(processes, AI_TOOLS);
            reportTransition(previousBrowsers, browsers,
                    StudentEventType.BROWSER_OPEN, StudentEventType.BROWSER_CLOSED, "Browser applications");
            reportTransition(previousAiTools, aiTools,
                    StudentEventType.AI_TOOL_OPEN, StudentEventType.AI_TOOL_CLOSED, "AI applications");
            previousBrowsers = browsers;
            previousAiTools = aiTools;
        } catch (RuntimeException ignored) {
            // Process visibility differs by operating system and account permissions.
        }
    }

    private Set<String> matching(Set<String> processes, Set<String> knownNames) {
        Set<String> matches = new TreeSet<>();
        for (String process : processes) {
            knownNames.stream().filter(process::contains).findFirst().ifPresent(ignored -> matches.add(process));
        }
        return Set.copyOf(matches);
    }

    private void reportTransition(
            Set<String> previous,
            Set<String> current,
            StudentEventType opened,
            StudentEventType closed,
            String label) {
        if (previous.isEmpty() && !current.isEmpty()) {
            recorder.accept(opened, label + " detected: " + String.join(", ", current));
        } else if (!previous.isEmpty() && current.isEmpty()) {
            recorder.accept(closed, label + " no longer detected");
        } else if (!previous.equals(current) && !current.isEmpty()) {
            recorder.accept(opened, label + " changed: " + String.join(", ", current));
        }
    }

    private String safeProcessName(String command) {
        try {
            String fileName = Path.of(command).getFileName().toString().toLowerCase(Locale.ROOT);
            int extension = fileName.lastIndexOf('.');
            return extension > 0 ? fileName.substring(0, extension) : fileName;
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
