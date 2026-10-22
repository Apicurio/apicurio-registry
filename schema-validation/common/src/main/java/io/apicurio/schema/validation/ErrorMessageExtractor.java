/*
 * Copyright 2022 Red Hat
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

package io.apicurio.schema.validation;

import io.apicurio.registry.rest.client.models.ProblemDetails;

/**
 * Utility for extracting detailed error messages from
 * exceptions, including Apicurio Registry ProblemDetails.
 *
 * @author Fabian Martinez
 */
public final class ErrorMessageExtractor {

    private ErrorMessageExtractor() {
    }

    public static String extractErrorMessage(Exception e) {
        StringBuilder errorMessage = new StringBuilder();

        errorMessage.append(e.getClass().getSimpleName());
        String message = getDetailedMessage(e);
        if (message != null && !message.isEmpty()) {
            errorMessage.append(": ").append(message);
        }

        Throwable cause = e.getCause();
        while (cause != null) {
            errorMessage.append(" | Caused by: ")
                    .append(cause.getClass().getSimpleName());
            String causeMessage = getDetailedMessage(cause);
            if (causeMessage != null
                    && !causeMessage.isEmpty()) {
                errorMessage.append(": ")
                        .append(causeMessage);
            }
            cause = cause.getCause();
        }

        return errorMessage.toString();
    }

    private static String getDetailedMessage(
            Throwable throwable) {
        if (throwable instanceof ProblemDetails) {
            String detail =
                    ((ProblemDetails) throwable).getDetail();
            if (detail != null && !detail.isEmpty()) {
                return detail;
            }
        }
        return throwable.getMessage();
    }

}
