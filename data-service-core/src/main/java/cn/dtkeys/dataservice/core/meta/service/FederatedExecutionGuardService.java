package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.config.DataServiceResourceProtectionProperties;
import cn.dtkeys.dataservice.core.error.ResourceProtectionException;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

@Service
public class FederatedExecutionGuardService {

    private final DataServiceResourceProtectionProperties resourceProtectionProperties;
    private final Semaphore semaphore;

    public FederatedExecutionGuardService(DataServiceResourceProtectionProperties resourceProtectionProperties) {
        this.resourceProtectionProperties = resourceProtectionProperties;
        this.semaphore = new Semaphore(resourceProtectionProperties.getMaxConcurrentQueries(), true);
    }

    public GuardPermit acquire() {
        if (!semaphore.tryAcquire()) {
            throw ResourceProtectionException.concurrentQueriesExceeded(resourceProtectionProperties.getMaxConcurrentQueries());
        }
        return new GuardPermit(semaphore);
    }

    public void validateElapsed(DsServiceRecord service, long elapsedMs) {
        Integer timeoutSeconds = service.getFederatedQueryTimeoutSeconds();
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            timeoutSeconds = resourceProtectionProperties.getFederatedQueryTimeoutSeconds();
        }
        if (elapsedMs > timeoutSeconds * 1000L) {
            throw ResourceProtectionException.queryTimeoutExceeded("FEDERATED_EXECUTION", timeoutSeconds);
        }
    }

    public record GuardPermit(Semaphore semaphore) implements AutoCloseable {

        @Override
        public void close() {
            semaphore.release();
        }
    }
}
