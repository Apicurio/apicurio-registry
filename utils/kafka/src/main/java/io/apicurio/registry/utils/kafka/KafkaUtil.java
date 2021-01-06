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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.utils.ConcurrentUtil;

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
     * @param admin the Kafka admin to use
     * @param topicNames topics to create, if they don't exist
     * @param topicConfig the config to use for the new topic
     */
    public static void createTopics(Admin admin, Set<String> topicNames, Map<String, String> topicConfig) {
        if (topicConfig == null) {
            topicConfig = new HashMap<>();
        }

        Set<String> topics = result(admin.listTopics().names());
        List<NewTopic> topicsToCreate = new ArrayList<>();
        for (String topicName : topicNames) {
            NewTopic newTopic = createTopic(admin, topics, topicName, topicConfig);
            if (newTopic != null) {
                topicsToCreate.add(newTopic);
            }
        }
        if (topicsToCreate.size() > 0) {
            result(admin.createTopics(topicsToCreate).all());
        }
    }
    public static void createTopics(Admin admin, Set<String> topicNames) {
        createTopics(admin, topicNames, null);
    }
    
    private static NewTopic createTopic(Admin admin, Set<String> existingTopicNames, String topicName, final Map<String, String> topicProperties) {
        if (!existingTopicNames.contains(topicName)) {
            KafkaFuture<NewTopic> newTopicKF = admin.describeCluster().nodes().thenApply(nodes -> {
                log.info("Creating new Kafka topic: {}", topicName);
                int replicationFactor = Math.min(3, nodes.size());
                if (topicProperties.containsKey("replication.factor")) {
                    replicationFactor = Integer.parseInt(topicProperties.get("replication.factor"));
                }
                int minimumInSyncReplicas = Math.max(replicationFactor - 1, 1);
                topicProperties.putIfAbsent(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(minimumInSyncReplicas));
                return new NewTopic(topicName, 1, (short) replicationFactor).configs(topicProperties);
            }).whenComplete((nt, t) -> log.info("Created new topic: {}", topicName, t));
            NewTopic newTopic = KafkaUtil.result(newTopicKF);
            return newTopic;
        }
        return null;
    }
}
