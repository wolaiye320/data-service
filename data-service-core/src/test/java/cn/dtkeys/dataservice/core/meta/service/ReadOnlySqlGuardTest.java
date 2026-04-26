package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadOnlySqlGuardTest {

    private final ReadOnlySqlGuard guard = new ReadOnlySqlGuard();

    @Test
    void shouldRejectDmlAndDdlStatements() {
        assertThatThrownBy(() -> guard.validate("insert into public.orders(id) values (1)"))
                .isInstanceOf(DataServiceException.class)
                .hasMessage("仅允许 select 或 with 查询语句");

        assertThatThrownBy(() -> guard.validate("create table public.orders_archive(id bigint)"))
                .isInstanceOf(DataServiceException.class)
                .hasMessage("仅允许 select 或 with 查询语句");
    }

    @Test
    void shouldRejectMultiStatementAndProcedureCalls() {
        assertThatThrownBy(() -> guard.validate("""
                select * from public.orders;
                select * from public.order_items
                """))
                .isInstanceOf(DataServiceException.class)
                .hasMessage("不允许多语句 SQL");

        assertThatThrownBy(() -> guard.validate("select call refresh_orders()"))
                .isInstanceOf(DataServiceException.class)
                .hasMessage("SQL 不允许存储过程或过程式语句");
    }

    @Test
    void shouldRejectDangerousFunctions() {
        assertThatThrownBy(() -> guard.validate("select pg_sleep(1)"))
                .isInstanceOf(DataServiceException.class)
                .hasMessage("SQL 包含危险函数调用");

        assertThatThrownBy(() -> guard.validate("with t as (select benchmark(1000000, sha1('a'))) select * from t"))
                .isInstanceOf(DataServiceException.class)
                .hasMessage("SQL 包含危险函数调用");
    }
}
