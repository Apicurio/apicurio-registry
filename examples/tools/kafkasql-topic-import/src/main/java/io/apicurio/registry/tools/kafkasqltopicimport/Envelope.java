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

package io.apicurio.registry.tools.kafkasqltopicimport;

import lombok.*;

import java.util.List;


/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class Envelope {

    private String topic;

    private Integer partition;

    private Long offset;

    private String tstype;

    private Long ts;

    private Long broker;

    private List<String> headers;

    private String key;

    private String payload;
}
