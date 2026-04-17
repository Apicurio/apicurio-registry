package io.apicurio.registry.openapi.content.extract;

import io.apicurio.datamodels.models.Components;
import io.apicurio.datamodels.models.Document;
import io.apicurio.datamodels.models.Operation;
import io.apicurio.datamodels.models.Parameter;
import io.apicurio.datamodels.models.Schema;
import io.apicurio.datamodels.models.SecurityScheme;
import io.apicurio.datamodels.models.Server;
import io.apicurio.datamodels.models.Tag;
import io.apicurio.datamodels.models.asyncapi.AsyncApiChannelItem;
import io.apicurio.datamodels.models.asyncapi.AsyncApiMessage;
import io.apicurio.datamodels.models.asyncapi.AsyncApiOperation;
import io.apicurio.datamodels.models.asyncapi.AsyncApiServer;
import io.apicurio.datamodels.models.asyncapi.AsyncApiServers;
import io.apicurio.datamodels.models.asyncapi.AsyncApiChannel;
import io.apicurio.datamodels.models.asyncapi.AsyncApiMultiFormatSchema;
import io.apicurio.datamodels.models.asyncapi.v3x.AsyncApi3xOperations;
import io.apicurio.datamodels.models.openapi.OpenApiOperation;
import io.apicurio.datamodels.models.openapi.OpenApiPathItem;
import io.apicurio.datamodels.models.openapi.OpenApiServer;
import io.apicurio.datamodels.models.visitors.CombinedVisitorAdapter;
import io.apicurio.datamodels.util.NodeUtil;
import io.apicurio.registry.content.extract.StructuredElement;

import java.util.ArrayList;
import java.util.List;

/**
 * A visitor that extracts structured elements from OpenAPI and AsyncAPI documents for search indexing. Uses
 * the apicurio-data-models visitor pattern to traverse the document tree and collect named elements such as
 * schemas, paths, operations, tags, servers, parameters, security schemes, channels, and messages.
 *
 * <p>This visitor is shared between {@link OpenApiStructuredContentExtractor} and
 * {@code AsyncApiStructuredContentExtractor} since the {@link CombinedVisitorAdapter} handles both OpenAPI
 * and AsyncAPI node types. Node types that don't appear in a given document format are simply never visited.
 */
public class StructuredContentVisitor extends CombinedVisitorAdapter {

    private final List<StructuredElement> elements = new ArrayList<>();

    /**
     * Returns the list of structured elements extracted during the visit.
     *
     * @return the extracted structured elements
     */
    public List<StructuredElement> getElements() {
        return elements;
    }

    // ------------------------------------------------------------------
    // Shared visitors (both OpenAPI and AsyncAPI)
    // ------------------------------------------------------------------

    /**
     * Extracts component-level schema names. Only schemas that are direct map entries in a
     * {@link Components} node are captured (not inline property schemas).
     */
    @Override
    public void visitSchema(Schema node) {
        if (node.mapPropertyName() != null && node.parent() instanceof Components) {
            elements.add(new StructuredElement("schema", node.mapPropertyName()));
        }
    }

    /**
     * Extracts document-level tag names. Tags nested inside operations or channels are excluded.
     */
    @Override
    public void visitTag(Tag node) {
        if (node.getName() != null && node.parent() instanceof Document) {
            elements.add(new StructuredElement("tag", node.getName()));
        }
    }

    /**
     * Extracts component-level security scheme names.
     */
    @Override
    public void visitSecurityScheme(SecurityScheme node) {
        if (node.mapPropertyName() != null && node.parent() instanceof Components) {
            elements.add(new StructuredElement("security_scheme", node.mapPropertyName()));
        }
    }

