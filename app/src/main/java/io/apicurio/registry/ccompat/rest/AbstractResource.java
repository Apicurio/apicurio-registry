package io.apicurio.registry.ccompat.rest;

import io.apicurio.registry.ccompat.store.RegistryStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * @author Ales Justin
 */
public abstract class AbstractResource {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Inject
    RegistryStorageFacade facade;

    protected void checkSubject(String subject) {
        if (facade.listSubjects().contains(subject) == false) {
            Errors.noSuchSubject(subject);
        }
    }
}
