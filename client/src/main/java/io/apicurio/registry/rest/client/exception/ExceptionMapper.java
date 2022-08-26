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


/**
 * @author Carles Arnal 'carles.arnal@redhat.com'
 */
public class ExceptionMapper {

    public static RestClientException map(RestClientException ex) throws RestClientException {
        if (ex == null || ex.getError() == null || ex.getError().getName() == null) {
            return ex;
        }
        switch (ex.getError().getName()) {
            case "ArtifactAlreadyExistsException":
                return new ArtifactAlreadyExistsException(ex.getError());
            case "VersionAlreadyExistsException":
                return new VersionAlreadyExistsException(ex.getError());
            case "ArtifactNotFoundException":
                return new ArtifactNotFoundException(ex.getError());
            case "RuleNotFoundException":
                return new RuleNotFoundException(ex.getError());
            case "RuleAlreadyExistsException":
                return new RuleAlreadyExistsException(ex.getError());
            case "RoleMappingNotFoundException":
                return new RoleMappingNotFoundException(ex.getError());
            case "RoleMappingAlreadyExistsException":
                return new RoleMappingAlreadyExistsException(ex.getError());
            case "VersionNotFoundException":
                return new VersionNotFoundException(ex.getError());
            case "DefaultRuleDeletionException":
                return new DefaultRuleDeletionException(ex.getError());
            case "RuleViolationException":
                return new RuleViolationException(ex.getError());
            case "BadRequestException":
                return new BadRequestException(ex.getError());
            case "InvalidArtifactStateException":
                return new InvalidArtifactStateException(ex.getError());
            case "UnprocessableEntityException":
                return new UnprocessableEntityException(ex.getError());
            case "UnprocessableSchemaException":
                return new UnprocessableSchemaException(ex.getError());
            case "InvalidArtifactTypeException":
                return new InvalidArtifactTypeException(ex.getError());
            case "LimitExceededException":
                return new LimitExceededException(ex.getError());
            case "TenantNotFoundException":
                return new TenantNotFoundException(ex.getError());
            case "TenantNotAuthorizedException":
                return new TenantNotAuthorizedException(ex.getError());
            case "ContentNotFoundException":
                return new ContentNotFoundException(ex.getError());
            case "InvalidGroupIdException":
                return new InvalidGroupIdException(ex.getError());
            case "MissingRequiredParameterException":
                return new MissingRequiredParameterException(ex.getError());
            case "LogConfigurationNotFoundException":
                return new LogConfigurationNotFoundException(ex.getError());
            case "GroupNotFoundException":
                return new GroupNotFoundException(ex.getError());
            case "TenantManagerClientException":
                return new TenantManagerClientException(ex.getError());
            case "ParametersConflictException":
                return new ParametersConflictException(ex.getError());
            case "ConfigPropertyNotFoundException":
                return new ConfigPropertyNotFoundException(ex.getError());
            case "InvalidPropertyValueException":
                return new InvalidPropertyValueException(ex.getError());
            default:
                return ex;
        }
    }
}