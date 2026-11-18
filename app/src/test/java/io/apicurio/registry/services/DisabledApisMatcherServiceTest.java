package io.apicurio.registry.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisabledApisMatcherServiceTest {

    private DisabledApisMatcherService service;

    @BeforeEach
    void setUp() {
        service = new DisabledApisMatcherService();
        service.log = LoggerFactory.getLogger(DisabledApisMatcherService.class);
        service.disableRegexps = Optional.of(List.of("/apis/ccompat/.*", "/apis/registry/v2/.*"));
        service.init(null);
    }

    @Test
    void normalizePathRemovesMatrixParametersAndDotSegments() {
        assertEquals("/apis/ccompat/v7/subjects", DisabledApisMatcherService.normalizePath("/apis/ccompat;x=1/v7/subjects"));
        assertEquals("/apis/ccompat/v7/subjects", DisabledApisMatcherService.normalizePath("/apis;x/ccompat/v7;a=b;c=d/subjects"));
        assertEquals("/apis/ccompat/v7/subjects", DisabledApisMatcherService.normalizePath("//apis/./ccompat/x/../v7/subjects"));
        assertEquals("/apis/ccompat/v7/subjects", DisabledApisMatcherService.normalizePath("/apis/%63compat/v7/subjects"));
        assertEquals("/apis/registry/v2/", DisabledApisMatcherService.normalizePath("/apis/registry/v2;x/"));
        assertEquals("/", DisabledApisMatcherService.normalizePath("/../.."));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/apis/ccompat/v7/subjects",
            "/apis/ccompat;x=1/v7/subjects",
            "/apis;x/ccompat/v7/subjects",
            "/apis/ccompat/v7;x/subjects",
            "/apis/%63compat/v7/subjects",
            "/apis/%63compat;x/v7/subjects",
            "//apis/ccompat/v7/subjects",
            "/apis/x/../ccompat/v7/subjects",
            "/apis/registry/v2;x/search/artifacts",
            "/apis/registry;x/v2/search/artifacts"
    })
    void disabledPathsAreDisabledInEveryForm(String path) {
        assertTrue(service.isDisabled(path), path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/apis/registry/v3/search/artifacts",
            "/apis/registry;x=1/v3/search/artifacts",
            "/apis/ccompatible/v7/subjects"
    })
    void otherPathsAreNotDisabled(String path) {
        assertFalse(service.isDisabled(path), path);
    }

    @Test
    void nullPathIsNotDisabled() {
        assertFalse(service.isDisabled(null));
    }
}
