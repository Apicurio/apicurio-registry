package io.apicurio.registry.storage.impl.panache.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(
        name = "labels",
        indexes = {@Index(columnList = "label")}
)
public class Label extends PanacheEntity {

    @ManyToOne
    @JoinColumn(name="globalId", nullable=false)
    public Version version;

    @Column(name = "label")
    public String label;
}
