package io.apicurio.registry.storage.impl.jdbc.entity;

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
