package br.com.fiap.feedback.lambda;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SnsMessageChunkerTest {
    @Test void mantemRelatorioPequenoEmUmaMensagem() {
        var messages = SnsMessageChunker.split("Resumo", List.of("- avaliação ótima"));

        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("Parte 000001 de 000001"));
        assertTrue(messages.getFirst().endsWith("- avaliação ótima"));
    }

    @Test void divideEmPartesSequenciaisRespeitandoBytesUtf8() {
        var entries = List.of("ação-áéíóú-😀", "feedback-2", "feedback-3", "feedback-4");
        var messages = SnsMessageChunker.split("Resumo", entries, 56);

        assertTrue(messages.size() > 1);
        for (int i = 0; i < messages.size(); i++) {
            assertTrue(messages.get(i).getBytes(StandardCharsets.UTF_8).length <= 56);
            assertTrue(messages.get(i).contains(String.format("Parte %06d de %06d", i + 1, messages.size())));
        }
        for (var entry : entries) assertTrue(messages.stream().anyMatch(message -> message.contains(entry)));
    }

    @Test void rejeitaItemQueNaoCabeInteiroEmUmaMensagem() {
        assertThrows(IllegalArgumentException.class,
                () -> SnsMessageChunker.split("Resumo", List.of("item grande"), 30));
    }

    @Test void usaMargemAbaixoDoLimitePadraoSns() {
        assertTrue(SnsMessageChunker.MAX_MESSAGE_BYTES < 256 * 1024);
    }
}
