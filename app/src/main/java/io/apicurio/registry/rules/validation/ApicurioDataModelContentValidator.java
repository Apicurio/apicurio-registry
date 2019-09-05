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

package io.apicurio.registry.rules.validation;

import java.util.List;

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.core.models.Document;
import io.apicurio.datamodels.core.models.ValidationProblem;

/**
 * A content validator implementation for the OpenAPI and AsyncAPI content types.
 * @author eric.wittmann@gmail.com
 */
public abstract class ApicurioDataModelContentValidator implements ContentValidator {

    /**
     * @see io.apicurio.registry.rules.validation.ContentValidator#validate(io.apicurio.registry.rules.validation.ValidationLevel, java.lang.String)
     */
    @Override
    public void validate(ValidationLevel level, String artifactContent) throws InvalidContentException {
        Document document = null;
        if (level == ValidationLevel.SYNTAX_ONLY || level == ValidationLevel.FULL) {
            try {
                document = Library.readDocumentFromJSONString(artifactContent);
            } catch (Exception e) {
                throw new InvalidContentException("Syntax violation for " + getDataModelType() + " artifact.", e);
            }
        }
        
        if (level == ValidationLevel.FULL) {
            List<ValidationProblem> problems = Library.validate(document, null);
            if (!problems.isEmpty()) {
                // TODO should include the details of all the validation problems in the exception
                throw new InvalidContentException("The " + getDataModelType() + " artifact is not semantically valid. " + problems.size() + " problems found.");
            }
        }
    }
    
    /**
     * Returns the type of data model being validated.  Subclasses must implement.
     */
    protected abstract String getDataModelType();

}
