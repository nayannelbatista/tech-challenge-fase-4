package br.com.fiap.feedback.lambda;

import software.amazon.awssdk.services.eventbridge.model.EventBridgeException;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import java.util.function.IntConsumer;
import java.util.function.Function;

final class EventBridgeResponsePublisher {
    private EventBridgeResponsePublisher() {}

    static void publishWithRetries(PutEventsRequest request,
                                   Function<PutEventsRequest, PutEventsResponse> publish,
                                   int maxAttempts,
                                   IntConsumer pauseBeforeRetry) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts deve ser positivo");
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            var response = invoke(request, publish);
            if (EventBridgeResponseValidator.isSuccessful(response)) return;
            if (attempt == maxAttempts || !EventBridgeResponseValidator.canRetry(response)) {
                EventBridgeResponseValidator.requireSuccess(response);
            }
            pauseBeforeRetry.accept(attempt);
        }
    }

    private static PutEventsResponse invoke(PutEventsRequest request,
                                            Function<PutEventsRequest, PutEventsResponse> publish) {
        try {
            return publish.apply(request);
        } catch (EventBridgeException e) {
            var code = e.awsErrorDetails() == null ? e.getClass().getSimpleName() : e.awsErrorDetails().errorCode();
            throw new EventBridgePublicationException("Falha na chamada PutEvents: " + code, e);
        } catch (RuntimeException e) {
            throw new EventBridgePublicationException("Falha na chamada PutEvents: " + e.getClass().getSimpleName(), e);
        }
    }
}
