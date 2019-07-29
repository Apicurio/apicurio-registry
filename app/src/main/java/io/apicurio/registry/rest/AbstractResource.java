package io.apicurio.registry.rest;

import io.apicurio.registry.store.RegistryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * @author Ales Justin
 */
public abstract class AbstractResource {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Inject
    RegistryStore store;

    protected void checkSubject(String subject) {
        if (store.listSubjects().contains(subject) == false) {
            Errors.noSuchSubject(subject);
        }
    }
}
