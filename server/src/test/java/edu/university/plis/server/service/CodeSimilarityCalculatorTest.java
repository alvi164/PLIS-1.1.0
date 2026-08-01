package edu.university.plis.server.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeSimilarityCalculatorTest {
    private final CodeSimilarityCalculator calculator = new CodeSimilarityCalculator();

    @Test
    void detectsEquivalentCodeAfterIdentifierAndLiteralChanges() {
        String first = """
                public class Main {
                    public static void main(String[] args) {
                        int total = 0;
                        for (int index = 0; index < 100; index++) {
                            total += index;
                        }
                        System.out.println(total);
                    }
                }
                """;
        String renamed = """
                public class Solution {
                    public static void main(String[] values) {
                        int answer = 0;
                        for (int cursor = 0; cursor < 500; cursor++) {
                            answer += cursor;
                        }
                        System.out.println(answer);
                    }
                }
                """;

        assertThat(calculator.calculate(first, renamed)).isGreaterThanOrEqualTo(0.95);
    }

    @Test
    void rejectsTinyOrStructurallyDifferentSamples() {
        assertThat(calculator.calculate("return 1;", "return 1;")).isZero();

        String sorting = """
                public class Main { public static void main(String[] a) {
                    int[] data = { 4, 3, 2, 1 };
                    for (int i = 0; i < data.length; i++) {
                        for (int j = i + 1; j < data.length; j++) {
                            if (data[j] < data[i]) { int t=data[i]; data[i]=data[j]; data[j]=t; }
                        }
                    }
                }}
                """;
        String recursion = """
                public class Main { static int fib(int n) {
                    if (n <= 1) return n;
                    return fib(n - 1) + fib(n - 2);
                }
                public static void main(String[] a) {
                    System.out.println(fib(12));
                }}
                """;
        assertThat(calculator.calculate(sorting, recursion)).isLessThan(IntegrityRules.SIMILARITY_THRESHOLD);
    }
}
