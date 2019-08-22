package io.apicurio.registry.storage.impl.jpa.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(
        name = "meta",
        uniqueConstraints = @UniqueConstraint(columnNames = {"artifact_id", "version", "key"}),
        indexes = @Index(columnList = "artifact_id, version, key", unique = true)
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class MetaData {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "artifact_id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private String artifactId;

    @Column(name = "version", updatable = false)
    @EqualsAndHashCode.Include
    private Long version;

    @Column(name = "key", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private String key;

    @Column(name = "value", nullable = false)
    @Setter
    private String value;
}
