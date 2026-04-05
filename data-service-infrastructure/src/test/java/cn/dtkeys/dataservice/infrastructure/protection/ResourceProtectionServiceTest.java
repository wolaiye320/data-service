package cn.dtkeys.dataservice.infrastructure.protection;

import cn.dtkeys.dataservice.common.exception.QueryTimeoutException;
import cn.dtkeys.dataservice.common.exception.ResourceLimitExceededException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceProtectionServiceTest {

    @Test
    void shouldRejectBatchSizeBeyondLimit() {
        ResourceProtectionProperties properties = new ResourceProtectionProperties();
        properties.setMaxBatchSize(10);
        ResourceProtectionService service = new ResourceProtectionService(properties);

        assertThatThrownBy(() -> service.validateBatchSize(11))
            .isInstanceOf(ResourceLimitExceededException.class)
            .hasMessageContaining("批量规模超过系统限制");
    }

    @Test
    void shouldRejectResultRowsBeyondLimit() {
        ResourceProtectionProperties properties = new ResourceProtectionProperties();
        properties.setMaxResultRows(20);
        ResourceProtectionService service = new ResourceProtectionService(properties);

        assertThatThrownBy(() -> service.validateResultRows(21))
            .isInstanceOf(ResourceLimitExceededException.class)
            .hasMessageContaining("结果集行数超过系统限制");
    }

    @Test
    void shouldRejectTimeoutBeyondLimit() {
        ResourceProtectionProperties properties = new ResourceProtectionProperties();
        properties.setQueryTimeoutSeconds(15);
        ResourceProtectionService service = new ResourceProtectionService(properties);

        assertThatThrownBy(() -> service.validateQueryTimeout(16))
            .isInstanceOf(QueryTimeoutException.class)
            .hasMessageContaining("查询超时阈值超过系统限制");
    }

    @Test
    void shouldRejectQueryWhenConcurrentExecutionsExceedLimit() throws Exception {
        ResourceProtectionProperties properties = new ResourceProtectionProperties();
        properties.setMaxConcurrentQueries(1);
        ResourceProtectionService service = new ResourceProtectionService(properties);
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> asyncError = new AtomicReference<>();

        Thread first = new Thread(() -> {
            try {
                service.executeWithinConcurrencyLimit(() -> {
                    acquired.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    return null;
                });
            } catch (Throwable throwable) {
                asyncError.set(throwable);
            }
        });
        first.start();
        acquired.await();

        try {
            assertThatThrownBy(() -> service.executeWithinConcurrencyLimit(() -> null))
                .isInstanceOf(ResourceLimitExceededException.class)
                .hasMessageContaining("并发查询数超过系统限制");
        } finally {
            release.countDown();
            first.join();
        }

        assertThat(asyncError.get()).isNull();
    }
}
