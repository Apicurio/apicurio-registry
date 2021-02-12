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

package io.apicurio.tests.serdes.apicurio;

import static io.apicurio.tests.common.Constants.SERDES;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.avro.Schema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.serdes.KafkaClients;

/**
 * @author Fabian Martinez
 */
@Tag(SERDES)
public class ApicurioSerDesIT extends BaseIT {

    @Test
    @Tag(ACCEPTANCE)
    void testAvroApicurioSerDes() throws InterruptedException, ExecutionException, TimeoutException {
        String topicName = TestUtils.generateTopic();
        String subjectName = topicName + "-value";
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecordapicurio1\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}");
        createArtifactViaApicurioClient(registryClient, schema, subjectName);

        KafkaClients.produceAvroApicurioMessagesTopicStrategy(topicName, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroApicurioMessages(topicName, 10).get(5, TimeUnit.SECONDS);
    }

}
