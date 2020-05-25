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

package io.apicurio.registry.storage.impl.jpa.entity;

import io.apicurio.registry.types.RuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(
        name = "rules",
        uniqueConstraints = @UniqueConstraint(columnNames = {"artifact_id", "name"}),
        indexes = @Index(columnList = "artifact_id")
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Rule {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "artifact_id", updatable = false)
    private String artifactId;

    @Column(name = "name", updatable = false, nullable = false)
    @Enumerated(EnumType.STRING)
    private RuleType name;
}
