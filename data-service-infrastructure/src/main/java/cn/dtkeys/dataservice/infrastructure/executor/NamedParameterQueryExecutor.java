package cn.dtkeys.dataservice.infrastructure.executor;

import cn.dtkeys.dataservice.common.exception.DatasourceUnavailableException;
import cn.dtkeys.dataservice.common.exception.QueryExecutionException;
import cn.dtkeys.dataservice.common.exception.QueryTimeoutException;
import cn.dtkeys.dataservice.domain.runtime.DataServiceRuntimeSource;
import cn.dtkeys.dataservice.infrastructure.datasource.DatasourceConnectionManager;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * 基于 NamedParameterJdbcTemplate 的统一查询执行器。
 */
@Component
public class NamedParameterQueryExecutor {

    private final DatasourceConnectionManager datasourceConnectionManager;

    public NamedParameterQueryExecutor(DatasourceConnectionManager datasourceConnectionManager) {
        this.datasourceConnectionManager = datasourceConnectionManager;
    }

    /**
     * 在指定数据源上执行只读查询。
     *
     * @param runtimeSource 运行时来源
     * @param sql SQL 文本
     * @param params 命名参数
     * @param timeoutSeconds 超时时间
     * @param maxRows 最大返回行数
     * @return 原始结果集
     */
    public List<Map<String, Object>> query(DataServiceRuntimeSource runtimeSource,
                                           String sql,
                                           Map<String, Object> params,
                                           int timeoutSeconds,
                                           int maxRows) {
        try {
            DataSource dataSource = datasourceConnectionManager.getDataSource(runtimeSource);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.setQueryTimeout(timeoutSeconds);
            jdbcTemplate.setMaxRows(maxRows + 1);

            NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
            return namedParameterJdbcTemplate.query(sql, new MapSqlParameterSource(params), new ColumnMapRowMapper());
        } catch (QueryTimeoutException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            if (exception.getMessage() != null && exception.getMessage().toLowerCase().contains("timeout")) {
                throw new QueryTimeoutException("查询执行超时");
            }
            throw new QueryExecutionException("查询执行失败", exception);
        } catch (DatasourceUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new QueryExecutionException("查询执行失败", exception);
        }
    }

    public int streamQuery(DataServiceRuntimeSource runtimeSource,
                           String sql,
                           Map<String, Object> params,
                           int timeoutSeconds,
                           int fetchSize,
                           int maxRows,
                           StreamingQueryRowHandler rowHandler) {
        try {
            DataSource dataSource = datasourceConnectionManager.getDataSource(runtimeSource);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.setQueryTimeout(timeoutSeconds);
            jdbcTemplate.setFetchSize(Math.max(1, fetchSize));
            jdbcTemplate.setMaxRows(maxRows + 1);

            NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
            ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();
            MapSqlParameterSource parameterSource = new MapSqlParameterSource(params);
            final int[] rowCount = {0};
            namedParameterJdbcTemplate.query(sql, parameterSource, rs -> {
                rowHandler.handleRow(rowMapper.mapRow(rs, rowCount[0]));
                rowCount[0]++;
            });
            return rowCount[0];
        } catch (QueryTimeoutException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            if (exception.getMessage() != null && exception.getMessage().toLowerCase().contains("timeout")) {
                throw new QueryTimeoutException("查询执行超时");
            }
            throw new QueryExecutionException("查询流式执行失败", exception);
        } catch (DatasourceUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new QueryExecutionException("查询流式执行失败", exception);
        }
    }
}
