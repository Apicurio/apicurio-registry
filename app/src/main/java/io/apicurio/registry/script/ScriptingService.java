package io.apicurio.registry.script;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.RuleType;
import io.quarkus.arc.Arc;
import io.roastedroot.quickjs4j.annotations.HostFunction;
import io.roastedroot.quickjs4j.annotations.JsModule;
import io.roastedroot.quickjs4j.core.Builtins;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Runner;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class ScriptingService {

    private static final ObjectMapper mapper = Arc.container().instance(ObjectMapper.class).get();

    @JsModule
    static final class ScriptingABI implements AutoCloseable {
        private final Runner runner;
        private final String library;
        private Class outputClass;
        private Object result;

        ScriptingABI(String library) {
            this.library = library;
            var builtins = Builtins.builder().add(ScriptingABI_Builtins.toBuiltins(this)).build();
            var engine = Engine.builder().withBuiltins(builtins).build();
            this.runner = Runner.builder().withEngine(engine).build();
        }

        public void exec(String code) {
            runner.compileAndExec(library + "\n" + code);
        }

        @HostFunction("java_set_result")
        public void setResult(JsonNode output) {
            try {
                result = mapper.readerFor(outputClass).readValue(output);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // TODO: should this be more generic?
        // let start like this ...
        @HostFunction("throw_validity_exception")
        public void throwValidityException(JsonNode rawViolations) {
            try {
                // TODO: handle collections in the framework!
                List<RuleViolation> violationList = mapper
                        .readerForListOf(RuleViolation.class)
                        .readValue(rawViolations);
                Set<RuleViolation> violations = new HashSet<>();
                violations.addAll(violationList);
                throw new RuleViolationException(
                        "RAML validation failed.",
                        RuleType.VALIDITY,
                        ValidityLevel.FULL.name(), // TODO: fixme hardcoded
                        violations);
            } catch (IOException e) {
                String stringRawViolations = "<failed to serialize>";
                try {
                    stringRawViolations = mapper.writeValueAsString(rawViolations);
                } catch (JsonProcessingException jsonE) {
                    // ignore
                }
                throw new RuntimeException("Received: " + stringRawViolations + " but expected RuleViolation[]");
            }
        }

        public void setOutputClass(Class outputClass) {
            this.outputClass = outputClass;
        }

        public Object result() {
            return result;
        }

        @Override
        public void close() {
            runner.close();
        }
    }

    public <I, O> O executeScript(String script, String scriptType, I input, Class<O> outputClass) throws ScriptExecutionException {
        // TODO: this is extremely suboptimal as we will be loading the library on every call
        // but let's start with something
        try {
            String scriptContent = new String(Files.readAllBytes(Path.of(script)), StandardCharsets.UTF_8);

            try (ScriptingABI jsRunner = new ScriptingABI(scriptContent)) {
                jsRunner.setOutputClass(outputClass);
                jsRunner.exec("java_set_result(" + scriptType + "(" + mapper.writeValueAsString(input) + "));");
                return (O) jsRunner.result();
            } catch (RuntimeException re) {
                // TODO: this doesn't looks great, let's see a better way to integrate
                if (re.getCause() != null &&
                        re.getCause().getCause() != null &&
                        re.getCause() instanceof ExecutionException) {
                    if (re.getCause().getCause() instanceof RegistryException) {
                        throw (RegistryException) re.getCause().getCause();
                    }
                }
                throw re;
//            } catch (ChicoryException e) {
//                throw new ScriptExecutionException(e);
            }
        } catch (IOException e) {
            throw new ScriptExecutionException("Failed to load script from path: " + script, e);
        }
    }

}
