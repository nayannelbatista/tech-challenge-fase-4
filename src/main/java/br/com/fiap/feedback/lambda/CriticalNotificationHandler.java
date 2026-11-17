package br.com.fiap.feedback.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import java.util.Map;

public class CriticalNotificationHandler implements RequestHandler<Map<String, Object>, Void> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SnsClient sns = SnsClient.create();
    @Override public Void handleRequest(Map<String, Object> event, Context context) {
        try {
            JsonNode detail = JSON.valueToTree(event.get("detail"));
            var message = "Feedback crítico recebido\nDescrição: " + detail.path("descricao").asText()
                    + "\nUrgência: " + detail.path("urgencia").asText()
                    + "\nData: " + detail.path("data").asText();
            sns.publish(PublishRequest.builder().topicArn(System.getenv("ADMIN_TOPIC_ARN"))
                    .subject("Feedback crítico").message(message).build());
        } catch (Exception e) { context.getLogger().log("Falha na notificação: " + e.getClass().getSimpleName()); throw new RuntimeException(e); }
        return null;
    }
}
