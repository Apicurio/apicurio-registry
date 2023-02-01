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

package io.apicurio.registry;

import io.apicurio.rest.client.auth.AccessTokenResponse;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @author Fabian Martinez
 */
@RegisterForReflection(targets = {
  AccessTokenResponse.class,
  // Needed for the JAXRSClient
  org.apache.commons.logging.LogFactory.class,
  org.apache.commons.logging.impl.LogFactoryImpl.class,
  org.apache.commons.logging.impl.SimpleLog.class,
  // For jackson
  com.fasterxml.jackson.dataformat.yaml.YAMLParser.class,
  // The following list is generated running `jbang cli/tools/extractRegisterForReflection.java`
  io.apicurio.datamodels.asyncapi.models.AaiChannelBindings.class,
  io.apicurio.datamodels.asyncapi.models.AaiChannelBindingsDefinition.class,
  io.apicurio.datamodels.asyncapi.models.AaiChannelItem.class,
  io.apicurio.datamodels.asyncapi.models.AaiComponents.class,
  io.apicurio.datamodels.asyncapi.models.AaiContact.class,
  io.apicurio.datamodels.asyncapi.models.AaiCorrelationId.class,
  io.apicurio.datamodels.asyncapi.models.AaiDocument.class,
  io.apicurio.datamodels.asyncapi.models.AaiExternalDocumentation.class,
  io.apicurio.datamodels.asyncapi.models.AaiHeaderItem.class,
  io.apicurio.datamodels.asyncapi.models.AaiInfo.class,
  io.apicurio.datamodels.asyncapi.models.AaiLicense.class,
  io.apicurio.datamodels.asyncapi.models.AaiMessage.class,
  io.apicurio.datamodels.asyncapi.models.AaiMessageBase.class,
  io.apicurio.datamodels.asyncapi.models.AaiMessageBindings.class,
  io.apicurio.datamodels.asyncapi.models.AaiMessageBindingsDefinition.class,
  io.apicurio.datamodels.asyncapi.models.AaiMessageTrait.class,
  io.apicurio.datamodels.asyncapi.models.AaiMessageTraitDefinition.class,
  io.apicurio.datamodels.asyncapi.models.AaiOAuthFlows.class,
  io.apicurio.datamodels.asyncapi.models.AaiOperation.class,
  io.apicurio.datamodels.asyncapi.models.AaiOperationBase.class,
  io.apicurio.datamodels.asyncapi.models.AaiOperationBindings.class,
  io.apicurio.datamodels.asyncapi.models.AaiOperationBindingsDefinition.class,
  io.apicurio.datamodels.asyncapi.models.AaiOperationTrait.class,
  io.apicurio.datamodels.asyncapi.models.AaiOperationTraitDefinition.class,
  io.apicurio.datamodels.asyncapi.models.AaiParameter.class,
  io.apicurio.datamodels.asyncapi.models.AaiSchema.class,
  io.apicurio.datamodels.asyncapi.models.AaiSecurityRequirement.class,
  io.apicurio.datamodels.asyncapi.models.AaiSecurityScheme.class,
  io.apicurio.datamodels.asyncapi.models.AaiServer.class,
  io.apicurio.datamodels.asyncapi.models.AaiServerBindings.class,
  io.apicurio.datamodels.asyncapi.models.AaiServerBindingsDefinition.class,
  io.apicurio.datamodels.asyncapi.models.AaiServerVariable.class,
  io.apicurio.datamodels.asyncapi.models.AaiTag.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20AuthorizationCodeOAuthFlow.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20ChannelBindings.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20ChannelBindingsDefinition.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20ChannelItem.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20ClientCredentialsOAuthFlow.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Components.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Contact.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20CorrelationId.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Document.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20ExternalDocumentation.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20HeaderItem.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20ImplicitOAuthFlow.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Info.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20License.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Message.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20MessageBindings.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20MessageBindingsDefinition.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20MessageTrait.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20MessageTraitDefinition.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20OAuthFlows.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Operation.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20OperationBindings.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20OperationBindingsDefinition.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20OperationTrait.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20OperationTraitDefinition.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Parameter.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20PasswordOAuthFlow.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Schema.Aai20AdditionalPropertiesSchema.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Schema.Aai20AllOfSchema.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Schema.Aai20AnyOfSchema.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Schema.Aai20NotSchema.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Schema.Aai20OneOfSchema.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Schema.Aai20PropertySchema.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Schema.Aai30ItemsSchema.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Schema.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20SchemaDefinition.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20SecurityRequirement.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20SecurityScheme.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Server.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20ServerBindings.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20ServerBindingsDefinition.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20ServerVariable.class,
  io.apicurio.datamodels.asyncapi.v2.models.Aai20Tag.class,
  io.apicurio.datamodels.core.models.Document.class,
  io.apicurio.datamodels.core.models.ExtensibleNode.class,
  io.apicurio.datamodels.core.models.Extension.class,
  io.apicurio.datamodels.core.models.common.AuthorizationCodeOAuthFlow.class,
  io.apicurio.datamodels.core.models.common.ClientCredentialsOAuthFlow.class,
  io.apicurio.datamodels.core.models.common.Components.class,
  io.apicurio.datamodels.core.models.common.Contact.class,
  io.apicurio.datamodels.core.models.common.ExternalDocumentation.class,
  io.apicurio.datamodels.core.models.common.ImplicitOAuthFlow.class,
  io.apicurio.datamodels.core.models.common.Info.class,
  io.apicurio.datamodels.core.models.common.License.class,
  io.apicurio.datamodels.core.models.common.ModernSecurityScheme.class,
  io.apicurio.datamodels.core.models.common.OAuthFlow.class,
  io.apicurio.datamodels.core.models.common.OAuthFlows.class,
  io.apicurio.datamodels.core.models.common.Operation.class,
  io.apicurio.datamodels.core.models.common.Parameter.class,
  io.apicurio.datamodels.core.models.common.PasswordOAuthFlow.class,
  io.apicurio.datamodels.core.models.common.Schema.class,
  io.apicurio.datamodels.core.models.common.SecurityRequirement.class,
  io.apicurio.datamodels.core.models.common.SecurityScheme.class,
  io.apicurio.datamodels.core.models.common.Server.class,
  io.apicurio.datamodels.core.models.common.ServerVariable.class,
  io.apicurio.datamodels.core.models.common.Tag.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.InvalidApiDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.InvalidContactEmailRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.InvalidContactUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.InvalidExternalDocsDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.InvalidLicenseUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.InvalidServerDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.InvalidServerUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.InvalidTagDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.InvalidTermsOfServiceUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidApiBasePathRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidApiHostRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidExampleDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidExternalDocsUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidHeaderDefaultValueRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidHeaderDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidLinkDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidOAuthAuthorizationUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidOAuthRefreshUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidOAuthTokenUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidOpenIDConnectUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidOperationConsumesRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidOperationDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidOperationProducesRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidParameterDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidPathItemDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidRequestBodyDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidResponseDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidSchemaItemsDefaultValueRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidSecuritySchemeAuthUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidSecuritySchemeDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidSecuritySchemeTokenUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidServerVariableDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.format.OasInvalidXmlNamespaceUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasDuplicatePathSegmentRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasEmptyPathSegmentRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasIdenticalPathTemplateRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasInvalidCallbackDefinitionNameRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasInvalidExampleDefinitionNameRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasInvalidHeaderDefinitionNameRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasInvalidHttpResponseCodeRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasInvalidLinkDefinitionNameRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasInvalidParameterDefNameRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasInvalidPathSegmentRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasInvalidPropertyNameRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasInvalidRequestBodyDefinitionNameRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasInvalidResponseDefNameRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasInvalidSchemaDefNameRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasInvalidScopeNameRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasInvalidSecuritySchemeNameRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasUnmatchedEncodingPropertyRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.name.OasUnmatchedExampleTypeRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.reference.OasInvalidCallbackReferenceRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.reference.OasInvalidExampleReferenceRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.reference.OasInvalidHeaderReferenceRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.reference.OasInvalidLinkOperationReferenceRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.reference.OasInvalidLinkReferenceRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.reference.OasInvalidParameterReferenceRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.reference.OasInvalidPathItemReferenceRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.reference.OasInvalidRequestBodyReferenceRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.reference.OasInvalidResponseReferenceRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.reference.OasInvalidSchemaReferenceRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.reference.OasInvalidSecurityRequirementNameRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.reference.OasInvalidSecuritySchemeReferenceRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.type.OasInvalidPropertyTypeValidationRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.type.OasInvalidSchemaArrayItemsRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.type.OasInvalidSchemaTypeValueRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasAllowReservedNotAllowedForParamRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasAllowReservedNotAllowedRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasEncodingStyleNotAllowedRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasExplodeNotAllowedRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasFormDataParamNotAllowedRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasInvalidApiConsumesMTRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasInvalidApiProducesMTRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasInvalidApiSchemeRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasInvalidEncodingForMPMTRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasInvalidHeaderStyleRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasInvalidHttpSecuritySchemeTypeRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasInvalidLinkOperationIdRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasInvalidOperationIdRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasInvalidOperationSchemeRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasInvalidPropertyValueRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasInvalidSecurityReqScopesRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasMissingPathParamDefinitionRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasMissingResponseForOperationRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasOperationSummaryTooLongRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasPathParamNotFoundRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasRequiredParamWithDefaultValueRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasSecurityRequirementScopesMustBeEmptyRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasServerVarNotFoundInTemplateRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnexpectedArrayCollectionFormatRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnexpectedHeaderCollectionFormatRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnexpectedHeaderUsageRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnexpectedNumOfParamMTsRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnexpectedNumberOfHeaderMTsRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnexpectedParamAllowEmptyValueRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnexpectedParamCollectionFormatRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnexpectedParamMultiRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnexpectedRequestBodyRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnexpectedSecurityRequirementScopesRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnexpectedUsageOfBearerTokenRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnexpectedUsageOfDiscriminatorRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnexpectedXmlWrappingRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownApiKeyLocationRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownArrayCollectionFormatRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownArrayFormatRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownArrayTypeRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownCookieParamStyleRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownEncodingStyleRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownHeaderCollectionFormatRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownHeaderFormatRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownHeaderParamStyleRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownHeaderTypeRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownOauthFlowTypeRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownParamCollectionFormatRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownParamFormatRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownParamLocationRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownParamStyleRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownParamTypeRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownPathParamStyleRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownQueryParamStyleRule.class,
  io.apicurio.datamodels.core.validation.rules.invalid.value.OasUnknownSecuritySchemeTypeRule.class,
  io.apicurio.datamodels.core.validation.rules.mutex.OasBodyAndFormDataMutualExclusivityRule.class,
  io.apicurio.datamodels.core.validation.rules.mutex.OasExampleValueMutualExclusivityRule.class,
  io.apicurio.datamodels.core.validation.rules.mutex.OasHeaderExamplesMutualExclusivityRule.class,
  io.apicurio.datamodels.core.validation.rules.mutex.OasHeaderSchemaContentMutualExclusivityRule.class,
  io.apicurio.datamodels.core.validation.rules.mutex.OasLinkOperationRefMutualExclusivityRule.class,
  io.apicurio.datamodels.core.validation.rules.mutex.OasMediaTypeExamplesMutualExclusivityRule.class,
  io.apicurio.datamodels.core.validation.rules.mutex.OasParameterExamplesMutualExclusivityRule.class,
  io.apicurio.datamodels.core.validation.rules.mutex.OasParameterSchemaContentMutualExclusivityRule.class,
  io.apicurio.datamodels.core.validation.rules.other.OasBodyParameterUniquenessValidationRule.class,
  io.apicurio.datamodels.core.validation.rules.other.OasIgnoredContentTypeHeaderRule.class,
  io.apicurio.datamodels.core.validation.rules.other.OasIgnoredHeaderParameterRule.class,
  io.apicurio.datamodels.core.validation.rules.other.OasOperationIdUniquenessValidationRule.class,
  io.apicurio.datamodels.core.validation.rules.other.OasParameterUniquenessValidationRule.class,
  io.apicurio.datamodels.core.validation.rules.other.OasUnknownPropertyRule.class,
  io.apicurio.datamodels.core.validation.rules.other.SecurityRequirementUniquenessValidationRule.class,
  io.apicurio.datamodels.core.validation.rules.other.TagUniquenessValidationRule.class,
  io.apicurio.datamodels.core.validation.rules.required.AaMissingCorrelationIdRule.class,
  io.apicurio.datamodels.core.validation.rules.required.AasMissingServerProtocolRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingApiKeySchemeParamLocationRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingApiKeySchemeParamNameRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingApiTitleRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingApiVersionRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingHttpSecuritySchemeTypeRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingLicenseNameRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingOAuthFlowAuthUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingOAuthFlowRokenUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingOAuthFlowScopesRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingOAuthSecuritySchemeFlowsRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingOpenIdConnectSecuritySchemeConnectUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingOperationDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingOperationIdRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingOperationSummaryRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingSecuritySchemeTypeRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingServerTemplateUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.required.MissingTagNameRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingApiInformationRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingApiPathsRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingBodyParameterSchemaRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingDiscriminatorPropertyNameRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingExternalDocumentationUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingHeaderArrayInformationRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingHeaderTypeRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingItemsArrayInformationRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingItemsTypeRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingOAuthSchemeAuthUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingOAuthSchemeFlowTypeRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingOAuthSchemeScopesRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingOAuthSchemeTokenUrlRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingOpenApiPropertyRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingOperationResponsesRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingOperationTagsRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingParameterArrayTypeRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingParameterLocationRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingParameterNameRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingParameterTypeRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingRequestBodyContentRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingResponseDefinitionDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingResponseDescriptionRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingSchemaArrayInformationRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasMissingServerVarDefaultValueRule.class,
  io.apicurio.datamodels.core.validation.rules.required.OasPathParamsMustBeRequiredRule.class,
  io.apicurio.datamodels.core.validation.rules.required.RequiredPropertyValidationRule.class,
  io.apicurio.datamodels.openapi.models.OasContact.class,
  io.apicurio.datamodels.openapi.models.OasDocument.class,
  io.apicurio.datamodels.openapi.models.OasExternalDocumentation.class,
  io.apicurio.datamodels.openapi.models.OasHeader.class,
  io.apicurio.datamodels.openapi.models.OasInfo.class,
  io.apicurio.datamodels.openapi.models.OasLicense.class,
  io.apicurio.datamodels.openapi.models.OasOperation.class,
  io.apicurio.datamodels.openapi.models.OasParameter.class,
  io.apicurio.datamodels.openapi.models.OasPathItem.class,
  io.apicurio.datamodels.openapi.models.OasPaths.class,
  io.apicurio.datamodels.openapi.models.OasResponse.class,
  io.apicurio.datamodels.openapi.models.OasResponses.class,
  io.apicurio.datamodels.openapi.models.OasSchema.class,
  io.apicurio.datamodels.openapi.models.OasSecurityRequirement.class,
  io.apicurio.datamodels.openapi.models.OasTag.class,
  io.apicurio.datamodels.openapi.models.OasXML.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Contact.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Definitions.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Document.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Example.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20ExternalDocumentation.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Header.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Headers.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Info.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Items.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20License.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Operation.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Parameter.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20ParameterDefinition.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20ParameterDefinitions.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20PathItem.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Paths.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Response.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20ResponseDefinition.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20ResponseDefinitions.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Responses.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Schema.Oas20AdditionalPropertiesSchema.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Schema.Oas20AllOfSchema.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Schema.Oas20ItemsSchema.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Schema.Oas20PropertySchema.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Schema.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20SchemaDefinition.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Scopes.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20SecurityDefinitions.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20SecurityRequirement.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20SecurityScheme.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20Tag.class,
  io.apicurio.datamodels.openapi.v2.models.Oas20XML.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30AuthorizationCodeOAuthFlow.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Callback.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30CallbackDefinition.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30CallbackPathItem.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30ClientCredentialsOAuthFlow.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Components.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Contact.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Discriminator.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Document.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Encoding.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Example.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30ExampleDefinition.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30ExternalDocumentation.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Header.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30HeaderDefinition.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30ImplicitOAuthFlow.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Info.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30License.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Link.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30LinkDefinition.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30LinkParameterExpression.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30LinkRequestBodyExpression.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30LinkServer.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30MediaType.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30OAuthFlows.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Operation.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Parameter.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30ParameterDefinition.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30PasswordOAuthFlow.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30PathItem.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Paths.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30RequestBody.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30RequestBodyDefinition.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Response.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30ResponseDefinition.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Responses.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Schema.Oas30AdditionalPropertiesSchema.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Schema.Oas30AllOfSchema.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Schema.Oas30AnyOfSchema.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Schema.Oas30ItemsSchema.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Schema.Oas30NotSchema.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Schema.Oas30OneOfSchema.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Schema.Oas30PropertySchema.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Schema.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30SchemaDefinition.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30SecurityRequirement.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30SecurityScheme.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Server.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30ServerVariable.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30Tag.class,
  io.apicurio.datamodels.openapi.v3.models.Oas30XML.class
})
public class ApicurioRegisterForReflection {

}
