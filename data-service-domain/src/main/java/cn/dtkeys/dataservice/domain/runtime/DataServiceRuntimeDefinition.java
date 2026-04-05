package cn.dtkeys.dataservice.domain.runtime;

import cn.dtkeys.dataservice.domain.model.DSCachePolicy;
import cn.dtkeys.dataservice.domain.model.DSDefinition;
import cn.dtkeys.dataservice.domain.model.DSField;
import cn.dtkeys.dataservice.domain.model.DSParam;

import java.util.List;

public record DataServiceRuntimeDefinition(DSDefinition definition,
                                           List<DataServiceRuntimeSource> sources,
                                           List<DSParam> params,
                                           List<DSField> fields,
                                           DSCachePolicy cachePolicy) {
}
