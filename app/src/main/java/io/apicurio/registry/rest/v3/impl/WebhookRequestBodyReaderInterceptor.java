package io.apicurio.registry.rest.v3.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.apicurio.registry.rest.v3.beans.CreateWebhookSubscription;
import io.apicurio.registry.rest.v3.beans.UpdateWebhookSubscription;
import io.apicurio.registry.util.JsonObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Provider
public class WebhookRequestBodyReaderInterceptor implements ReaderInterceptor {

    /**
     * Far above the largest valid body (all fields at their maximum length is under
     * 8 KiB).
     */
    public static final int MAX_BODY_BYTES = 64 * 1024;

    private static final byte[] EMPTY_OBJECT = "{}".getBytes(StandardCharsets.UTF_8);

    private static final Map<Class<?>, Set<String>> DECLARED_FIELDS = Map.of(
            CreateWebhookSubscription.class, declaredFields(CreateWebhookSubscription.class),
            UpdateWebhookSubscription.class, declaredFields(UpdateWebhookSubscription.class));

    @Inject
    WebhookRequestBodyContext bodyContext;

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException {
        Set<String> declared = DECLARED_FIELDS.get(context.getType());
        if (declared == null) {
            return context.proceed();
        }

        byte[] body = context.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            bodyContext.setTooLarge(true);
            return proceedWith(context, EMPTY_OBJECT);
        }

        try {
            JsonNode node = JsonObjectMapper.MAPPER.readTree(body);
            if (node == null || !node.isObject()) {
                bodyContext.setMalformed(true);
                return proceedWith(context, EMPTY_OBJECT);
            }
            // Bind once here, ignoring unknown fields, so a type error is caught before the
            // real binding.
            JsonObjectMapper.MAPPER.readerFor(context.getType())
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(node);
            bodyContext.setUnrecognizedFields(unrecognizedFields(node, declared));
        } catch (IOException e) {
            bodyContext.setMalformed(true);
            return proceedWith(context, EMPTY_OBJECT);
        }
        return proceedWith(context, body);
    }

    private static Object proceedWith(ReaderInterceptorContext context, byte[] body) throws IOException {
        context.setInputStream(new ByteArrayInputStream(body));
        return context.proceed();
    }

    private static Set<String> unrecognizedFields(JsonNode node, Set<String> declared) {
        Set<String> unrecognized = new LinkedHashSet<>();
        for (Iterator<String> names = node.fieldNames(); names.hasNext();) {
            String name = names.next();
            if (!declared.contains(name)) {
                unrecognized.add(name);
            }
        }
        return unrecognized;
    }

    private static Set<String> declaredFields(Class<?> beanClass) {
        ObjectMapper mapper = JsonObjectMapper.MAPPER;
        return mapper.getDeserializationConfig().introspect(mapper.constructType(beanClass))
                .findProperties().stream().map(BeanPropertyDefinition::getName).collect(Collectors.toSet());
    }
}
