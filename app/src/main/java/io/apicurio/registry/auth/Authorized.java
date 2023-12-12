package io.apicurio.registry.auth;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@InterceptorBinding
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface Authorized {

    @Nonbinding
    AuthorizedStyle style() default AuthorizedStyle.GroupAndArtifact;

    @Nonbinding
    AuthorizedLevel level() default AuthorizedLevel.Read;

}
