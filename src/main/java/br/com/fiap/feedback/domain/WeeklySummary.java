package br.com.fiap.feedback.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record WeeklySummary(LocalDate inicio, LocalDate fim, long total, double mediaNotas,
                            Map<LocalDate, Long> quantidadePorDia,
                            Map<Feedback.Urgencia, Long> quantidadePorUrgencia,
                            List<Feedback> feedbacks) {
    public static WeeklySummary from(LocalDate inicio, LocalDate fim, List<Feedback> feedbacks) {
        var dias = new java.util.TreeMap<LocalDate, Long>();
        for (var d = inicio; !d.isAfter(fim); d = d.plusDays(1)) dias.put(d, 0L);
        var urgencias = new java.util.EnumMap<Feedback.Urgencia, Long>(Feedback.Urgencia.class);
        for (var u : Feedback.Urgencia.values()) urgencias.put(u, 0L);
        int soma = 0;
        for (var f : feedbacks) {
            var dia = f.data().atZone(java.time.ZoneOffset.UTC).toLocalDate();
            dias.merge(dia, 1L, Long::sum);
            urgencias.merge(f.urgencia(), 1L, Long::sum);
            soma += f.nota();
        }
        return new WeeklySummary(inicio, fim, feedbacks.size(), feedbacks.isEmpty() ? 0.0 : (double) soma / feedbacks.size(),
                java.util.Collections.unmodifiableMap(dias), java.util.Collections.unmodifiableMap(urgencias), List.copyOf(feedbacks));
    }
}
