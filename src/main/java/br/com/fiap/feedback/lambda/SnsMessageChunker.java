package br.com.fiap.feedback.lambda;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SnsMessageChunker {
    static final int MAX_MESSAGE_BYTES = 240 * 1024;
    private static final int MAX_PART_NUMBER = 999_999;

    private SnsMessageChunker() {}

    static List<String> split(String header, List<String> lines) {
        return split(header, lines, MAX_MESSAGE_BYTES);
    }

    static List<String> split(String header, List<String> lines, int maxBytes) {
        var partPrefix = header + "\nParte 000001 de 000001\n";
        if (utf8Length(partPrefix) > maxBytes) throw new IllegalArgumentException("Cabeçalho excede o limite da mensagem");

        var groups = new ArrayList<List<String>>();
        var current = new ArrayList<String>();
        for (var line : lines) {
            var candidate = new ArrayList<>(current);
            candidate.add(line);
            if (utf8Length(message(partPrefix, candidate)) <= maxBytes) {
                current = candidate;
                continue;
            }
            if (current.isEmpty()) throw new IllegalArgumentException("Um item excede o limite de bytes da mensagem");
            groups.add(List.copyOf(current));
            current = new ArrayList<>(List.of(line));
            if (utf8Length(message(partPrefix, current)) > maxBytes)
                throw new IllegalArgumentException("Um item excede o limite de bytes da mensagem");
        }
        if (!current.isEmpty() || groups.isEmpty()) groups.add(List.copyOf(current));
        if (groups.size() > MAX_PART_NUMBER) throw new IllegalArgumentException("Relatório excede o número máximo de partes");

        var total = groups.size();
        var result = new ArrayList<String>(total);
        for (int i = 0; i < total; i++) {
            var partHeader = header + "\nParte " + sixDigits(i + 1) + " de " + sixDigits(total) + "\n";
            var text = message(partHeader, groups.get(i));
            if (utf8Length(text) > maxBytes) throw new IllegalArgumentException("Parte do relatório excede o limite de bytes da mensagem");
            result.add(text);
        }
        return List.copyOf(result);
    }

    private static String message(String prefix, List<String> lines) {
        return prefix + String.join("\n", lines);
    }

    private static int utf8Length(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String sixDigits(int value) {
        return String.format(Locale.ROOT, "%06d", value);
    }
}
