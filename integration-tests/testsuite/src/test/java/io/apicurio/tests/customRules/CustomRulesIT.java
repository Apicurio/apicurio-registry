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
package io.apicurio.tests.customRules;

import io.apicurio.registry.rest.client.exception.RuleViolationException;
import io.apicurio.registry.rest.v2.beans.CustomRule;
import io.apicurio.registry.rest.v2.beans.CustomRuleBinding;
import io.apicurio.registry.rest.v2.beans.RuleViolationError;
import io.apicurio.registry.rest.v2.beans.WebhookCustomRuleConfig;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.webhook.WebhookExecuteRuleRequest;
import io.apicurio.registry.rules.webhook.WebhookExecuteRuleResponse;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.CustomRuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioV2BaseIT;
import io.apicurio.tests.common.Constants;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Tag(Constants.SMOKE)
@DisabledIfEnvironmentVariable(named = Constants.CURRENT_ENV, matches = Constants.CURRENT_ENV_K8S_REGEX)
class CustomRulesIT extends ApicurioV2BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomRulesIT.class);

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testCustomWebhookGlobal() throws Exception {
        AtomicInteger reqCounter = new AtomicInteger(0);
        var server = Vertx.vertx().createHttpServer(new HttpServerOptions().setPort(3333))
            .requestHandler(req -> {
                LOGGER.info("received webhook request");
                if ("/validate".equals(req.path()) && req.method() == HttpMethod.POST) {
                    reqCounter.incrementAndGet();
                    req.body()
                        .map(buf -> {
                            try {
                                return mapper.readValue(buf.getBytes(), WebhookExecuteRuleRequest.class);
                            } catch (IOException e1) {
                                throw new UncheckedIOException(e1);
                            }
                        })
                        .onSuccess(werr -> {
                            WebhookExecuteRuleResponse wres = new WebhookExecuteRuleResponse();
                            LOGGER.info("Executing custom webhook for artifact {}", werr.getArtifactId());
                            if (werr.getArtifactId().contains("fail")) {
                                wres.setSuccess(false);
                                wres.setErrorCauses(Set.of(new RuleViolation("fail test", "hello")));
                            } else {
                                wres.setSuccess(true);
                            }
                            try {
                                req.response().setStatusCode(200).end(Buffer.buffer(mapper.writeValueAsBytes(wres)));
                            } catch (JsonProcessingException e) {
                                LOGGER.error("Error in webhook server", e);
                                req.response().setStatusCode(500).end();
                            }
                        })
                        .onFailure(t -> {
                            LOGGER.error("Error in webhook server", t);
                            req.response().setStatusCode(500).end();
                        });

                } else {
                    LOGGER.info("webhook server, path not found {}", req.path());
                    req.response().setStatusCode(404).end();
                }
            })
            .listen()
            .toCompletionStage()
            .toCompletableFuture()
            .get(10, TimeUnit.SECONDS);


        try {

            CustomRule cr = new CustomRule();
            cr.setCustomRuleType(CustomRuleType.webhook);
            cr.setDescription("desc");
            cr.setId("test-webhook");
            cr.setSupportedArtifactType(null);
            WebhookCustomRuleConfig config = new WebhookCustomRuleConfig();
            config.setUrl("http://localhost:3333/validate");
            config.setSecret("test secret");
            cr.setWebhookConfig(config);

            registryClient.createCustomRule(cr);

            // Verify the rule was added.
            retryOp((c) -> {
                long count = c.listCustomRules().size();
                assertEquals(1, count);
            });

            //enable the custom rule globally
            CustomRuleBinding binding = new CustomRuleBinding();
            binding.setCustomRuleId(cr.getId());
            registryClient.createGlobalCustomRuleBinding(binding);

            retryOp((c) -> {
                long count = c.listGlobalCustomRuleBindings().size();
                assertEquals(1, count);
            });

            String groupId = TestUtils.generateArtifactId();
            String artifactId = "test-ok";

            String v1Content = resourceToString("artifactTypes/" + "protobuf/tutorial_v1.proto");
            String v2Content = resourceToString("artifactTypes/" + "protobuf/tutorial_v2.proto");

            createArtifact(groupId, artifactId, ArtifactType.PROTOBUF, IoUtil.toStream(v1Content));
            createArtifactVersion(groupId, artifactId, IoUtil.toStream(v2Content));

            Assertions.assertThrows(RuleViolationException.class, () -> createArtifact(groupId, "test-fail", ArtifactType.PROTOBUF, IoUtil.toStream(v1Content)));

            try {
                createArtifact(groupId, "test-fail-2", ArtifactType.PROTOBUF, IoUtil.toStream(v1Content));
                Assertions.fail();
            } catch (RuleViolationException rvex) {

                Object error = rvex.getError();

                Assertions.assertTrue(error instanceof RuleViolationError);

                RuleViolationError rve = (RuleViolationError) error;
                assertEquals(1, rve.getCauses().size());
                assertEquals("fail test", rve.getCauses().get(0).getDescription());
                assertEquals("hello", rve.getCauses().get(0).getContext());

            } catch (Exception e) {
                Assertions.fail(e);
            }

            assertEquals(4, reqCounter.get());

        } finally {
            server.close();
        }

    }

    @AfterEach
    void deleteCustomRules() throws Exception {
        registryClient.listCustomRules()
            .forEach(cr -> {
                registryClient.deleteCustomRule(cr.getId());
            });
        retryOp((rc) -> {
            var rules = rc.listCustomRules();
            assertEquals(0, rules.size(), "All custom rules not deleted");
        });
    }
}

