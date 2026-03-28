package cn.dtkeys.dataservice.federation.model;

import java.util.List;

public record FederatedParsedQuery(
    String originalSql,
    List<String> selectedFields,
    List<String> sourceTables,
    String whereClause,
    boolean joinQuery
) {
}
