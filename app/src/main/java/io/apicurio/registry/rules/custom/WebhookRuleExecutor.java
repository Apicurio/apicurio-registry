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

package io.apicurio.registry.rules.custom;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.rest.v2.beans.WebhookCustomRuleConfig;
import io.apicurio.registry.rules.RuleContext;
import io.apicurio.registry.rules.RuleExecutor;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.webhook.WebhookExecuteRuleRequest;
import io.apicurio.registry.rules.webhook.WebhookExecuteRuleResponse;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
@Logged
public class WebhookRuleExecutor implements RuleExecutor {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String SIGNATURE_HEADER = "X-Registry-Signature-256";

    private WebClient httpClient;

    @Inject
    Logger logger;

    @Inject
    Vertx vertx;

    /**
     * @see io.apicurio.registry.rules.RuleExecutor#execute(io.apicurio.registry.rules.RuleContext)
     */
    @Override
    public void execute(RuleContext context) throws RuleViolationException {

        logger.trace("Executing webhook rule {}", context.getRuleId());

        WebhookCustomRuleConfig config;
        try {
            config = mapper.readValue(context.getConfiguration(), WebhookCustomRuleConfig.class);
        } catch (IOException e) {
            throw new RegistryException("Error parsing webhook custom rule configuration", e);
        }

        WebhookExecuteRuleRequest data = new WebhookExecuteRuleRequest();
        data.setGroupId(context.getGroupId());
        data.setArtifactId(context.getArtifactId());
//        data.setCurrentVersion(SIGNATURE_HEADER);
//        data.setUpdatedVersion(SIGNATURE_HEADER);
        data.setArtifactType(context.getArtifactType());
        if (context.getCurrentContent() != null) {
            data.setCurrentContentB64(Base64.getEncoder().encode(context.getCurrentContent().bytes()));
        }
        data.setUpdatedContentB64(Base64.getEncoder().encode(context.getUpdatedContent().bytes()));

        Buffer buffer;
        try {
            buffer = Buffer.buffer(mapper.writeValueAsBytes(data));
        } catch (IOException e) {
            throw new RegistryException("Error serializing webhook request body", e);
        }

        WebhookExecuteRuleResponse response = ConcurrentUtil.result(sendRequest(config, buffer));
        if (!response.isSuccess() && response.getErrorCauses() != null && !response.getErrorCauses().isEmpty()) {
            throw RuleViolationException.customRuleException("webhook rule execution, error causes received",
                        context.getRuleId(),
                        context.getConfiguration(),
                        response.getErrorCauses());
        } else if (!response.isSuccess()) {
            throw RuleViolationException.customRuleException("webhook rule execution not successfull but not error causes received",
                    context.getRuleId(),
                    context.getConfiguration(),
                    Collections.emptySet());
        }

    }

    private CompletionStage<WebhookExecuteRuleResponse> sendRequest(WebhookCustomRuleConfig config, Buffer data) {
        try {
            RequestOptions opts = new RequestOptions()
                    .setMethod(HttpMethod.POST)
                    .setAbsoluteURI(config.getUrl())
                    .putHeader("ce-id", UUID.randomUUID().toString())
                    .putHeader("ce-specversion", "1.0")
                    .putHeader("ce-source", "apicurio-registry")
                    .putHeader("ce-type", "execute-rule-request")
                    .putHeader("content-type", MediaType.APPLICATION_JSON);

            if (config.getSecret() != null && !config.getSecret().isEmpty()) {
                byte[] dataToSign = ArrayUtils.addAll(config.getSecret().getBytes(StandardCharsets.UTF_8), data.getBytes());
                String signature = DigestUtils.sha256Hex(dataToSign);
                String signatureValue = "sha256=" + signature;
                opts.putHeader(SIGNATURE_HEADER, signatureValue);
            }

            //TODO set timeouts
            return getHttpClient()
                .request(HttpMethod.POST, opts)
                .as(BodyCodec.json(WebhookExecuteRuleResponse.class))
                .sendBuffer(data)
                    .map(res -> {
                        if (res.statusCode() == 200) {
                            return res.body();
                        } else {
                            WebhookExecuteRuleResponse response = new WebhookExecuteRuleResponse();
                            response.setSuccess(false);
                            response.setErrorCauses(Set.of(new RuleViolation("webhook responded with status code " + res.statusCode(), "")));
                            return response;
                        }
                    })
                    .otherwise(ex -> {
                        logger.error("Error making webhook request to {}", config.getUrl(), ex);
                        WebhookExecuteRuleResponse response = new WebhookExecuteRuleResponse();
                        response.setSuccess(false);
                        response.setErrorCauses(Set.of(new RuleViolation("exception sending request to webhook:" + ex.getMessage(), "")));
                        return response;
                    })
                    .toCompletionStage();
        } catch (Exception e) {
            logger.error("Error sending webhook request to {}", config.getUrl(), e);
            throw new RegistryException(e);
        }
    }

    private synchronized WebClient getHttpClient() {
        if (httpClient == null) {
            httpClient = WebClient.create(vertx);
        }
        return httpClient;
    }

}
