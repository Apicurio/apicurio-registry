/*
 * Copyright 2019 Red Hat
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

import io.apicurio.registry.rules.RuleContext;
import io.apicurio.registry.rules.RuleExecutor;
import io.apicurio.registry.rules.RuleViolationException;

import java.util.Collections;
import javax.enterprise.context.ApplicationScoped;

/**
 * Rule executor for the "Compatibility" rule.  The Compatibility Rule is responsible
 * for ensuring that the updated content does not violate the configured compatibility
 * level.  Levels include e.g. Backward compatibility.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class CompatibilityRuleExecutor implements RuleExecutor {

    /**
     * @see io.apicurio.registry.rules.RuleExecutor#execute(io.apicurio.registry.rules.RuleContext)
     */
    @Override
    public void execute(RuleContext context) throws RuleViolationException {
        CompatibilityLevel level = CompatibilityLevel.valueOf(context.getConfiguration());
        ArtifactTypeAdapter adapter = ArtifactTypeAdapterFactory.toAdapter(context.getArtifactType());
        if (!adapter.isCompatibleWith(
            level,
            Collections.singletonList(context.getCurrentContent()),
            context.getUpdatedContent())
        ) {
            throw new RuleViolationException(String.format("Incompatible artifact: %s [%s]",
                                                           context.getArtifactId(), context.getArtifactType()));
        }
    }

}
