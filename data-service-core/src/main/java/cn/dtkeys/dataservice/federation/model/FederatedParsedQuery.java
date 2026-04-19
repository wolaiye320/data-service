package cn.dtkeys.dataservice.federation.model;

import java.util.List;

public record FederatedParsedQuery(
    String originalSql,
    List<String> selectedFields,
    List<String> sourceTables,
    List<FederatedSourceReference> sourceReferences,
    String whereClause,
    boolean joinQuery
) {
    public FederatedParsedQuery(String originalSql,
                                List<String> selectedFields,
                                List<String> sourceTables,
                                String whereClause,
                                boolean joinQuery) {
        this(
            originalSql,
            selectedFields,
            sourceTables,
            sourceTables.stream()
                .map(source -> new FederatedSourceReference(source, source))
                .toList(),
            whereClause,
            joinQuery
        );
    }
}
