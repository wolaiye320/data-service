package cn.dtkeys.dataservice.query.model;

import cn.dtkeys.dataservice.datasource.model.DSCatalog;
import cn.dtkeys.dataservice.datasource.model.DSConnection;
import cn.dtkeys.dataservice.service.model.DSSource;

public record DataServiceRuntimeSource(DSSource source,
                                       DSConnection connection,
                                       DSCatalog catalog) {
}
