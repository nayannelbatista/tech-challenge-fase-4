package br.com.fiap.feedback.lambda;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class DynamoDbQueryPaginator {
    private DynamoDbQueryPaginator() {}

    static List<Map<String, AttributeValue>> queryAll(
            QueryRequest initialRequest, Function<QueryRequest, QueryResponse> query) {
        var items = new ArrayList<Map<String, AttributeValue>>();
        var request = initialRequest;
        while (true) {
            var response = query.apply(request);
            items.addAll(response.items());
            var lastEvaluatedKey = response.lastEvaluatedKey();
            if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) return List.copyOf(items);
            request = initialRequest.toBuilder().exclusiveStartKey(lastEvaluatedKey).build();
        }
    }
}
