/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.connector;

import io.apicurio.registry.search.client.SearchClient;
import io.apicurio.registry.search.common.Search;
import io.apicurio.registry.utils.IoUtil;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * @author Ales Justin
 */
public class SearchTask extends SinkTask {
    private static final Logger log = LoggerFactory.getLogger(SearchTask.class);

    private SearchClient client;

    @Override
    public void start(Map<String, String> props) {
        SearchSinkConnectorConfig config = new SearchSinkConnectorConfig(props);
        Properties properties = new Properties();
        properties.putAll(config.originals());
        client = SearchClient.create(properties);
        if (config.getBoolean(SearchSinkConnectorConfig.SEARCH_CLIENT_INITIALIZE)) {
            try {
                client.initialize(false);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    public void put(Collection<SinkRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        try {
            List<Search.Artifact> artifacts = records.stream()
                                                     .map(ConnectRecord::value)
                                                     .map(Search.Artifact.class::cast)
                                                     .collect(Collectors.toList());

            log.info("Indexing artifacts ({}) ... ", artifacts.size());
            client.index(artifacts);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void stop() {
        IoUtil.closeIgnore(client);
    }

    @Override
    public String version() {
        return Version.getVersion();
    }
}
