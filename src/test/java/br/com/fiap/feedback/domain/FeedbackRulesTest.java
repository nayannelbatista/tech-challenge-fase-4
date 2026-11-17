package br.com.fiap.feedback.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class FeedbackRulesTest {
    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
    @Test void notaQuatroEhCriticaEDescricaoEhNormalizada() {
        var f = FeedbackRules.criar("  atendimento ruim  ", 4, NOW);
        assertEquals("atendimento ruim", f.descricao());
        assertEquals(Feedback.Urgencia.CRITICA, f.urgencia());
    }
    @Test void notasAcimaDeQuatroSaoNormais() {
        assertEquals(Feedback.Urgencia.NORMAL, FeedbackRules.criar("bom", 5, NOW).urgencia());
        assertEquals(Feedback.Urgencia.NORMAL, FeedbackRules.criar("ótimo", 10, NOW).urgencia());
    }
    @Test void rejeitaNotaForaDoIntervaloEDescricaoVazia() {
        assertThrows(IllegalArgumentException.class, () -> FeedbackRules.criar("x", -1, NOW));
        assertThrows(IllegalArgumentException.class, () -> FeedbackRules.criar("x", 11, NOW));
        assertThrows(IllegalArgumentException.class, () -> FeedbackRules.criar("  ", 5, NOW));
    }
    @Test void resumoAgrupaDiasUrgenciaEMedia() {
        var a = FeedbackRules.criar("ruim", 2, Instant.parse("2026-11-15T10:00:00Z"));
        var b = FeedbackRules.criar("bom", 8, Instant.parse("2026-11-15T21:00:00Z"));
        var r = WeeklySummary.from(java.time.LocalDate.parse("2026-11-15"), java.time.LocalDate.parse("2026-11-16"), java.util.List.of(a,b));
        assertEquals(2, r.quantidadePorDia().get(java.time.LocalDate.parse("2026-11-15")));
        assertEquals(1, r.quantidadePorUrgencia().get(Feedback.Urgencia.CRITICA));
        assertEquals(5.0, r.mediaNotas());
    }
}
