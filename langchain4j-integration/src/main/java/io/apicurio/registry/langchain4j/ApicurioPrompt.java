/*
 * Copyright 2024 Red Hat
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

package io.apicurio.registry.langchain4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to inject a prompt template from Apicurio Registry.
 * <p>
 * This annotation can be used on method parameters or methods in LangChain4j AI services
 * to automatically fetch and inject prompt templates from Apicurio Registry.
 * <p>
 * Example usage:
 * <pre>{@code
 * @RegisterAiService
 * public interface SummarizationService {
 *
 *     @UserMessage("{prompt}")
 *     String summarize(@ApicurioPrompt(artifactId = "summarization-v1") String prompt,
 *                      String document, String style);
 * }
 * }</pre>
 *
 * @author Carles Arnal
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
public @interface ApicurioPrompt {

    /**
     * The artifact ID of the prompt template in Apicurio Registry.
     *
     * @return the artifact ID
     */
    String artifactId();

    /**
     * The group ID containing the prompt template.
     * If empty, the default group from configuration will be used.
     *
     * @return the group ID, or empty for default
     */
    String groupId() default "";

    /**
     * The version expression for the prompt template.
     * Examples: "1.0", "2.1.3", "branch=latest", "branch=production"
     * If empty, the latest version will be used.
     *
     * @return the version expression, or empty for latest
     */
    String version() default "";
}
