package io.apicurio.registry.utils.tests;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@TestTemplate
@ExtendWith(RegistryRestServiceExtension.class)
public @interface RegistryRestServiceTest {

}
