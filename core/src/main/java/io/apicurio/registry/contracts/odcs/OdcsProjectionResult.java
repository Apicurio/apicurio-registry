package io.apicurio.registry.contracts.odcs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class OdcsProjectionResult {
    @Builder.Default
    private int rulesApplied = 0;
    @Builder.Default
    private int labelsApplied = 0;
    @Builder.Default
    private int tagsApplied = 0;
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    public void addWarning(String warning) {
        warnings.add(warning);
    }
}
