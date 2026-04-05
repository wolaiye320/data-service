package cn.dtkeys.dataservice.domain.runtime;

import cn.dtkeys.dataservice.domain.model.DSCatalog;
import cn.dtkeys.dataservice.domain.model.DSConnection;
import cn.dtkeys.dataservice.domain.model.DSSource;

public record DataServiceRuntimeSource(DSSource source,
                                       DSConnection connection,
                                       DSCatalog catalog) {
}