    /**
     * Extracts operationIds from OpenAPI and AsyncAPI operations.
     *
     * <ul>
     *   <li>OpenAPI: extracts the operationId from each HTTP method operation.</li>
     *   <li>AsyncAPI 3.x: extracts the operation name (map key) for top-level operations.</li>
     *   <li>AsyncAPI 2.x: extracts the operationId from subscribe/publish channel operations via
     *       reflection, since getOperationId() is declared independently on each version-specific
     *       interface (v20–v26).</li>
     * </ul>
     */
    @Override
    public void visitOperation(Operation node) {
        if (node instanceof OpenApiOperation) {
            String opId = ((OpenApiOperation) node).getOperationId();
            if (opId != null) {
                elements.add(new StructuredElement("operation", opId));
            }
        } else if (node instanceof AsyncApiOperation) {
            if (node.mapPropertyName() != null && node.parent() instanceof AsyncApi3xOperations) {
                // AsyncAPI 3.x: top-level operations use the map key as the name
                elements.add(new StructuredElement("operation", node.mapPropertyName()));
            } else {
                // AsyncAPI 2.x: operationId is on version-specific interfaces
                String opId = getAsyncApiOperationId(node);
                if (opId != null) {
                    elements.add(new StructuredElement("operation", opId));
                }
            }
        }
    }

    /**
     * Extracts server information. For OpenAPI, extracts the server URL from document-level servers. For
     * AsyncAPI, extracts the server name (map key) from document-level servers.
     */
    @Override
    public void visitServer(Server node) {
        if (node instanceof OpenApiServer) {
            if (node.parent() instanceof Document) {
                String url = ((OpenApiServer) node).getUrl();
                if (url != null) {
                    elements.add(new StructuredElement("server", url));
                }
            }
        } else if (node instanceof AsyncApiServer) {
            if (node.mapPropertyName() != null && node.parent() instanceof AsyncApiServers) {
                elements.add(new StructuredElement("server", node.mapPropertyName()));
            }
        }
    }

    // ------------------------------------------------------------------
    // OpenAPI-specific visitors
    // ------------------------------------------------------------------

    /**
     * Extracts OpenAPI path names from the paths section.
     */
    @Override
    public void visitPathItem(OpenApiPathItem node) {
        if (node.mapPropertyName() != null) {
            elements.add(new StructuredElement("path", node.mapPropertyName()));
        }
    }

    /**
     * Extracts component-level parameter names (OpenAPI).
     */
    @Override
    public void visitParameter(Parameter node) {
        if (node.mapPropertyName() != null && node.parent() instanceof Components) {
            elements.add(new StructuredElement("parameter", node.mapPropertyName()));
        }
    }

    // ------------------------------------------------------------------
    // AsyncAPI-specific visitors
    // ------------------------------------------------------------------

    /**
     * Extracts AsyncAPI 2.x channel names.
     */
    @Override
    public void visitChannelItem(AsyncApiChannelItem node) {
        if (node.mapPropertyName() != null) {
            elements.add(new StructuredElement("channel", node.mapPropertyName()));
        }
    }

    /**
     * Extracts AsyncAPI 3.x channel names.
     */
    @Override
    public void visitChannel(AsyncApiChannel node) {
        if (node.mapPropertyName() != null) {
            elements.add(new StructuredElement("channel", node.mapPropertyName()));
        }
    }

    /**
     * Extracts component-level message names (AsyncAPI).
     */
    @Override
    public void visitMessage(AsyncApiMessage node) {
        if (node.mapPropertyName() != null && node.parent() instanceof Components) {
            elements.add(new StructuredElement("message", node.mapPropertyName()));
        }
    }

    /**
     * Extracts component-level multi-format schema names (AsyncAPI 3.x). In AsyncAPI 3.x, component
     * schemas may be represented as {@link AsyncApiMultiFormatSchema} instead of plain
     * {@link Schema} nodes.
     */
    @Override
    public void visitMultiFormatSchema(AsyncApiMultiFormatSchema node) {
        if (node.mapPropertyName() != null && node.parent() instanceof Components) {
            elements.add(new StructuredElement("schema", node.mapPropertyName()));
        }
    }

    /**
     * Gets the operationId from an AsyncAPI 2.x operation using reflection. All 2.x version-specific
     * interfaces (v20–v26) declare getOperationId() independently without a common parent type.
     */
    private String getAsyncApiOperationId(Operation node) {
        return (String) NodeUtil.getNodeProperty(node, "operationId");
    }
}
