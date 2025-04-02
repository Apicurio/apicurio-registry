package io.apicurio.utils.test.raml.microsvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.compatibility.CompatibilityDifference;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.compatibility.SimpleCompatibilityDifference;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.webhooks.beans.CompatibilityCheckerRequest;
import io.apicurio.registry.types.webhooks.beans.CompatibilityCheckerResponse;
import io.apicurio.registry.types.webhooks.beans.ContentAccepterRequest;
import io.apicurio.registry.types.webhooks.beans.ContentCanonicalizerRequest;
import io.apicurio.registry.types.webhooks.beans.ContentCanonicalizerResponse;
import io.apicurio.registry.types.webhooks.beans.ContentDereferencerRequest;
import io.apicurio.registry.types.webhooks.beans.ContentDereferencerResponse;
import io.apicurio.registry.types.webhooks.beans.ContentValidatorRequest;
import io.apicurio.registry.types.webhooks.beans.ContentValidatorResponse;
import io.apicurio.registry.types.webhooks.beans.IncompatibleDifference;
import io.apicurio.registry.types.webhooks.beans.ReferenceFinderRequest;
import io.apicurio.registry.types.webhooks.beans.ReferenceFinderResponse;
import io.apicurio.registry.types.webhooks.beans.ResolvedReference;
import io.apicurio.registry.types.webhooks.beans.ResolvedReferenceUrl;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RamlTestMicroService extends AbstractVerticle {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final int port;
    private HttpServer server;

    public RamlTestMicroService(int port) {
        this.port = port;
    }

    @Override
    public void start() {
        server = vertx.createHttpServer();

        server.requestHandler(req -> {
            if (!"POST".equals(req.method().name())) {
                req.response()
                        .setStatusCode(405)
                        .putHeader("content-type", "text/plain")
                        .end("Method Not Allowed");
                return;
            }

            if (!"application/json".equalsIgnoreCase(req.getHeader("content-type"))) {
                req.response()
                        .setStatusCode(400)
                        .putHeader("content-type", "text/plain")
                        .end("Bad Request: Content-Type must be application/json");
                return;
            }

            req.bodyHandler(body -> {
                handleRequest(req, body.toString());
            });
        });

        server.listen(port, result -> {
            if (result.succeeded()) {
                System.out.println("Server started on port " + port);
            } else {
                System.out.println("Failed to start server: " + result.cause());
            }
        });
    }

    public void stopServer() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (server != null) {
            server.close(ar -> {
                if (ar.succeeded()) {
                    future.complete(null);
                } else {
                    future.completeExceptionally(ar.cause());
                }
            });
        } else {
            future.complete(null);
        }
    }

    private void handleRequest(HttpServerRequest req, String body) {
        String path = req.path();
        try {
            switch (path) {
                case "/contentAccepter":
                    handleContentAccepter(req, body);
                    break;
                case "/compatibilityChecker":
                    handleCompatibilityChecker(req, body);
                    break;
                case "/contentCanonicalizer":
                    handleContentCanonicalizer(req, body);
                    break;
                case "/contentValidator":
                    handleContentValidator(req, body);
                    break;
                case "/contentDereferencer":
                    handleContentDereferencer(req, body);
                    break;
                case "/referenceFinder":
                    handleReferenceFinder(req, body);
                    break;
                default:
                    req.response()
                            .setStatusCode(404)
                            .putHeader("content-type", "text/plain")
                            .end("Not Found");
            }
        } catch (Exception e) {
            req.response()
                    .setStatusCode(500)
                    .putHeader("content-type", "text/plain")
                    .end("Server error: " + e.getMessage()); // TODO include the stack trace in the response
        }
    }

    private void handleContentAccepter(HttpServerRequest req, String body) throws Exception {
        ContentAccepterRequest request = objectMapper.readValue(body, ContentAccepterRequest.class);
        RamlContentAccepter accepter = new RamlContentAccepter();
        boolean accepted = accepter.acceptsContent(toServerBean(request.getTypedContent()), toServerBean(request.getResolvedReferences()));
        req.response().putHeader("content-type", "application/json").end(String.valueOf(accepted));
    }

    private void handleCompatibilityChecker(HttpServerRequest req, String body) throws Exception {
        CompatibilityCheckerRequest request = objectMapper.readValue(body, CompatibilityCheckerRequest.class);
        RamlCompatibilityChecker compatibilityChecker = new RamlCompatibilityChecker();
        CompatibilityLevel level = CompatibilityLevel.valueOf(request.getLevel());
        List<TypedContent> existingArtifacts = request.getExistingArtifacts() == null ? Collections.emptyList() :
                request.getExistingArtifacts().stream().map(this::toServerBean).collect(Collectors.toList());
        TypedContent proposedArtifact = toServerBean(request.getProposedArtifact());
        Map<String, TypedContent> resolvedRefs = toServerBean(request.getResolvedReferences());
        CompatibilityExecutionResult result = compatibilityChecker.testCompatibility(level, existingArtifacts, proposedArtifact, resolvedRefs);

        CompatibilityCheckerResponse response = new CompatibilityCheckerResponse();
        response.setIncompatibleDifferences(toBean(result.getIncompatibleDifferences()));
        req.response().putHeader("content-type", "application/json").end(objectMapper.writeValueAsString(response));
    }

    private void handleContentCanonicalizer(HttpServerRequest req, String body) throws Exception {
        ContentCanonicalizerRequest request = objectMapper.readValue(body, ContentCanonicalizerRequest.class);
        RamlContentCanonicalizer canonicalizer = new RamlContentCanonicalizer();
        TypedContent typedContent = toServerBean(request.getContent());
        Map<String, TypedContent> resolvedRefs = toServerBean(request.getResolvedReferences());
        TypedContent canonicalizedContent = canonicalizer.canonicalize(typedContent, resolvedRefs);

        ContentCanonicalizerResponse response = new ContentCanonicalizerResponse();
        response.setTypedContent(toBean(canonicalizedContent));
        req.response().putHeader("content-type", "application/json").end(objectMapper.writeValueAsString(response));
    }

    private void handleContentValidator(HttpServerRequest req, String body) throws Exception {
        ContentValidatorRequest request = objectMapper.readValue(body, ContentValidatorRequest.class);
        RamlContentValidator validator = new RamlContentValidator();
        TypedContent content = toServerBean(request.getContent());
        ValidityLevel level = ValidityLevel.valueOf(request.getLevel());
        ContentValidatorRequest.Function function = request.getFunction();

        ContentValidatorResponse response = new ContentValidatorResponse();
        try {
            if (function == ContentValidatorRequest.Function.validate) {
                Map<String, TypedContent> resolvedRefs = toServerBean(request.getResolvedReferences());
                validator.validate(level, content, resolvedRefs);
            } else if (function == ContentValidatorRequest.Function.validateReferences) {
                List<ArtifactReference> artifactRefs = toServerBean2(request.getReferences());
                validator.validateReferences(content, artifactRefs);
            }
        } catch (RuleViolationException e) {
            Set<RuleViolation> causes = e.getCauses();
            response.setRuleViolations(causes.stream().map(cause -> {
                io.apicurio.registry.types.webhooks.beans.RuleViolation violation = new io.apicurio.registry.types.webhooks.beans.RuleViolation();
                violation.setContext(cause.getContext());
                violation.setDescription(cause.getDescription());
                return violation;
            }).toList());
        }
        req.response().putHeader("content-type", "application/json").end(objectMapper.writeValueAsString(response));
    }

    private void handleContentDereferencer(HttpServerRequest req, String body) throws Exception {
        ContentDereferencerRequest request = objectMapper.readValue(body, ContentDereferencerRequest.class);
        RamlContentDereferencer contentDereferencer = new RamlContentDereferencer();
        TypedContent typedContent = toServerBean(request.getContent());

        ContentDereferencerResponse response = new ContentDereferencerResponse();
        if (request.getFunction() == ContentDereferencerRequest.Function.dereference) {
            Map<String, TypedContent> resolvedRefs = toServerBean(request.getResolvedReferences());
            TypedContent dereferencedContent = contentDereferencer.dereference(typedContent, resolvedRefs);
            response.setTypedContent(toBean(dereferencedContent));
        } else if (request.getFunction() == ContentDereferencerRequest.Function.rewriteReferences) {
            Map<String, String> resolvedReferenceUrls = toServerBean3(request.getResolvedReferenceUrls());
            TypedContent rewrittenContent = contentDereferencer.rewriteReferences(typedContent, resolvedReferenceUrls);
            response.setTypedContent(toBean(rewrittenContent));
        }
        req.response().putHeader("content-type", "application/json").end(objectMapper.writeValueAsString(response));
    }

    private void handleReferenceFinder(HttpServerRequest req, String body) throws Exception {
        ReferenceFinderRequest request = objectMapper.readValue(body, ReferenceFinderRequest.class);
        RamlReferenceFinder referenceFinder = new RamlReferenceFinder();
        TypedContent typedContent = toServerBean(request.getTypedContent());
        Set<ExternalReference> externalReferences = referenceFinder.findExternalReferences(typedContent);

        ReferenceFinderResponse response = new ReferenceFinderResponse();
        response.setExternalReferences(toBean2(externalReferences));
        req.response().putHeader("content-type", "application/json").end(objectMapper.writeValueAsString(response));
    }

    private TypedContent toServerBean(io.apicurio.registry.types.webhooks.beans.TypedContent typedContent) {
        return TypedContent.create(typedContent.getContent(), typedContent.getContentType());
    }

    private io.apicurio.registry.types.webhooks.beans.TypedContent toBean(TypedContent typedContent) {
        io.apicurio.registry.types.webhooks.beans.TypedContent bean = new io.apicurio.registry.types.webhooks.beans.TypedContent();
        bean.setContent(typedContent.getContent().content());
        bean.setContentType(typedContent.getContentType());
        return bean;
    }

    private Map<String, TypedContent> toServerBean(List<ResolvedReference> resolvedReferences) {
        Map<String, TypedContent> rval = new HashMap<>();
        for (ResolvedReference ref : resolvedReferences) {
            rval.put(ref.getName(), TypedContent.create(ref.getContent(), ref.getContentType()));
        }
        return rval;
    }

    private static List<ArtifactReference> toServerBean2(List<io.apicurio.registry.types.webhooks.beans.ArtifactReference> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        return references.stream().map(ar -> {
            ArtifactReference ref = new ArtifactReference();
            ref.setName(ar.getName());
            ref.setGroupId(ar.getGroupId());
            ref.setArtifactId(ar.getArtifactId());
            ref.setVersion(ar.getVersion());
            return ref;
        }).toList();
    }

    private List<IncompatibleDifference> toBean(Set<CompatibilityDifference> incompatibleDifferences) {
        return incompatibleDifferences == null ? List.of() :
                incompatibleDifferences.stream().map(this::toBean).toList();
    }

    private List<io.apicurio.registry.types.webhooks.beans.ExternalReference> toBean2(Set<ExternalReference> externalReferences) {
        return externalReferences == null ? List.of() :
                externalReferences.stream().map(this::toBean).toList();
    }

    private IncompatibleDifference toBean(CompatibilityDifference difference) {
        IncompatibleDifference incompatibleDifference = new IncompatibleDifference();
        if (difference instanceof SimpleCompatibilityDifference) {
            incompatibleDifference.setContext(difference.asRuleViolation().getContext());
            incompatibleDifference.setDescription(difference.asRuleViolation().getDescription());
        }
        return incompatibleDifference;
    }

    private io.apicurio.registry.types.webhooks.beans.ExternalReference toBean(ExternalReference externalReference) {
        io.apicurio.registry.types.webhooks.beans.ExternalReference ref = new io.apicurio.registry.types.webhooks.beans.ExternalReference();
        ref.setComponent(externalReference.getComponent());
        ref.setResource(externalReference.getResource());
        ref.setFullReference(externalReference.getFullReference());
        return ref;
    }

    private Map<String, String> toServerBean3(List<ResolvedReferenceUrl> resolvedReferenceUrls) {
        if (resolvedReferenceUrls == null || resolvedReferenceUrls.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> rval = new HashMap<>();
        for (ResolvedReferenceUrl rrurl : resolvedReferenceUrls) {
            rval.put(rrurl.getName(), rrurl.getUrl());
        }
        return rval;
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 6060;
        Vertx vertx = Vertx.vertx();
        RamlTestMicroService verticle = new RamlTestMicroService(port);
        vertx.deployVerticle(verticle);
    }
}
