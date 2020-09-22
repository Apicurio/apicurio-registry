package io.apicurio.registry.storage.impl.panache.repository;

import io.apicurio.registry.storage.impl.panache.entity.Label;
import io.apicurio.registry.storage.impl.panache.entity.Version;
import io.quarkus.hibernate.orm.panache.PanacheRepository;

import javax.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class LabelRepository implements PanacheRepository<Label> {

    public void persistLabels(List<String> labels, Version version) {
        if (labels != null && !labels.isEmpty()) {
            labels.forEach(labelStr -> {

                Label label = new Label();
                label.label = labelStr;
                label.version = version;
                persist(label);
            });
        }
    }
}
