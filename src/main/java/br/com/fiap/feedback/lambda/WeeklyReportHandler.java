package br.com.fiap.feedback.lambda;

import br.com.fiap.feedback.domain.Feedback;
import br.com.fiap.feedback.domain.WeeklySummary;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import java.time.*;
import java.util.*;

public class WeeklyReportHandler implements RequestHandler<Map<String, Object>, Void> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DynamoDbClient db = DynamoDbClient.create();
    private final SnsClient sns = SnsClient.create();
    @Override public Void handleRequest(Map<String, Object> event, Context context) {
        var hoje = LocalDate.now(ZoneOffset.UTC);
        var fim = hoje.minusDays(1);
        var inicio = fim.minusDays(6);
        var feedbacks = new ArrayList<Feedback>();
        for (var dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            var request = QueryRequest.builder().tableName(System.getenv("FEEDBACK_TABLE"))
                    .keyConditionExpression("dia = :dia").expressionAttributeValues(Map.of(":dia", AttributeValue.builder().s(dia.toString()).build())).build();
            var items = DynamoDbQueryPaginator.queryAll(request, db::query);
            items.forEach(item -> feedbacks.add(new Feedback(item.get("id").s(), item.get("descricao").s(),
                    Integer.parseInt(item.get("nota").n()), Feedback.Urgencia.valueOf(item.get("urgencia").s()), Instant.parse(item.get("data").s()))));
        }
        var resumo = WeeklySummary.from(inicio, fim, feedbacks);
        var header = "Relatório semanal: " + inicio + " a " + fim + " UTC\nTotal: " + resumo.total()
                + "\nMédia das notas: " + String.format(Locale.ROOT, "%.2f", resumo.mediaNotas())
                + "\nQuantidade por dia: " + resumo.quantidadePorDia()
                + "\nQuantidade por urgência: " + resumo.quantidadePorUrgencia()
                + "\nFeedbacks (data, urgência e descrição):";
        var linhas = feedbacks.isEmpty() ? List.of("Nenhum feedback") : feedbacks.stream()
                .map(f -> "- " + f.data() + " | " + f.urgencia() + " | nota " + f.nota() + " | " + f.descricao()).toList();
        var mensagens = SnsMessageChunker.split(header, linhas);
        for (var mensagem : mensagens) {
            sns.publish(PublishRequest.builder().topicArn(System.getenv("ADMIN_TOPIC_ARN"))
                    .subject("Relatório semanal de feedbacks").message(mensagem).build());
        }
        context.getLogger().log("Relatório semanal enviado; total=" + resumo.total() + "; mensagens=" + mensagens.size());
        return null;
    }
}
