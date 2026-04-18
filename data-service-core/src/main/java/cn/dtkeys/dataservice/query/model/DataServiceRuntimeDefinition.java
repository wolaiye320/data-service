package cn.dtkeys.dataservice.query.model;

import cn.dtkeys.dataservice.service.model.DSCachePolicy;
import cn.dtkeys.dataservice.service.model.DSDefinition;
import cn.dtkeys.dataservice.service.model.DSField;
import cn.dtkeys.dataservice.service.model.DSParam;

import java.util.List;

public record DataServiceRuntimeDefinition(DSDefinition definition,
                                           List<DataServiceRuntimeSource> sources,
                                           List<DSParam> params,
                                           List<DSField> fields,
                                           DSCachePolicy cachePolicy) {
}
