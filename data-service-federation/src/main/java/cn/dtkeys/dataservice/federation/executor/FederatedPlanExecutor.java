package cn.dtkeys.dataservice.federation.executor;

import cn.dtkeys.dataservice.federation.model.ExecutionStageResult;
import cn.dtkeys.dataservice.federation.model.FederatedExecutionResult;
import cn.dtkeys.dataservice.federation.model.FederatedPlan;
import cn.dtkeys.dataservice.federation.model.FederatedPlanStage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class FederatedPlanExecutor {

    private static final FederatedExecutionOptions DEFAULT_OPTIONS = FederatedExecutionOptions.defaults();

    private final PlanSplitter planSplitter = new PlanSplitter();
    private final RemoteQueryExecutor remoteQueryExecutor = new RemoteQueryExecutor();
    private final LocalResultAssembler localResultAssembler = new LocalResultAssembler();

    public FederatedExecutionResult execute(FederatedPlan plan) {
        return execute(plan, DEFAULT_OPTIONS);
    }

    public FederatedExecutionResult execute(FederatedPlan plan, FederatedExecutionOptions executionOptions) {
        List<FederatedPlanStage> stages = planSplitter.split(plan);
        List<ExecutionStageResult> stageResults = new ArrayList<>();
        Map<String, ExecutionStageResult> completedStages = new ConcurrentHashMap<>();
        int maxParallelism = resolveMaxParallelism(plan, executionOptions);
        List<List<FederatedPlanStage>> stageWaves = splitByDependencies(stages, maxParallelism);

        ExecutorService executorService = Executors.newFixedThreadPool(maxParallelism);
        try {
            for (List<FederatedPlanStage> wave : stageWaves) {
                List<ExecutionStageResult> waveResults = executeWave(executorService, wave, completedStages);
                for (ExecutionStageResult result : waveResults) {
                    stageResults.add(result);
                    completedStages.put(result.stageId(), result);
                }
            }
        } finally {
            executorService.shutdownNow();
        }
        try (FederatedExecutionBuffer executionBuffer = localResultAssembler.assemble(stageResults, executionOptions)) {
            Map<String, Object> executionSummary = new LinkedHashMap<>();
            executionSummary.put("maxParallelism", maxParallelism);
            executionSummary.put("stageWaveCount", stageWaves.size());
            executionSummary.put("dynamicFilterStageCount", stages.stream().filter(FederatedPlanStage::dynamicFilterEnabled).count());
            executionSummary.put("executedStageCount", stageResults.size());
            executionSummary.put("resultBuffer", executionBuffer.summary());
            return new FederatedExecutionResult(stageResults, executionBuffer.previewRows(), executionSummary);
        }
    }

    private List<ExecutionStageResult> executeWave(ExecutorService executorService,
                                                   List<FederatedPlanStage> wave,
                                                   Map<String, ExecutionStageResult> completedStages) {
        List<Future<ExecutionStageResult>> futures = new ArrayList<>(wave.size());
        for (FederatedPlanStage stage : wave) {
            Callable<ExecutionStageResult> task = () -> remoteQueryExecutor.execute(stage, completedStages);
            futures.add(executorService.submit(task));
        }

        List<ExecutionStageResult> results = new ArrayList<>(wave.size());
        for (Future<ExecutionStageResult> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("联邦阶段执行被中断", exception);
            } catch (ExecutionException exception) {
                throw new IllegalStateException("联邦阶段执行失败", exception.getCause());
            }
        }
        return List.copyOf(results);
    }

    private int resolveMaxParallelism(FederatedPlan plan, FederatedExecutionOptions executionOptions) {
        if (executionOptions.maxParallelism() > 0) {
            return executionOptions.maxParallelism();
        }
        Object value = plan.executionProfile().getOrDefault("maxParallelism", DEFAULT_OPTIONS.maxParallelism());
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        return DEFAULT_OPTIONS.maxParallelism();
    }

    private List<List<FederatedPlanStage>> splitByDependencies(List<FederatedPlanStage> stages, int maxParallelism) {
        Map<String, FederatedPlanStage> stagesById = stages.stream()
            .collect(Collectors.toMap(FederatedPlanStage::stageId, stage -> stage, (left, right) -> left, LinkedHashMap::new));
        Map<String, Integer> unresolvedDependencyCount = new HashMap<>();
        Map<String, List<String>> dependentStageIds = new HashMap<>();
        for (FederatedPlanStage stage : stages) {
            int dependencyCount = 0;
            for (String dependencyStageId : stage.dependsOnStageIds()) {
                if (!stagesById.containsKey(dependencyStageId)) {
                    throw new IllegalStateException("联邦阶段依赖不存在: " + dependencyStageId);
                }
                dependencyCount++;
                dependentStageIds.computeIfAbsent(dependencyStageId, ignored -> new ArrayList<>()).add(stage.stageId());
            }
            unresolvedDependencyCount.put(stage.stageId(), dependencyCount);
        }

        List<List<FederatedPlanStage>> waves = new ArrayList<>();
        HashSet<String> scheduledStageIds = new HashSet<>();
        while (scheduledStageIds.size() < stages.size()) {
            List<FederatedPlanStage> readyStages = stages.stream()
                .filter(stage -> !scheduledStageIds.contains(stage.stageId()))
                .filter(stage -> unresolvedDependencyCount.getOrDefault(stage.stageId(), 0) == 0)
                .toList();
            if (readyStages.isEmpty()) {
                throw new IllegalStateException("联邦阶段依赖存在环或无法解析");
            }
            for (int start = 0; start < readyStages.size(); start += maxParallelism) {
                int end = Math.min(start + maxParallelism, readyStages.size());
                List<FederatedPlanStage> wave = List.copyOf(readyStages.subList(start, end));
                waves.add(wave);
                for (FederatedPlanStage stage : wave) {
                    scheduledStageIds.add(stage.stageId());
                }
            }
            for (FederatedPlanStage completedStage : readyStages) {
                List<String> dependentIds = dependentStageIds.getOrDefault(completedStage.stageId(), List.of());
                for (String dependentId : dependentIds) {
                    unresolvedDependencyCount.computeIfPresent(dependentId, (ignored, count) -> count - 1);
                }
            }
        }
        return waves;
    }
}
