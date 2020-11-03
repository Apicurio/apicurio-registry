/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rules.compatibility;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.rest.beans.RuleViolationCause;
import io.apicurio.registry.rules.RuleContext;
import io.apicurio.registry.rules.RuleExecutor;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;

/**
 * Rule executor for the "Compatibility" rule.  The Compatibility Rule is responsible
 * for ensuring that the updated content does not violate the configured compatibility
 * level.  Levels include e.g. Backward compatibility.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Logged
public class CompatibilityRuleExecutor implements RuleExecutor {

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    /**
     * @see io.apicurio.registry.rules.RuleExecutor#execute(io.apicurio.registry.rules.RuleContext)
     */
    @Override
    public void execute(RuleContext context) throws RuleViolationException {
        CompatibilityLevel level = CompatibilityLevel.valueOf(context.getConfiguration());
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(context.getArtifactType());
        CompatibilityChecker checker = provider.getCompatibilityChecker();
        List<ContentHandle> existingArtifacts = context.getCurrentContent() != null
            ? singletonList(context.getCurrentContent()) : emptyList();
        CompatibilityExecutionResult compatibilityExecutionResult = checker.testCompatibility(
             level,
             existingArtifacts,
             context.getUpdatedContent());
        if (!compatibilityExecutionResult.isCompatible()) {
            throw new RuleViolationException(String.format("Incompatible artifact: %s [%s], num of incompatible diffs: {%s}",
                 context.getArtifactId(), context.getArtifactType(),
                 compatibilityExecutionResult.getIncompatibleDifferences().size()),
                 RuleType.COMPATIBILITY, context.getConfiguration(),
                 transformCompatibilityDiffs(compatibilityExecutionResult.getIncompatibleDifferences()));
        }
    }

    /**
     * Convert the set of compatibility differences into a collection of rule violation causes
     * for return to the user.
     * @param differences
     */
    private Set<RuleViolationCause> transformCompatibilityDiffs(Set<CompatibilityDifference> differences) {
        if (!differences.isEmpty()) {
            Set<RuleViolationCause> res = new HashSet<>();
            for (CompatibilityDifference diff : differences) {
                res.add(diff.asRuleViolationCause());
            }
            return res;
        } else {
            return Collections.emptySet();
        }
    }

}
