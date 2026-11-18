package br.com.fiap.feedback.lambda;

import br.com.fiap.feedback.domain.Feedback;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedbackHandlerTest {
    @Test
    void serializaInstanteComoTextoIso8601SemModuloJsr310() throws Exception {
        var feedback = new Feedback("feedback-id", "Atendimento ruim", 2,
                Feedback.Urgencia.CRITICA, Instant.parse("2026-11-17T15:30:45Z"));

        var detail = new ObjectMapper().readTree(FeedbackHandler.writeFeedback(feedback));

        assertEquals("feedback-id", detail.path("id").asText());
        assertEquals("CRITICA", detail.path("urgencia").asText());
        assertEquals("2026-11-17T15:30:45Z", detail.path("data").asText());
    }
}
