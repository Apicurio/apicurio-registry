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

package io.apicurio.registry.rules.validity;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.rules.RuleContext;
import io.apicurio.registry.rules.RuleExecutor;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Logged
public class ValidityRuleExecutor implements RuleExecutor {

    @Inject
    ArtifactTypeUtilProviderFactory factory;
    
    /**
     * @see io.apicurio.registry.rules.RuleExecutor#execute(io.apicurio.registry.rules.RuleContext)
     */
    @Override
    public void execute(RuleContext context) throws RuleViolationException {
        ValidityLevel level = ValidityLevel.valueOf(context.getConfiguration());
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(context.getArtifactType());
        ContentValidator validator = provider.getContentValidator();
        validator.validate(level, context.getUpdatedContent(), context.getResolvedReferences());
    }

}
