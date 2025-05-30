package io.apicurio.registry.operator;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import lombok.Getter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Keeps track of not only the annotations that have been added, but also of those that have been deleted.
 */
public class AnnotationManager {

    private final Map<String, String> annotations = new LinkedHashMap<>();

    private final Set<String> deletedAnnotations = new HashSet<>();

    @Getter
    private boolean changed;

    /**
     * Provide a new collection of annotations, replacing the previous ones.
     */
    public void replace(Map<String, String> source) {
        annotations.keySet().forEach(k -> {
            if (source.containsKey(k)) {
                deletedAnnotations.remove(k);
            } else {
                deletedAnnotations.add(k);
            }
        });
        annotations.clear();
        annotations.putAll(source);
    }

    /**
     * Update the target collection of annotations.
     */
    public void update(Map<String, String> target) {
        var original = new HashMap<>(target);
        deletedAnnotations.forEach(target::remove);
        target.putAll(annotations);
        changed = !original.equals(target);
    }

    public void reset() {
        annotations.clear();
        deletedAnnotations.clear();
    }

    public static void updateResourceAnnotations(Context<ApicurioRegistry3> context, HasMetadata resource, AnnotationManager annotations, Map<String, String> sourceAnnotations) {
        // We need to use the client to support annotation removal:
        var fresh = context.getClient().resource(resource).get();
        if (fresh != null) {
            annotations.replace(sourceAnnotations);
            annotations.update(fresh.getMetadata().getAnnotations());
            if (annotations.isChanged()) {
                context.getClient().resource(fresh).update();
                annotations.reset();
            }
        }
    }
}
