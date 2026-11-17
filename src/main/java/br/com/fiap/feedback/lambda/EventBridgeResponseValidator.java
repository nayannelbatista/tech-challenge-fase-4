package br.com.fiap.feedback.lambda;

import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import java.util.List;
import java.util.Set;

final class EventBridgeResponseValidator {
    private static final Set<String> RETRYABLE_ERROR_CODES = Set.of("InternalFailure", "ThrottlingException");
    private EventBridgeResponseValidator() {}

    static boolean isSuccessful(PutEventsResponse response) {
        return failedEntryCount(response) == 0 && errorCodes(response).isEmpty();
    }

    static boolean canRetry(PutEventsResponse response) {
        var codes = errorCodes(response);
        return !codes.isEmpty()
                && failedEntryCount(response) == codes.size()
                && codes.stream().allMatch(RETRYABLE_ERROR_CODES::contains);
    }

    static void requireSuccess(PutEventsResponse response) {
        var failedCount = failedEntryCount(response);
        var failedEntries = errorCodes(response);
        if (!isSuccessful(response)) {
            var codes = failedEntries.isEmpty() ? List.of("código não informado") : failedEntries;
            throw new EventBridgePublicationException(
                    "FailedEntryCount=" + failedCount + ", ErrorCodes=" + String.join(",", codes));
        }
    }

    private static int failedEntryCount(PutEventsResponse response) {
        return response.failedEntryCount() == null ? 0 : response.failedEntryCount();
    }

    private static List<String> errorCodes(PutEventsResponse response) {
        return response.entries().stream()
                .filter(entry -> entry.errorCode() != null)
                .map(entry -> entry.errorCode())
                .toList();
    }
}
