/*
 * Copyright 2020 IBM
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
package io.apicurio.registry.utils.serde;

import java.util.Map;

public enum AvroEncoding {

    BINARY,
    JSON;

    public static final String AVRO_JSON = "JSON";

    public static final String AVRO_BINARY = "BINARY";

    public static AvroEncoding fromConfig(Map<String, ?> config){
        AvroEncoding encoding = AvroEncoding.BINARY;
        if(config.containsKey(SerdeConfig.AVRO_ENCODING)){
            try {
                encoding = AvroEncoding.valueOf((String) config.get(SerdeConfig.AVRO_ENCODING));
            }
            catch (IllegalArgumentException ex){
            }
        }
        return encoding;
    }
}
