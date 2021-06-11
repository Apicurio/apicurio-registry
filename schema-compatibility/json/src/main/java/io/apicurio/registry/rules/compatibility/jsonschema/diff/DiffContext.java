/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rules.compatibility.jsonschema.diff;

import lombok.Getter;
import org.everit.json.schema.SchemaLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public class DiffContext {

    private static final Logger log = LoggerFactory.getLogger(DiffContext.class);

    private final Set<Difference> diff = new HashSet<>();

    private DiffContext parentContext;

    private DiffContext rootContext;

    @Getter
    private final String pathUpdated;

    final Set<SchemaLocation> visited = new HashSet<>();


    private DiffContext(DiffContext rootContext, DiffContext parentContext, String pathUpdated, Set<SchemaLocation> visited) {
        this.rootContext = rootContext;
        this.parentContext = parentContext;
        this.pathUpdated = pathUpdated;
        this.visited.addAll(visited);
    }


    public DiffContext sub(String pathFragmentUpdated) {
        return new DiffContext(rootContext, this, pathUpdated + "/" + pathFragmentUpdated, this.visited);
    }


    private void initRootContext(DiffContext rootContext) {
        if (this.rootContext != null || parentContext != null)
            throw new IllegalStateException();
        this.rootContext = rootContext;
        parentContext = rootContext;
    }

    public static DiffContext createRootContext(String basePathFragmentUpdated, Set<SchemaLocation> visited) {
        if(visited == null)
            visited = new HashSet<>();
        DiffContext rootContext = new DiffContext(null, null, basePathFragmentUpdated, visited);
        rootContext.initRootContext(rootContext);
        return rootContext;
    }

    public static DiffContext createRootContext() {
        return createRootContext("", null);
    }


    private void addToDifferenceSets(Difference difference) {
        diff.add(difference);
        if (rootContext != this)
            parentContext.addToDifferenceSets(difference);
    }


    public void addDifference(DiffType type, Object originalSubchema, Object updatedSubchema) {
        Difference difference = Difference.builder()
             .diffType(type)
             .pathOriginal("")
             .pathUpdated(pathUpdated)
             .subSchemaOriginal(Objects.toString(originalSubchema)) // make sure toString is good enough
             .subSchemaUpdated(Objects.toString(updatedSubchema))
             .build();
        addToDifferenceSets(difference);
//        if(!type.isBackwardsCompatible())
//            log.warn("New incompatible difference found: " + difference);
    }

    public void log(String message) {
        log.debug("[Context path (updated): {}]{}", pathUpdated, message);
    }

    public Set<Difference> getDiff() {
        return new HashSet<>(diff);
    }


    /**
     * Return true, if this context contains an incompatible difference.
     */
    public boolean foundIncompatibleDifference() {
        return diff.stream().anyMatch(d -> !d.getDiffType().isBackwardsCompatible());
    }

    public Set<Difference> getIncompatibleDifferences() {
        return diff.stream().filter(d -> !d.getDiffType().isBackwardsCompatible()).collect(Collectors.toSet());
    }

    public boolean foundAllDifferencesAreCompatible() {
        return !foundIncompatibleDifference();
    }

    @Override
    public String toString() {
        return "DiffContext {" +
                " foundAllDifferencesAreCompatible = " + foundAllDifferencesAreCompatible() +
                ", diff = " + diff +
                ", pathAtUpdated = '" + pathUpdated + "'" +
                " }";
    }
}