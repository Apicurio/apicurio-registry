/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.rest.client.exception;

import io.apicurio.registry.rest.v2.beans.Error;
import io.apicurio.registry.rest.v2.beans.RuleViolationError;

/**
 * @author Carles Arnal 'carles.arnal@redhat.com'
 */
public class ExceptionMapper {

    public static RestClientException map(Error error) throws RestClientException {
        if (error == null || error.getName() == null) {
            return new RestClientException(error);
        }
        switch (error.getName()) {
            case "NotFoundException":
                return new NotFoundException(error);
            case "AlreadyExistsException":
                return new AlreadyExistsException(error);
            case "ArtifactAlreadyExistsException":
                return new ArtifactAlreadyExistsException(error);
            case "ArtifactNotFoundException":
                return new ArtifactNotFoundException(error);
            case "RuleNotFoundException":
                return new RuleNotFoundException(error);
            case "RuleAlreadyExistsException":
                return new RuleAlreadyExistsException(error);
            case "RoleMappingNotFoundException":
                return new RoleMappingNotFoundException(error);
            case "RoleMappingAlreadyExistsException":
                return new RoleMappingAlreadyExistsException(error);
            case "VersionNotFoundException":
                return new VersionNotFoundException(error);
            case "DefaultRuleDeletionException":
                return new DefaultRuleDeletionException(error);
            case "RuleViolationException":
                return new RuleViolationException((RuleViolationError)error);
            case "BadRequestException":
                return new BadRequestException(error);
            case "InvalidArtifactStateException":
                return new InvalidArtifactStateException(error);
            case "UnprocessableEntityException":
                return new UnprocessableEntityException(error);
            case "UnprocessableSchemaException":
                return new UnprocessableSchemaException(error);
            case "InvalidArtifactTypeException":
                return new InvalidArtifactTypeException(error);
            case "LimitExceededException":
                return new LimitExceededException(error);
            case "TenantNotFoundException":
                return new TenantNotFoundException(error);
            case "TenantNotAuthorizedException":
                return new TenantNotAuthorizedException(error);
            case "ContentNotFoundException":
                return new ContentNotFoundException(error);
            case "InvalidGroupIdException":
                return new InvalidGroupIdException(error);
            case "MissingRequiredParameterException":
                return new MissingRequiredParameterException(error);
            case "LogConfigurationNotFoundException":
                return new LogConfigurationNotFoundException(error);
            case "GroupNotFoundException":
                return new GroupNotFoundException(error);
            case "TenantManagerClientException":
                return new TenantManagerClientException(error);
            case "ParametersConflictException":
                return new ParametersConflictException(error);
            default:
                return new RestClientException(error);
        }
    }
}