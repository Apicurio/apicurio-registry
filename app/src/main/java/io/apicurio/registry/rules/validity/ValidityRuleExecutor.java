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

package io.apicurio.registry.rules.validity;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.apicurio.registry.rules.RuleContext;
import io.apicurio.registry.rules.RuleExecutor;
import io.apicurio.registry.rules.RuleViolationException;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class ValidityRuleExecutor implements RuleExecutor {

    @Inject
    ContentValidatorFactory factory;
    
    /**
     * @see io.apicurio.registry.rules.RuleExecutor#execute(io.apicurio.registry.rules.RuleContext)
     */
    @Override
    public void execute(RuleContext context) throws RuleViolationException {
        try {
            ValidityLevel level = ValidityLevel.valueOf(context.getConfiguration());
            ContentValidator validator = factory.createValidator(context.getArtifactType());
            validator.validate(level, context.getUpdatedContent());
        } catch (InvalidContentException e) {
            throw new RuleViolationException(e.getMessage(), e);
        }
    }

}
