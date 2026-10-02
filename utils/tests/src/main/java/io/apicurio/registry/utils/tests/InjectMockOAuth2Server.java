package io.apicurio.registry.utils.tests;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to inject the {@link no.nav.security.mock.oauth2.MockOAuth2Server}
 * instance from {@link MockOAuth2TestResource} into test class fields.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InjectMockOAuth2Server {
}