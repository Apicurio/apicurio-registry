package io.apicurio.registry.contracts.quality;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class QualityScore {
    private float overall;
    private float completeness;
    private float compliance;
    private float stability;
}
