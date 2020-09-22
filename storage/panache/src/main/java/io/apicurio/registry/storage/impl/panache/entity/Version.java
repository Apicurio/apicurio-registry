package io.apicurio.registry.storage.impl.panache.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.sql.Timestamp;
import java.util.Set;

@Entity
@Table(
        name = "versions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"artifactId", "version"}),
        indexes = {@Index(columnList = "artifactId, version", unique = true),
                @Index(columnList = "version"),
                @Index(columnList = "state"),
                @Index(columnList = "name"),
                @Index(columnList = "description"),
                @Index(columnList = "createdBy"),
                @Index(columnList = "createdOn"),
                @Index(columnList = "globalId"),
                @Index(columnList = "contentId")}
)
public class Version extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @Column(name = "globalId")
    public Long globalId;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "contentId", referencedColumnName = "contentId")
    public Content content;

    @ManyToOne
    @JoinColumn(name="artifactId", nullable=false)
    public Artifact artifact;

    @Column(name = "version")
    public Long version;

    @Column(name = "state")
    public String state;

    @Column(name = "name")
    public String name;

    @Column(name = "description")
    public String description;

    @Column(name = "createdBy")
    public String createdBy;

    @Column(name = "createdOn")
    public Timestamp createdOn;

    @OneToMany(mappedBy = "version")
    public Set<Label> labels;

    @OneToMany(mappedBy = "version")
    public Set<Label> properties;

    @Column(name = "labels", columnDefinition = "text")
    public String labelsStr;

    @Column(name = "properties", columnDefinition = "text")
    public String propertiesStr;
}
