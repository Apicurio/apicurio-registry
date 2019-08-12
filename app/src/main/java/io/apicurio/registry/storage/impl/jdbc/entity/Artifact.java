package io.apicurio.registry.storage.impl.jdbc.entity;

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
    private String content;
}
