package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.ArtifactTypeConfiguration;
import io.apicurio.registry.config.artifactTypes.JavaClassProvider;
import io.apicurio.registry.config.artifactTypes.Provider;
import io.apicurio.registry.config.artifactTypes.ScriptProvider;
import io.apicurio.registry.config.artifactTypes.WebhookProvider;
import io.apicurio.registry.http.HttpClientException;
import io.apicurio.registry.http.HttpClientService;
import org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractConfiguredArtifactTypeUtil<T> {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final HttpClientService httpClientService;
    protected final T delegate;

    protected class AbstractWebhookDelegate<I, O> {

        protected final ArtifactTypeConfiguration artifactType;
        protected final WebhookProvider provider;

        protected AbstractWebhookDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) {
            this.artifactType = artifactType;
            this.provider = provider;
        }

        protected O invokeHook(I requestBody, Class<O> outputClass) throws HttpClientException {
            return httpClientService.post(provider.getUrl(), requestBody, outputClass);
        }
    }

    protected class AbstractJavaClassDelegate {
        protected final ArtifactTypeConfiguration artifactType;
        protected final JavaClassProvider provider;
        protected final Class<T> javaClass;
        protected final T instance;

        public AbstractJavaClassDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception {
            this.artifactType = artifactType;
            this.provider = provider;
            this.javaClass = (Class<T>) loadJavaClassFromProvider(provider);
            this.instance = (T) instantiateJavaClass(javaClass);
        }

        private Class<?> loadJavaClassFromProvider(JavaClassProvider provider) throws Exception {
            try {
                String fqcn = provider.getClassname();
                return ClassUtils.getClass(fqcn);
            } catch (ClassNotFoundException e) {
                throw new Exception("JavaClass artifact type util failed (class not found): " + provider.getClassname());
            }
        }

        private T instantiateJavaClass(Class<?> javaClass) throws Exception {
            try {
                return (T) javaClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new Exception("JavaClass artifact type util failed (could not instantiate class): " + e.getMessage(), e);
            }
        }
    }

    public AbstractConfiguredArtifactTypeUtil(HttpClientService httpClientService, ArtifactTypeConfiguration artifactType, Provider provider) {
        this.httpClientService = httpClientService;
        try {
            this.delegate = createDelegate(artifactType, provider);
        } catch (Exception e) {
            throw new ArtifactTypeConfigurationException("Error detected configuring artifact type: " + artifactType.getArtifactType(), e);
        }
    }

    private T createDelegate(ArtifactTypeConfiguration artifactType, Provider provider) throws Exception {
        if (provider instanceof WebhookProvider) {
            return createWebhookDelegate(artifactType, (WebhookProvider) provider);
        } else if (provider instanceof JavaClassProvider) {
            return createJavaClassDelegate(artifactType, (JavaClassProvider) provider);
        } else if (provider instanceof ScriptProvider) {
            throw new Exception("ScriptProvider not yet implemented.");
        } else {
            throw new Exception("Unknown provider type: " + provider.getClass().getName());
        }
    }

    protected abstract T createWebhookDelegate(ArtifactTypeConfiguration artifactType, WebhookProvider provider) throws Exception;

    protected abstract T createJavaClassDelegate(ArtifactTypeConfiguration artifactType, JavaClassProvider provider) throws Exception;
}
