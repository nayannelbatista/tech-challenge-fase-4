package br.com.fiap.feedback.domain;

import java.time.Instant;
import java.util.UUID;

public final class FeedbackRules {
    public static final int NOTA_CRITICA_MAXIMA = 4;
    private FeedbackRules() {}

    public static Feedback criar(String descricao, Integer nota, Instant data) {
        if (descricao == null || descricao.isBlank()) throw new IllegalArgumentException("descricao é obrigatória");
        if (descricao.length() > 4000) throw new IllegalArgumentException("descricao deve ter no máximo 4000 caracteres");
        if (nota == null || nota < 0 || nota > 10) throw new IllegalArgumentException("nota deve ser um inteiro entre 0 e 10");
        var urgencia = nota <= NOTA_CRITICA_MAXIMA ? Feedback.Urgencia.CRITICA : Feedback.Urgencia.NORMAL;
        return new Feedback(UUID.randomUUID().toString(), descricao.trim(), nota, urgencia, data);
    }
}
