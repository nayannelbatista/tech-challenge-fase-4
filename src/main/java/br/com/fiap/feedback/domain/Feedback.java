package br.com.fiap.feedback.domain;

import java.time.Instant;

public record Feedback(String id, String descricao, int nota, Urgencia urgencia, Instant data) {
    public enum Urgencia { CRITICA, NORMAL }
}
