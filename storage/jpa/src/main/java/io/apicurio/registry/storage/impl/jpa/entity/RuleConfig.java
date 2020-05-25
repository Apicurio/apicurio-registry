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
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(
        name = "rule_config",
        uniqueConstraints = @UniqueConstraint(columnNames = {"rule_id", "key"}),
        indexes = @Index(columnList = "rule_id, key", unique = true)
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class RuleConfig {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private Long id;

    @OnDelete(action = OnDeleteAction.CASCADE) // JPA cascade does not work on uni-directional refs
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "rule_id")
    @EqualsAndHashCode.Include
    private Rule rule;

    @Column(name = "key", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private String key;

    @Column(name = "value", nullable = false)
    @Setter
    private String value;
}
