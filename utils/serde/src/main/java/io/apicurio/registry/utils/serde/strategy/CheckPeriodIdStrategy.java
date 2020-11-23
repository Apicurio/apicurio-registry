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

package io.apicurio.registry.utils.serde.strategy;

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.serde.AbstractKafkaStrategyAwareSerDe;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Ales Justin
 */
public abstract class CheckPeriodIdStrategy<T> implements GlobalIdStrategy<T> {

    static class CheckValue {
        public CheckValue(long ts, long id) {
            this.ts = ts;
            this.id = id;
        }

        long ts;
        long id;
    }

    private long checkPeriod;
    private Map<String, CheckValue> checkMap = new ConcurrentHashMap<>();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        Object cp = configs.get(AbstractKafkaStrategyAwareSerDe.REGISTRY_CHECK_PERIOD_CONFIG_PARAM);
        if (cp != null) {
            long checkPeriodParam;
            if (cp instanceof Number) {
                checkPeriodParam = ((Number) cp).longValue();
            } else if (cp instanceof String) {
                checkPeriodParam = Long.parseLong((String) cp);
            } else if (cp instanceof Duration) {
                checkPeriodParam = ((Duration) cp).toMillis();
            } else {
                throw new IllegalArgumentException("Check period config param type unsupported: " + cp);
            }
            if (checkPeriodParam < 0) {
                throw new IllegalArgumentException("Check period must be non-negative: " + checkPeriodParam);
            }
            this.checkPeriod = checkPeriodParam;
        }
    }

    abstract long findIdInternal(RegistryService service, String artifactId, ArtifactType artifactType, T schema);

    public long findId(RegistryService service, String artifactId, ArtifactType artifactType, T schema) {
        CheckValue cv = checkMap.compute(artifactId, (aID, v) -> {
            long now = System.currentTimeMillis();
            if (v == null) {
                long id = findIdInternal(service, artifactId, artifactType, schema);
                return new CheckValue(now, id);
            } else {
                if (v.ts + checkPeriod < now) {
                    long id = findIdInternal(service, artifactId, artifactType, schema);
                    v.ts = now;
                    v.id = id;
                }
                return v;
            }
        });
        return cv.id;
    }
}
