package cn.dtkeys.dataservice.infrastructure.executor;

import cn.dtkeys.dataservice.common.exception.ParamInvalidException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlReadOnlyValidatorTest {

    private final SqlReadOnlyValidator validator = new SqlReadOnlyValidator();

    @Test
    void shouldAcceptSelectAndWithQuery() {
        assertThatCode(() -> validator.validate("select * from customer where customer_id = :customerId"))
            .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("""
            with customer_cte as (
                select customer_id from customer where customer_id = :customerId
            )
            select * from customer_cte
            """)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectWriteAndMultiStatementSql() {
        assertThatThrownBy(() -> validator.validate("update customer set name = 'A'"))
            .isInstanceOf(ParamInvalidException.class);
        assertThatThrownBy(() -> validator.validate("select * from customer; delete from customer"))
            .isInstanceOf(ParamInvalidException.class);
    }

    @Test
    void shouldRejectDangerousKeywordInWithClause() {
        assertThatThrownBy(() -> validator.validate("""
            with updated_rows as (
                delete from customer returning customer_id
            )
            select * from updated_rows
            """)).isInstanceOf(ParamInvalidException.class);
    }
}
