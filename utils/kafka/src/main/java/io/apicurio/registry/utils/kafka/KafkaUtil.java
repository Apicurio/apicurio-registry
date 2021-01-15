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

package io.apicurio.registry.utils.kafka;

import io.apicurio.registry.utils.ConcurrentUtil;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * @author Ales Justin
 */
public class KafkaUtil {
    private static final Logger log = LoggerFactory.getLogger(KafkaUtil.class);

    public static <T> T result(KafkaFuture<T> kf) {
        CompletionStage<T> cs = toCompletionStage(kf);
        return ConcurrentUtil.result(cs);
    }

    public static <T> CompletionStage<T> toCompletionStage(KafkaFuture<T> kf) {
        CompletableFuture<T> cf = new CompletableFuture<>();
        kf.whenComplete((v, t) -> {
            if (t != null) {
                cf.completeExceptionally(t);
            } else {
                cf.complete(v);
            }
        });
        return cf;
    }

    /**
     * Create topics with sensible defaults.
     *
     * @param properties the Kafka properties to create Kafka admin
     * @param topicNames topics to create, if they don't exist
     * @param topicConfig the config to use for the new topic
     */
    public static void createTopics(Properties properties, Set<String> topicNames, Map<String, String> topicConfig) {
        try (Admin admin = Admin.create(properties)) {
            createTopics(admin, topicNames, topicConfig);
        }
    }
    public static void createTopics(Properties properties, Set<String> topicNames) {
        createTopics(properties, topicNames, null);
    }

    /**
     * Create topics with sensible defaults.
     *
     * @param admin      the Kafka admin to use
     * @param topicNames topics to create, if they don't exist
     * @param topicConfig the config to use for the new topic
     */
    public static void createTopics(Admin admin, Set<String> topicNames, Map<String, String> topicConfig) {
        ConcurrentUtil.result(createTopicsAsync(admin, topicNames, topicConfig));
    }
    public static void createTopics(Admin admin, Set<String> topicNames) {
        createTopics(admin, topicNames, null);
    }

    /**
     * Create topics with sensible defaults, async.
     *
     * @param admin      the Kafka admin to use
     * @param topicNames topics to create, if they don't exist
     */
    public static CompletionStage<Void> createTopicsAsync(Admin admin, Set<String> topicNames, Map<String, String> topicConfig) {
        List<CompletionStage<NewTopic>> topicsToCreate = new ArrayList<>();
        return toCompletionStage(admin.listTopics().names())
                .thenCompose(topics -> {
                    for (String topicName : topicNames) {
                        createTopic(admin, topics, topicsToCreate, topicName, topicConfig);
                    }
                    //noinspection SuspiciousToArrayCall
                    return CompletableFuture.allOf(topicsToCreate.toArray(new CompletableFuture[0]));
                })
                .thenCompose(v -> {
                    if (topicsToCreate.size() > 0) {
                        return toCompletionStage(
                                admin.createTopics(
                                        topicsToCreate.stream()
                                                .map(ConcurrentUtil::result)
                                                .collect(Collectors.toList())
                                )
                                .all()
                        );
                    } else {
                        return CompletableFuture.completedFuture(null);
                    }
                });
    }

    private static void createTopic(Admin admin, Set<String> topics, List<CompletionStage<NewTopic>> topicsToCreate, String topicName, Map<String, String> topicConfig) {
        if (!topics.contains(topicName)) {
            KafkaFuture<NewTopic> newTopicKF = admin.describeCluster().nodes().thenApply(nodes -> {
                Map<String, String> configs = new HashMap<>();
                if (topicConfig != null) {
                    configs.putAll(topicConfig);
                }
                log.info("Creating new Kafka topic: {}", topicName);
                int replicationFactor = Math.min(3, nodes.size());
                if (configs.containsKey("replication.factor")) {
                    replicationFactor = Integer.parseInt(configs.get("replication.factor"));
                }
                int minimumInSyncReplicas = Math.max(replicationFactor - 1, 1);
                configs.putIfAbsent(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(minimumInSyncReplicas));
                return new NewTopic(topicName, 1, (short) replicationFactor).configs(configs);
            }).whenComplete((nt, t) -> log.info("Created new topic: {}", topicName, t));
            topicsToCreate.add(toCompletionStage(newTopicKF));
        }
    }
}
