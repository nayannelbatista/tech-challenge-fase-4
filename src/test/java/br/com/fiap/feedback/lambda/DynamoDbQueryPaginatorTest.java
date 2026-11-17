package br.com.fiap.feedback.lambda;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DynamoDbQueryPaginatorTest {
    @Test void segueLastEvaluatedKeyAteUltimaPagina() {
        var firstKey = Map.of("dia", AttributeValue.builder().s("2026-11-10").build(),
                "id", AttributeValue.builder().s("id-1").build());
        var page = new AtomicInteger();
        var request = QueryRequest.builder().tableName("feedback")
                .keyConditionExpression("dia = :dia")
                .expressionAttributeValues(Map.of(":dia", AttributeValue.builder().s("2026-11-10").build())).build();

        var items = DynamoDbQueryPaginator.queryAll(request, nextRequest -> {
            var call = page.getAndIncrement();
            if (call == 0) {
                assertTrue(nextRequest.exclusiveStartKey() == null || nextRequest.exclusiveStartKey().isEmpty());
                return QueryResponse.builder().items(List.of(Map.of("id", AttributeValue.builder().s("id-1").build())))
                        .lastEvaluatedKey(firstKey).build();
            }
            assertEquals(firstKey, nextRequest.exclusiveStartKey());
            assertEquals(request.keyConditionExpression(), nextRequest.keyConditionExpression());
            return QueryResponse.builder().items(List.of(Map.of("id", AttributeValue.builder().s("id-2").build()))).build();
        });

        assertEquals(2, page.get());
        assertEquals(2, items.size());
        assertEquals("id-1", items.get(0).get("id").s());
        assertEquals("id-2", items.get(1).get("id").s());
    }

    @Test void consultaUmaVezQuandoNaoHaMaisPaginas() {
        var calls = new AtomicInteger();
        var request = QueryRequest.builder().tableName("feedback").build();

        var items = DynamoDbQueryPaginator.queryAll(request, ignored -> {
            calls.incrementAndGet();
            return QueryResponse.builder().build();
        });

        assertEquals(1, calls.get());
        assertTrue(items.isEmpty());
    }
}
