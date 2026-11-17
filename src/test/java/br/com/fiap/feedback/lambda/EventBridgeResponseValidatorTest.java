package br.com.fiap.feedback.lambda;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EventBridgeResponseValidatorTest {
    @Test void aceitaRespostaSemFalhas() {
        var response = PutEventsResponse.builder().failedEntryCount(0)
                .entries(PutEventsResultEntry.builder().eventId("event-id").build()).build();

        assertDoesNotThrow(() -> EventBridgeResponseValidator.requireSuccess(response));
    }

    @Test void detectaFailedEntryCountMesmoSemDetalheIndividual() {
        var response = PutEventsResponse.builder().failedEntryCount(1).build();

        var exception = assertThrows(EventBridgePublicationException.class,
                () -> EventBridgeResponseValidator.requireSuccess(response));
        assertTrue(exception.getMessage().contains("FailedEntryCount=1"));
    }

    @Test void detectaErroIndividualMesmoSeContagemVierZerada() {
        var response = PutEventsResponse.builder().failedEntryCount(0)
                .entries(PutEventsResultEntry.builder().errorCode("InternalFailure")
                        .errorMessage("mensagem técnica").build()).build();

        var exception = assertThrows(EventBridgePublicationException.class,
                () -> EventBridgeResponseValidator.requireSuccess(response));
        assertTrue(exception.getMessage().contains("InternalFailure"));
        assertFalse(exception.getMessage().contains("mensagem técnica"));
    }

    @Test void permiteRetryApenasParaFalhasTransitóriasComDetalheConsistente() {
        var throttled = PutEventsResponse.builder().failedEntryCount(1)
                .entries(PutEventsResultEntry.builder().errorCode("ThrottlingException").build()).build();
        var permanent = PutEventsResponse.builder().failedEntryCount(1)
                .entries(PutEventsResultEntry.builder().errorCode("AccessDeniedException").build()).build();
        var inconsistent = PutEventsResponse.builder().failedEntryCount(0)
                .entries(PutEventsResultEntry.builder().errorCode("InternalFailure").build()).build();

        assertTrue(EventBridgeResponseValidator.canRetry(throttled));
        assertFalse(EventBridgeResponseValidator.canRetry(permanent));
        assertFalse(EventBridgeResponseValidator.canRetry(inconsistent));
    }

    @Test void tentaNovamenteFalhaTransitóriaEParaQuandoPublicaçãoTemSucesso() {
        var calls = new AtomicInteger();
        var request = PutEventsRequest.builder().build();

        assertDoesNotThrow(() -> EventBridgeResponsePublisher.publishWithRetries(request, ignored -> {
            if (calls.getAndIncrement() == 0) {
                return PutEventsResponse.builder().failedEntryCount(1)
                        .entries(PutEventsResultEntry.builder().errorCode("InternalFailure").build()).build();
            }
            return PutEventsResponse.builder().failedEntryCount(0).build();
        }, 3, ignored -> {}));
        assertEquals(2, calls.get());
    }

    @Test void naoRepeteFalhaPermanente() {
        var calls = new AtomicInteger();
        var request = PutEventsRequest.builder().build();

        assertThrows(EventBridgePublicationException.class,
                () -> EventBridgeResponsePublisher.publishWithRetries(request, ignored -> {
                    calls.incrementAndGet();
                    return PutEventsResponse.builder().failedEntryCount(1)
                            .entries(PutEventsResultEntry.builder().errorCode("AccessDeniedException").build()).build();
                }, 3, ignored -> {}));
        assertEquals(1, calls.get());
    }

    @Test void limitaTentativasParaFalhaTransitóriaPersistente() {
        var calls = new AtomicInteger();
        var request = PutEventsRequest.builder().build();

        assertThrows(EventBridgePublicationException.class,
                () -> EventBridgeResponsePublisher.publishWithRetries(request, ignored -> {
                    calls.incrementAndGet();
                    return PutEventsResponse.builder().failedEntryCount(1)
                            .entries(PutEventsResultEntry.builder().errorCode("ThrottlingException").build()).build();
                }, 3, ignored -> {}));
        assertEquals(3, calls.get());
    }
}
