package edu.university.plis.shared.model;

public enum ProgrammingLanguage {
    JAVA("Java", ".java", "Main.java", """
            public class Main {
                public static void main(String[] args) {
                    // Write your solution here.
                }
            }
            """),
    PYTHON("Python", ".py", "solution.py", "# Write your solution here.\n"),
    C("C", ".c", "main.c", """
            #include <stdio.h>

            int main(void) {
                // Write your solution here.
                return 0;
            }
            """),
    CPP("C++", ".cpp", "main.cpp", """
            #include <iostream>

            int main() {
                // Write your solution here.
                return 0;
            }
            """),
    JAVASCRIPT("JavaScript", ".js", "solution.js", "// Write your solution here.\n"),
    CSHARP("C#", ".cs", "Program.cs", """
            using System;

            public class Program {
                public static void Main() {
                    // Write your solution here.
                }
            }
            """),
    KOTLIN("Kotlin", ".kt", "Main.kt", """
            fun main() {
                // Write your solution here.
            }
            """);

    private final String displayName;
    private final String extension;
    private final String defaultFileName;
    private final String starterCode;

    ProgrammingLanguage(String displayName, String extension, String defaultFileName, String starterCode) {
        this.displayName = displayName;
        this.extension = extension;
        this.defaultFileName = defaultFileName;
        this.starterCode = starterCode;
    }

    public String displayName() { return displayName; }
    public String extension() { return extension; }
    public String defaultFileName() { return defaultFileName; }
    public String starterCode() { return starterCode; }

    @Override
    public String toString() { return displayName; }
}
