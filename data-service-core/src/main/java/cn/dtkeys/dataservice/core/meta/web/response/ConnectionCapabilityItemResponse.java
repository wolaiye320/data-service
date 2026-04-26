package cn.dtkeys.dataservice.core.meta.web.response;

public record ConnectionCapabilityItemResponse(
        String capabilityCode,
        String capabilityValue,
        String capabilityDetailJson,
        String scope,
        String scopeValue,
        String status,
        String remark
) {
}
