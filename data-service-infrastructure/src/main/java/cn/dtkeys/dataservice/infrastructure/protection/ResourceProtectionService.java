package cn.dtkeys.dataservice.infrastructure.protection;

import cn.dtkeys.dataservice.common.exception.ResourceLimitExceededException;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * 统一资源保护校验入口。
 */
@Service
public class ResourceProtectionService {

    private final ResourceProtectionProperties resourceProtectionProperties;
    private final Semaphore querySemaphore;

    public ResourceProtectionService(ResourceProtectionProperties resourceProtectionProperties) {
        this.resourceProtectionProperties = resourceProtectionProperties;
        this.querySemaphore = new Semaphore(resourceProtectionProperties.getMaxConcurrentQueries(), true);
    }

    public void validateBatchSize(int batchSize) {
        resourceProtectionProperties.validateBatchSize(batchSize);
    }

    public void validateResultRows(int resultRows) {
        resourceProtectionProperties.validateResultRows(resultRows);
    }

    public void validateQueryTimeout(int timeoutSeconds) {
        resourceProtectionProperties.validateQueryTimeout(timeoutSeconds);
    }

    public void validateFederatedQueryTimeout(int timeoutSeconds) {
        resourceProtectionProperties.validateFederatedQueryTimeout(timeoutSeconds);
    }

    public <T> T executeWithinConcurrencyLimit(Supplier<T> supplier) {
        if (!querySemaphore.tryAcquire()) {
            throw new ResourceLimitExceededException(
                "并发查询数超过系统限制: " + resourceProtectionProperties.getMaxConcurrentQueries()
            );
        }
        try {
            return supplier.get();
        } finally {
            querySemaphore.release();
        }
    }

    public int availableQuerySlots() {
        return querySemaphore.availablePermits();
    }
}
