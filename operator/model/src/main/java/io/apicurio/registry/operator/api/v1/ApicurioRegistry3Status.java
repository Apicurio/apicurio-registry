package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.apicurio.registry.operator.api.v1.status.Condition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static lombok.AccessLevel.PRIVATE;

@JsonInclude(NON_NULL)
@JsonPropertyOrder({"conditions"})
@JsonDeserialize(using = None.class)
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@Builder
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@ToString
public class ApicurioRegistry3Status {

    /**
     * The latest generation of the ApicurioRegistry3 resource that this status has been updated for.
     */
    @JsonProperty("observedGeneration")
    @JsonPropertyDescription("""
            The latest generation of the ApicurioRegistry3 resource that this status has been updated for.""")
    @JsonSetter(nulls = SKIP)
    private Long observedGeneration;

    /**
     * Apicurio Registry operator and operand conditions.
     */
    @JsonProperty("conditions")
    @JsonPropertyDescription("""
            Apicurio Registry operator and operand conditions.""")
    @JsonSetter(nulls = SKIP)
    private List<Condition> conditions = new ArrayList<>();

    /**
     * Check that the statuses are semantically equivalent (i.e. ignoring observedGeneration, condition order, timestamps, etc.).
     */
    public static boolean isEquivalent(ApicurioRegistry3Status left, ApicurioRegistry3Status right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        var leftCopy = new ArrayList<>(left.conditions != null ? left.conditions : List.of());
        var rightCopy = new ArrayList<>(right.conditions != null ? right.conditions : List.of());
        for (int li = 0; li < leftCopy.size(); li++) {
            var found = false;
            for (int ri = li; ri < rightCopy.size(); ri++) {
                if (Condition.isEquivalent(leftCopy.get(li), rightCopy.get(ri))) {
                    // OK, exchange
                    var tmp = rightCopy.get(li);
                    rightCopy.set(li, rightCopy.get(ri));
                    rightCopy.set(ri, tmp);
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
