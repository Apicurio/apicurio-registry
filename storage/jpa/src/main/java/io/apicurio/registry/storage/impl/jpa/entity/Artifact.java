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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;


@Entity
@Table(
        name = "artifacts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"artifact_id", "version"}),
        indexes = @Index(columnList = "artifact_id, version", unique = true)
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Artifact {

    @Id
    @GeneratedValue
    @Column(name = "global_id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private Long globalId;

    @Column(name = "artifact_id", updatable = false, nullable = false)
    private String artifactId;

    @Column(name = "version", updatable = false, nullable = false)
    private Long version;

    @Column(name = "value", updatable = false, nullable = false)
    @Lob
    @Basic(fetch = FetchType.EAGER)
    private byte[] content;
}
