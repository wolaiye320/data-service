package cn.dtkeys.dataservice.federation.executor;

public record PredefinedJoinConfig(String joinType,
                                   String lookupParam,
                                   String lookupSourceColumn,
                                   String parentJoinField,
                                   String childJoinField,
                                   String childSqlTemplate) {
}
