package io.apicurio.registry.operator.unit;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.utils.JavaOptsAppend;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class JavaOptsAppendTest {

    @Test
    public void testJavaOpts_Empty() throws Exception {
        JavaOptsAppend optsAppend = new JavaOptsAppend();
        Assertions.assertThat(optsAppend.isEmpty()).isTrue();
        Assertions.assertThat(optsAppend.toEnvVar()).isNotNull();
        Assertions.assertThat(optsAppend.toEnvVar().getName()).isEqualTo(EnvironmentVariables.JAVA_OPTS_APPEND);
        Assertions.assertThat(optsAppend.toEnvVar().getValue()).isEqualTo("");
    }

    @Test
    public void testJavaOpts_EmptyEnvVar() throws Exception {
        JavaOptsAppend optsAppend = new JavaOptsAppend();
        optsAppend.setOptsFromEnvVar("");
        Assertions.assertThat(optsAppend.isEmpty()).isTrue();
        Assertions.assertThat(optsAppend.toEnvVar()).isNotNull();
        Assertions.assertThat(optsAppend.toEnvVar().getName()).isEqualTo(EnvironmentVariables.JAVA_OPTS_APPEND);
        Assertions.assertThat(optsAppend.toEnvVar().getValue()).isEqualTo("");
    }

    @Test
    public void testJavaOpts_EnvVar() throws Exception {
        JavaOptsAppend optsAppend = new JavaOptsAppend();
        optsAppend.setOptsFromEnvVar("-Xms512m -Xmx1g -XX:+UseG1GC -Dspring.profiles.active=prod");
        Assertions.assertThat(optsAppend.isEmpty()).isFalse();
        Assertions.assertThat(optsAppend.toEnvVar()).isNotNull();
        Assertions.assertThat(optsAppend.toEnvVar().getName()).isEqualTo(EnvironmentVariables.JAVA_OPTS_APPEND);
        Assertions.assertThat(optsAppend.toEnvVar().getValue()).isEqualTo("-Dspring.profiles.active=prod -XX:+UseG1GC -Xms512m -Xmx1g");
    }

    @Test
    public void testJavaOpts_EnvVarWithAdds() throws Exception {
        JavaOptsAppend optsAppend = new JavaOptsAppend();
        optsAppend.setOptsFromEnvVar("-Xms512m -Xmx1g");
        optsAppend.addOpt("-XX:+UseG1GC");
        optsAppend.addOpt("-Dspring.profiles.active=prod");
        Assertions.assertThat(optsAppend.isEmpty()).isFalse();
        Assertions.assertThat(optsAppend.toEnvVar()).isNotNull();
        Assertions.assertThat(optsAppend.toEnvVar().getName()).isEqualTo(EnvironmentVariables.JAVA_OPTS_APPEND);
        Assertions.assertThat(optsAppend.toEnvVar().getValue()).isEqualTo("-Dspring.profiles.active=prod -XX:+UseG1GC -Xms512m -Xmx1g");
    }

    @Test
    public void testJavaOpts_ParamConflict() throws Exception {
        JavaOptsAppend optsAppend = new JavaOptsAppend();
        optsAppend.addOpt("-Dspring.profiles.active=prod");
        optsAppend.addOpt("-Dspring.profiles.active=dev");
        Assertions.assertThat(optsAppend.toEnvVar().getValue()).isEqualTo("-Dspring.profiles.active=prod");

        optsAppend = new JavaOptsAppend();
        optsAppend.addOpt("-XX:ReservedCodeCacheSize=128m");
        optsAppend.addOpt("-XX:ReservedCodeCacheSize=256m");
        Assertions.assertThat(optsAppend.toEnvVar().getValue()).isEqualTo("-XX:ReservedCodeCacheSize=128m");

        optsAppend = new JavaOptsAppend();
        optsAppend.addOpt("-XX:ReservedCodeCacheSize=128m");
        optsAppend.addOpt("-XX:ReservedCodeCacheSize=256m");
        optsAppend.addOpt("-XX:+HeapDumpOnOutOfMemoryError");
        optsAppend.addOpt("-XX:+HeapDumpOnOutOfMemoryError");
        optsAppend.addOpt("-XX:HeapDumpPath=/path/to/dumps/");
        optsAppend.addOpt("-XX:HeapDumpPath=/path/to/other-dumps/");
        Assertions.assertThat(optsAppend.toEnvVar().getValue()).isEqualTo("-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/path/to/dumps/ -XX:ReservedCodeCacheSize=128m");

        optsAppend = new JavaOptsAppend();
        optsAppend.setOptsFromEnvVar("-XX:ReservedCodeCacheSize=128m -XX:+HeapDumpOnOutOfMemoryError");
        optsAppend.addOpt("-XX:ReservedCodeCacheSize=256m");
        optsAppend.addOpt("-XX:+HeapDumpOnOutOfMemoryError");
        optsAppend.addOpt("-XX:HeapDumpPath=/path/to/dumps/");
        optsAppend.addOpt("-XX:HeapDumpPath=/path/to/other-dumps/");
        Assertions.assertThat(optsAppend.toEnvVar().getValue()).isEqualTo("-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/path/to/dumps/ -XX:ReservedCodeCacheSize=128m");
    }

    @Test
    public void testJavaOpts_Conflicts() throws Exception {
        JavaOptsAppend optsAppend = new JavaOptsAppend();
        optsAppend.setOptsFromEnvVar("-Xms512m -Xmx1g -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005");
        optsAppend.addOpt("-Xms256m");
        optsAppend.addOpt("-Xmx2g");
        optsAppend.addOpt("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:12345");
        optsAppend.addOpt("-javaagent:/path/to/agent.jar");
        optsAppend.addOpt("-javaagent:/path/to/alt_agent.jar");
        Assertions.assertThat(optsAppend.isEmpty()).isFalse();
        Assertions.assertThat(optsAppend.toEnvVar()).isNotNull();
        Assertions.assertThat(optsAppend.toEnvVar().getName()).isEqualTo(EnvironmentVariables.JAVA_OPTS_APPEND);
        Assertions.assertThat(optsAppend.toEnvVar().getValue()).isEqualTo("-Xms512m -Xmx1g -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -javaagent:/path/to/agent.jar");
    }

}
