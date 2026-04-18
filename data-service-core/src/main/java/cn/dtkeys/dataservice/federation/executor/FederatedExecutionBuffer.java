package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.common.exception.QueryExecutionException;
import cn.dtkeys.dataservice.common.exception.ResourceLimitExceededException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 联邦查询结果缓冲器，优先保留受限内存预览，超阈值后将完整结果落到临时文件。
 */
public class FederatedExecutionBuffer implements AutoCloseable {

    private final int previewLimit;
    private final int maxRows;
    private final int inMemoryBudgetRows;
    private final List<Map<String, Object>> previewRows = new ArrayList<>();

    private int totalRows;
    private int inMemoryRows;
    private int spilledRows;
    private boolean spillTriggered;
    private Path spillFile;

    public FederatedExecutionBuffer(int previewLimit, int maxRows, int inMemoryBudgetRows) {
        this.previewLimit = Math.max(1, previewLimit);
        this.maxRows = Math.max(1, maxRows);
        this.inMemoryBudgetRows = Math.max(1, inMemoryBudgetRows);
    }

    public void appendRows(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            totalRows++;
            if (totalRows > maxRows) {
                throw new ResourceLimitExceededException("联邦查询结果集超过限制: " + maxRows);
            }
            if (previewRows.size() < previewLimit) {
                previewRows.add(copyRow(row));
            }
            if (inMemoryRows < inMemoryBudgetRows) {
                inMemoryRows++;
                continue;
            }
            spillTriggered = true;
            spilledRows++;
            writeRowToSpill(row);
        }
    }

    public List<Map<String, Object>> previewRows() {
        return List.copyOf(previewRows);
    }

    public Map<String, Object> summary() {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("previewRowCount", previewRows.size());
        summary.put("totalRowCount", totalRows);
        summary.put("spillTriggered", spillTriggered);
        summary.put("spilledRowCount", spilledRows);
        summary.put("inMemoryBudgetRows", inMemoryBudgetRows);
        summary.put("spillFile", spillFile == null ? null : spillFile.toString());
        return summary;
    }

    private Map<String, Object> copyRow(Map<String, Object> row) {
        return new LinkedHashMap<>(row);
    }

    private void writeRowToSpill(Map<String, Object> row) {
        try {
            if (spillFile == null) {
                spillFile = Files.createTempFile("federated-exec-", ".spill.jsonl");
            }
            Files.writeString(
                spillFile,
                row.toString() + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new QueryExecutionException("联邦查询中间结果落盘失败", exception);
        }
    }

    @Override
    public void close() {
        if (spillFile != null) {
            try {
                Files.deleteIfExists(spillFile);
            } catch (IOException ignored) {
                // 临时文件删除失败不影响主流程，执行摘要已保留路径信息。
            }
        }
    }
}
