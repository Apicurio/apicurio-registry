package io.apicurio.registry.storage.impl.panache.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(
        name = "content",
        uniqueConstraints = @UniqueConstraint(columnNames = "contentHash"),
        indexes = {@Index(columnList = "canonicalHash"),
                @Index(columnList = "contentHash")}

)
public class Content extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "contentId")
    public Long contentId;

    @Column(name = "canonicalHash")
    public String canonicalHash;

    @Column(name = "contentHash")
    public String contentHash;

    @Column(name = "content")
    @Lob
    public byte[] content;
}
