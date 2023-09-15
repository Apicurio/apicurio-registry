package io.apicurio.registry.storage.impl.gitops.model.v0;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@SuperBuilder
@NoArgsConstructor
@Setter
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Registry extends HasSchema {

    private String id;

    private List<Rule> globalRules;

    private List<Setting> settings;
}
