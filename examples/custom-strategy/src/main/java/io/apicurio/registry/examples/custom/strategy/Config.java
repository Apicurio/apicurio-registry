/*
 * Copyright 2020 JBoss Inc
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

package io.apicurio.registry.examples.custom.strategy;

/**
 * @author eric.wittmann@gmail.com
 */
public class Config {

    public static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v2";
    public static final String SERVERS = "localhost:9092";
    public static final String TOPIC_NAME = CustomStrategyExample.class.getSimpleName();
    public static final String SUBJECT_NAME = "Greeting";
    public static final String SCHEMA = "{\"type\":\"record\",\"name\":\"Greeting\",\"fields\":[{\"name\":\"Message\",\"type\":\"string\"},{\"name\":\"Time\",\"type\":\"long\"}]}";

}
