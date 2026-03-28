package cn.dtkeys.dataservice.federation.model;

import java.util.List;

public record ValidationResult(
    boolean valid,
    List<String> errors,
    List<String> warnings,
    List<String> recognizedSources
) {

    public static ValidationResult success(List<String> warnings, List<String> recognizedSources) {
        return new ValidationResult(true, List.of(), warnings, recognizedSources);
    }

    public static ValidationResult failure(List<String> errors, List<String> warnings, List<String> recognizedSources) {
        return new ValidationResult(false, errors, warnings, recognizedSources);
    }
}
