package edu.university.plis.student.client;

import edu.university.plis.student.model.CompilationResult;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InMemoryJavaCompiler {
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "\\bpublic\\s+(?:final\\s+)?(?:class|record|interface|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    public CompilationResult check(String sourceCode) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new CompilationResult(false, "A JDK is required for local compilation checks.");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager standardManager = compiler.getStandardFileManager(
                diagnostics, Locale.getDefault(), StandardCharsets.UTF_8);
             MemoryFileManager memoryManager = new MemoryFileManager(standardManager)) {
            JavaFileObject source = new SourceFile(className(sourceCode), sourceCode);
            JavaCompiler.CompilationTask task = compiler.getTask(null, memoryManager, diagnostics,
                    List.of("-proc:none", "--release", "17"), null, List.of(source));
            boolean successful = Boolean.TRUE.equals(task.call());
            if (successful) {
                return new CompilationResult(true, "Compilation successful. This run check does not execute student code.");
            }
            String output = diagnostics.getDiagnostics().stream()
                    .limit(12)
                    .map(this::formatDiagnostic)
                    .reduce((first, second) -> first + System.lineSeparator() + second)
                    .orElse("Compilation failed.");
            return new CompilationResult(false, output);
        } catch (Exception exception) {
            return new CompilationResult(false, "Compilation check failed: " + exception.getMessage());
        }
    }

    private String className(String sourceCode) {
        Matcher matcher = PUBLIC_TYPE.matcher(sourceCode == null ? "" : sourceCode);
        return matcher.find() ? matcher.group(1) : "Main";
    }

    private String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        return "line " + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(Locale.getDefault());
    }

    private static final class SourceFile extends SimpleJavaFileObject {
        private final String sourceCode;

        private SourceFile(String className, String sourceCode) {
            super(URI.create("string:///" + className + Kind.SOURCE.extension), Kind.SOURCE);
            this.sourceCode = sourceCode == null ? "" : sourceCode;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return sourceCode;
        }
    }

    private static final class ByteCodeFile extends SimpleJavaFileObject {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private ByteCodeFile(String className, Kind kind) {
            super(URI.create("bytes:///" + className.replace('.', '/') + kind.extension), kind);
        }

        @Override
        public OutputStream openOutputStream() {
            return output;
        }
    }

    private static final class MemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private MemoryFileManager(StandardJavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            return new ByteCodeFile(className, kind);
        }
    }
}
