package io.apicurio.registry.storage.impl.panache.entity;


import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.sql.Timestamp;


@Entity
@Table(
        name = "artifacts",
        indexes = @Index(columnList = "artifactId")
)
public class Artifact extends PanacheEntityBase {

    public Artifact() {
    }

    public Artifact(String artifactId, String artifactType, String createdBy, Timestamp createdOn, Long latest) {
        this.artifactId = artifactId;
        this.artifactType = artifactType;
        this.createdBy = createdBy;
        this.createdOn = createdOn;
        this.latest = latest;
    }

    @Id
    @Column(name = "artifactId", updatable = false, nullable = false)
    public String artifactId;

    @Column(name = "artifactType")
    public String artifactType;

    @Column(name = "createdBy")
    public String createdBy;

    @Column(name = "createdOn")
    public Timestamp createdOn;

    @Column(name = "latest")
    public Long latest;
}

