package br.com.fiap.feedback.lambda;

import br.com.fiap.feedback.domain.Feedback;
import br.com.fiap.feedback.domain.FeedbackRules;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

public class FeedbackHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DynamoDbClient db = DynamoDbClient.create();
    private final EventBridgeClient events = EventBridgeClient.create();
    private final String table = System.getenv("FEEDBACK_TABLE");
    private final String bus = System.getenv("EVENT_BUS_NAME");

    @Override public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            var input = JSON.readTree(request.getBody());
            if (input == null || !input.path("descricao").isTextual() || !input.path("nota").isIntegralNumber()
                    || !input.path("nota").canConvertToInt())
                return response(400, Map.of("erro", "Informe descricao como texto e nota como número inteiro"));
            var feedback = FeedbackRules.criar(input.path("descricao").asText(), input.path("nota").intValue(), Instant.now());
            var dia = feedback.data().atZone(ZoneOffset.UTC).toLocalDate().toString();
            db.putItem(PutItemRequest.builder().tableName(table).item(Map.of(
                    "dia", s(dia), "id", s(feedback.id()), "descricao", s(feedback.descricao()),
                    "nota", n(feedback.nota()), "urgencia", s(feedback.urgencia().name()), "data", s(feedback.data().toString()))).build());
            if (feedback.urgencia() == Feedback.Urgencia.CRITICA) {
                events.putEvents(PutEventsRequest.builder().entries(PutEventsRequestEntry.builder()
                        .eventBusName(bus).source("fiap.feedback").detailType("FeedbackCritico")
                        .detail(JSON.writeValueAsString(feedback)).build()).build());
            }
            return response(201, Map.of("id", feedback.id(), "urgencia", feedback.urgencia().name(), "data", feedback.data().toString()));
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) { return response(400, Map.of("erro", e.getMessage())); }
        catch (Exception e) {
            context.getLogger().log("Falha ao processar feedback: " + e.getClass().getSimpleName());
            return response(500, Map.of("erro", "Não foi possível registrar o feedback"));
        }
    }
    private static AttributeValue s(String v) { return AttributeValue.builder().s(v).build(); }
    private static AttributeValue n(int v) { return AttributeValue.builder().n(Integer.toString(v)).build(); }
    private static APIGatewayProxyResponseEvent response(int code, Object body) {
        try { return new APIGatewayProxyResponseEvent().withStatusCode(code).withHeaders(Map.of("Content-Type", "application/json; charset=utf-8")).withBody(JSON.writeValueAsString(body)); }
        catch (Exception ignored) { return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("{}"); }
    }
}
