package cn.dtkeys.dataservice.federation.model;

import java.util.Map;
import java.util.List;

public record ValidationResult(
    boolean valid,
    List<String> errors,
    List<String> warnings,
    List<String> recognizedSources,
    Map<String, Object> stageDetails
) {

    public static ValidationResult success(List<String> warnings, List<String> recognizedSources) {
        return new ValidationResult(true, List.of(), warnings, recognizedSources, Map.of());
    }

    public static ValidationResult failure(List<String> errors, List<String> warnings, List<String> recognizedSources) {
        return new ValidationResult(false, errors, warnings, recognizedSources, Map.of());
    }

    public static ValidationResult of(boolean valid,
                                      List<String> errors,
                                      List<String> warnings,
                                      List<String> recognizedSources,
                                      Map<String, Object> stageDetails) {
        return new ValidationResult(valid, errors, warnings, recognizedSources, stageDetails);
    }
}
