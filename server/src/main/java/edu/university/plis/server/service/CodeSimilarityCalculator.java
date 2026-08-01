package edu.university.plis.server.service;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CodeSimilarityCalculator {
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*");
    private static final Pattern TOKEN = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|[A-Za-z_$][A-Za-z0-9_$]*|\\d+(?:\\.\\d+)?|==|!=|<=|>=|&&|\\|\\||\\+\\+|--|[-+*/%<>=!&|^~?:.;,(){}\\[\\]]");
    private static final Set<String> LANGUAGE_WORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public", "return",
            "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "true", "false", "null", "var", "record",
            "sealed", "permits", "yield");

    public double calculate(String firstCode, String secondCode) {
        List<String> firstTokens = normalizedTokens(firstCode);
        List<String> secondTokens = normalizedTokens(secondCode);
        if (firstTokens.size() < 20 || secondTokens.size() < 20) {
            return 0.0;
        }
        Map<String, Integer> firstFrequency = frequencies(firstTokens);
        Map<String, Integer> secondFrequency = frequencies(secondTokens);
        double dotProduct = 0.0;
        for (Map.Entry<String, Integer> token : firstFrequency.entrySet()) {
            dotProduct += token.getValue() * secondFrequency.getOrDefault(token.getKey(), 0);
        }
        double firstMagnitude = magnitude(firstFrequency);
        double secondMagnitude = magnitude(secondFrequency);
        return dotProduct / (firstMagnitude * secondMagnitude);
    }

    List<String> normalizedTokens(String code) {
        if (code == null || code.isBlank()) {
            return List.of();
        }
        String withoutComments = COMMENTS.matcher(code).replaceAll(" ");
        Matcher matcher = TOKEN.matcher(withoutComments);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (token.startsWith("\"") || token.startsWith("'")) {
                tokens.add("STRING");
            } else if (Character.isDigit(token.charAt(0))) {
                tokens.add("NUMBER");
            } else if (Character.isJavaIdentifierStart(token.charAt(0)) && !LANGUAGE_WORDS.contains(token)) {
                tokens.add("IDENTIFIER");
            } else {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private Map<String, Integer> frequencies(List<String> tokens) {
        Map<String, Integer> frequencies = new HashMap<>();
        tokens.forEach(token -> frequencies.merge(token, 1, Integer::sum));
        return frequencies;
    }

    private double magnitude(Map<String, Integer> frequencies) {
        double squared = frequencies.values().stream()
                .mapToDouble(frequency -> (double) frequency * frequency)
                .sum();
        return Math.sqrt(squared);
    }
}
