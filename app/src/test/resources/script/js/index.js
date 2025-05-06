const yaml = require('yaml');

// TODO: export is not working because the script is inlined,
// how to integrate with proper require or something?
function contentCanonicalizer(input) {
    console.log("JS - invoked contentCanonicalizer");

    return {
        typedContent: {
            contentType: "string",
            ...input.content
        }
    };
};

function compatibilityChecker(input) {
    console.log("JS - invoked compatibilityChecker");

    var proposedArtifact = yaml.parse(input.proposedArtifact.content);

    if (input.existingArtifacts !== undefined) {
        for (const existing of input.existingArtifacts) {
            var existingArtifact = yaml.parse(existing.content);
            if (proposedArtifact.version <= existingArtifact.version) {
                throw_validity_exception(
                    [{ description: `Expected new version number but found identical versions: ${proposedArtifact.version}` }]
                );
            }
        }
    }

    return {
        incompatibleDifferences: []
    };
}

// TODO: how to implement dereferencing in JS land?
const DEREFERENCED_RAML_CONTENT = `---
title: "Mobile Order API"
baseUri: "http://localhost:8081/api"
version: 1.0
uses:
  assets: "assets.lib.raml"
annotationTypes:
  monitoringInterval:
    type: "integer"
/orders:
  displayName: "Orders"
  get:
    is:
    - "assets.paging"
    (monitoringInterval): 30
    description: "Lists all orders of a specific user"
    queryParameters:
      userId:
        type: "string"
        description: "use to query all orders of a user"
  post: null
  /{orderId}:
    get:
      responses:
        "200":
          body:
            application/json:
              type: "assets.Order"
            application/xml:
              type: "ORDER_XSD_CONTENT"
`;

function contentDereferencer(input) {
    console.log("JS - invoked contentDereferencer");

    return {
        typedContent: {
            contentType: "string",
            content: DEREFERENCED_RAML_CONTENT
        }
    }
}

function contentAccepter(input) {
    console.log("JS - invoked contentAccepter");

    return true;
}

// TODO: let's define the UX and tight things up
globalThis.contentCanonicalizer = (content) => contentCanonicalizer(content);
globalThis.compatibilityChecker = (content) => compatibilityChecker(content);
globalThis.contentDereferencer = (content) => contentDereferencer(content);
globalThis.contentAccepter = (content) => contentAccepter(content);
