package br.com.fiap.feedback.lambda;

final class EventBridgePublicationException extends RuntimeException {
    EventBridgePublicationException(String message) { super(message); }
    EventBridgePublicationException(String message, Throwable cause) { super(message, cause); }
}
