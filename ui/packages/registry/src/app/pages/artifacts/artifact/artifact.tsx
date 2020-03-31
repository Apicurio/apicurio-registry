/**
 * @license
 * Copyright 2020 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from "react";
import {Flex, FlexItem, PageSection, PageSectionVariants, Spinner} from '@patternfly/react-core';
import "./artifact.css";
import {PageComponent, PageProps, PageState} from "../../basePage";
import {ArtifactPageHeader} from "./components/pageheader";
import {RedocStandalone} from "redoc";


/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactPageProps extends PageProps {
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ArtifactPageState extends PageState {
    artifactContent: object | undefined;
}

/**
 * The artifacts page.
 */
export class ArtifactPage extends PageComponent<ArtifactPageProps, ArtifactPageState> {

    constructor(props: Readonly<ArtifactPageProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <React.Fragment>
                <PageSection className="ps_artifacts-header" variant={PageSectionVariants.light}>
                    <ArtifactPageHeader onUploadVersion={console.info} />
                </PageSection>
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    {
                        this.state.isLoading ?
                            <Flex>
                                <FlexItem><Spinner size="lg"/></FlexItem>
                                <FlexItem><span>Loading, please wait...</span></FlexItem>
                            </Flex>
                        :
                            <RedocStandalone spec={this.state.artifactContent} />
                    }
                </PageSection>
            </React.Fragment>
        );
    }

    protected initializeState(): ArtifactPageState {
        return {
            artifactContent: undefined,
            isLoading: true
        };
    }

    protected loadPageData(): void {
        const oaiString: string = `
{
    "openapi": "3.0.0",
    "info": {
        "title": "IBM Event Streams Schema Registry",
        "version": "0.0.1"
    },
    "paths": {
        "/ready": {
            "get": {
                "responses": {
                    "200": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/responses/Ok"
                                }
                            }
                        },
                        "description": "Server is running and the schema store is available"
                    },
                    "500": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/Error"
                                }
                            }
                        },
                        "description": "Server is running but there is a problem"
                    }
                },
                "summary": "Checks if the server is ready to receive requests.",
                "description": "Intended for use with a Kubernetes readiness probe.\\n\\nIt will verify that the schema data directory is still available, so should\\nfail if there is a problem with the file system."
            }
        },
        "/live": {
            "get": {
                "responses": {
                    "200": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/responses/Ok"
                                }
                            }
                        },
                        "description": "Server is running"
                    }
                },
                "summary": "Checks if the server is running.",
                "description": "Intended for use with a Kubernetes liveness probe.\\n\\nIt doesn't do anything other than return a static OK response, as a verification\\nthat the server is reachable and responsive."
            }
        },
        "/api/schemas": {
            "get": {
                "parameters": [
                    {
                        "name": "page",
                        "description": "Pagination parameter",
                        "schema": {
                            "default": 0,
                            "minimum": 0,
                            "type": "integer"
                        },
                        "in": "query",
                        "required": false
                    },
                    {
                        "name": "per_page",
                        "description": "Pagination parameter",
                        "schema": {
                            "default": 100,
                            "minimum": 1,
                            "type": "integer"
                        },
                        "in": "query",
                        "required": false
                    }
                ],
                "responses": {
                    "200": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "array",
                                    "items": {
                                        "$ref": "#/components/schemas/SchemaListItem"
                                    }
                                }
                            }
                        },
                        "description": "Schemas"
                    },
                    "401": {
                        "$ref": "#/components/responses/UnauthorizedError"
                    }
                },
                "summary": "List of schemas"
            },
            "post": {
                "requestBody": {
                    "content": {
                        "application/json": {
                            "schema": {
                                "$ref": "#/components/schemas/NewSchema"
                            }
                        }
                    },
                    "required": true
                },
                "parameters": [
                    {
                        "name": "verify",
                        "description": "If true, the provided schema definition is validated only, and not stored.",
                        "schema": {
                            "default": false,
                            "type": "boolean"
                        },
                        "in": "query",
                        "required": false
                    }
                ],
                "responses": {
                    "200": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/SchemaDefinition"
                                }
                            }
                        },
                        "description": "Verified. Returned for successful requests when verify=true"
                    },
                    "201": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/SchemaInfo"
                                }
                            }
                        },
                        "description": "Created. Returned for successful requests when verify=false"
                    },
                    "400": {
                        "$ref": "#/components/responses/InvalidSchema"
                    },
                    "401": {
                        "$ref": "#/components/responses/UnauthorizedError"
                    }
                },
                "summary": "Create a new schema"
            }
        },
        "/api/schemas/{schemaid}": {
            "get": {
                "responses": {
                    "200": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/SchemaInfo"
                                }
                            }
                        },
                        "description": "Schema info"
                    },
                    "401": {
                        "$ref": "#/components/responses/UnauthorizedError"
                    },
                    "404": {
                        "$ref": "#/components/responses/NotFound"
                    }
                },
                "summary": "Metadata about a single schema"
            },
            "delete": {
                "responses": {
                    "204": {
                        "description": "Deleted"
                    },
                    "401": {
                        "$ref": "#/components/responses/UnauthorizedError"
                    },
                    "404": {
                        "$ref": "#/components/responses/NotFound"
                    }
                },
                "summary": "Delete an entire schema"
            },
            "patch": {
                "requestBody": {
                    "content": {
                        "application/json": {
                            "schema": {
                                "$ref": "#/components/schemas/SchemaModificationPatch"
                            }
                        }
                    },
                    "required": true
                },
                "responses": {
                    "200": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/SchemaInfo"
                                }
                            }
                        },
                        "description": "Schema modified"
                    },
                    "401": {
                        "$ref": "#/components/responses/UnauthorizedError"
                    },
                    "404": {
                        "$ref": "#/components/responses/NotFound"
                    }
                },
                "summary": "Modify a whole schema"
            },
            "parameters": [
                {
                    "name": "schemaid",
                    "schema": {
                        "description": "Lower-case URL-encoded version of the schema name.",
                        "type": "string"
                    },
                    "in": "path",
                    "required": true
                }
            ]
        },
        "/api/schemas/{schemaid}/versions": {
            "post": {
                "requestBody": {
                    "content": {
                        "application/json": {
                            "schema": {
                                "$ref": "#/components/schemas/NewSchemaVersion"
                            }
                        }
                    },
                    "required": true
                },
                "parameters": [
                    {
                        "name": "verify",
                        "description": "If true, the provided schema definition is validated only, and not stored.",
                        "schema": {
                            "default": false,
                            "type": "boolean"
                        },
                        "in": "query",
                        "required": false
                    }
                ],
                "responses": {
                    "200": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/SchemaDefinition"
                                }
                            }
                        },
                        "description": "Verified. Returned for successful requests when verify=true"
                    },
                    "201": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/SchemaInfo"
                                }
                            }
                        },
                        "description": "Created. Returned for successful requests when verify=false"
                    },
                    "401": {
                        "$ref": "#/components/responses/UnauthorizedError"
                    },
                    "404": {
                        "$ref": "#/components/responses/NotFound"
                    },
                    "409": {
                        "$ref": "#/components/responses/IdenticalSchemaError"
                    }
                },
                "summary": "Upload a new version of an existing schema"
            },
            "parameters": [
                {
                    "name": "schemaid",
                    "schema": {
                        "description": "Lower-case URL-encoded version of the schema name.",
                        "type": "string"
                    },
                    "in": "path",
                    "required": true
                }
            ]
        },
        "/api/schemas/{schemaid}/versions/{versionnum}": {
            "get": {
                "parameters": [
                    {
                        "name": "versionnum",
                        "schema": {
                            "$ref": "#/components/schemas/VersionNumber"
                        },
                        "in": "path",
                        "required": true
                    }
                ],
                "responses": {
                    "200": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/Schema"
                                }
                            },
                            "application/vnd.apache.avro+json": {
                                "schema": {
                                    "$ref": "#/components/schemas/SchemaDefinition"
                                }
                            }
                        },
                        "description": "Schema info"
                    },
                    "401": {
                        "$ref": "#/components/responses/UnauthorizedError"
                    },
                    "404": {
                        "$ref": "#/components/responses/NotFound"
                    }
                },
                "summary": "A single version of a single schema"
            },
            "delete": {
                "parameters": [
                    {
                        "name": "versionnum",
                        "schema": {
                            "type": "string"
                        },
                        "in": "path",
                        "required": true
                    }
                ],
                "responses": {
                    "204": {
                        "description": "Deleted"
                    },
                    "401": {
                        "$ref": "#/components/responses/UnauthorizedError"
                    },
                    "404": {
                        "$ref": "#/components/responses/NotFound"
                    }
                },
                "summary": "Delete a single version of a schema"
            },
            "patch": {
                "requestBody": {
                    "content": {
                        "application/json": {
                            "schema": {
                                "$ref": "#/components/schemas/SchemaVersionModificationPatch"
                            }
                        }
                    },
                    "required": true
                },
                "parameters": [
                    {
                        "name": "versionnum",
                        "schema": {
                            "type": "string"
                        },
                        "in": "path",
                        "required": true
                    }
                ],
                "responses": {
                    "200": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/SchemaInfo"
                                }
                            }
                        },
                        "description": "Schema modified"
                    },
                    "401": {
                        "$ref": "#/components/responses/UnauthorizedError"
                    },
                    "404": {
                        "$ref": "#/components/responses/NotFound"
                    }
                },
                "summary": "Modify a single version of a schema"
            },
            "parameters": [
                {
                    "name": "schemaid",
                    "schema": {
                        "description": "Lower-case URL-encoded version of the schema name.",
                        "type": "string"
                    },
                    "in": "path",
                    "required": true
                }
            ]
        }
    },
    "components": {
        "schemas": {
            "SchemaName": {
                "description": "User-provided name for a schema. Not necessarily unique.",
                "maxLength": 100,
                "minLength": 1,
                "type": "string"
            },
            "SchemaDate": {
                "format": "date-time",
                "description": "Timestamp for when a schema version.\\n\\nWhen used for an overall schema, this will be the timestamp for when the\\nmost recent version was created.",
                "type": "string",
                "example": "2019-01-17T11:39:05.386Z"
            },
            "SchemaVersion": {
                "description": "Definition of a version of a schema.",
                "required": [
                    "id",
                    "name",
                    "date",
                    "state",
                    "enabled"
                ],
                "type": "object",
                "properties": {
                    "id": {
                        "description": "Server-managed unique ID for the version.\\n\\nGuaranteed to be unique within this schema only (different schemas will use\\nthe same version IDs).",
                        "minimum": 1,
                        "type": "integer",
                        "example": 1
                    },
                    "name": {
                        "description": "Client-provided description of a version.\\n\\nEnforced to be unique within this schema only (different schemas may use\\nthe same version names).",
                        "maxLength": 50,
                        "type": "string",
                        "example": "v1.0.0"
                    },
                    "date": {
                        "$ref": "#/components/schemas/SchemaDate"
                    },
                    "state": {
                        "$ref": "#/components/schemas/SchemaState"
                    },
                    "enabled": {
                        "description": "Set to false if the version of the schema is disabled.",
                        "type": "boolean"
                    }
                }
            },
            "SchemaState": {
                "description": "The state of a schema, or an individual version of a schema",
                "required": [
                    "state"
                ],
                "properties": {
                    "state": {
                        "description": "If the schema state is 'deprecated', all the schema version states are 'deprecated'.",
                        "enum": [
                            "deprecated",
                            "active"
                        ],
                        "type": "string"
                    },
                    "comment": {
                        "description": "User-provided string to explain why a schema is deprecated.\\nIgnored if the state is 'active'.\\nIf the schema state is 'deprecated', the schema version state comment will match\\nthe schema state comment, unless a specific state comment is set for the schema version.",
                        "maxLength": 300,
                        "type": "string",
                        "example": "We want to stop using integer IDs in future, so please use the newer\\nversions of the schema which have switched to string UUID ids."
                    }
                }
            },
            "SchemaSummary": {
                "description": "A high-level summary of the metadata for an overall schema, used in response\\npayloads.",
                "required": [
                    "id",
                    "name",
                    "state",
                    "enabled"
                ],
                "properties": {
                    "id": {
                        "description": "Lower-case URL-encoded version of the schema name.",
                        "type": "string"
                    },
                    "name": {
                        "$ref": "#/components/schemas/SchemaName"
                    },
                    "state": {
                        "$ref": "#/components/schemas/SchemaState"
                    },
                    "enabled": {
                        "description": "Set to false if the schema is disabled. If the schema is disabled, all\\nthe schema versions are disabled.",
                        "type": "boolean"
                    }
                }
            },
            "SchemaListItem": {
                "description": "Summary of the overall schema with the details about most recent version.\\n\\nIntended to be useful in responses with lists of schemas without needing to\\nmake additional requests for each individual schema.",
                "allOf": [
                    {
                        "$ref": "#/components/schemas/SchemaSummary"
                    },
                    {
                        "required": [
                            "latest"
                        ],
                        "type": "object",
                        "properties": {
                            "latest": {
                                "$ref": "#/components/schemas/SchemaVersion"
                            }
                        }
                    }
                ]
            },
            "SchemaInfo": {
                "description": "All metadata available for a single schema, including the metadata for every known version of the schema.\\nIt is a combination of SchemaSummary, and all of the schema versions metadata.",
                "allOf": [
                    {
                        "$ref": "#/components/schemas/SchemaSummary"
                    },
                    {
                        "required": [
                            "versions"
                        ],
                        "type": "object",
                        "properties": {
                            "versions": {
                                "type": "array",
                                "items": {
                                    "$ref": "#/components/schemas/SchemaVersion"
                                }
                            }
                        }
                    }
                ]
            },
            "Schema": {
                "description": "All of the data available for a single version of a single schema, including the Avro schema definition.",
                "required": [
                    "id",
                    "version",
                    "name",
                    "state",
                    "enabled",
                    "definition"
                ],
                "properties": {
                    "id": {
                        "description": "Lower-case URL-encoded version of the schema name.",
                        "type": "string"
                    },
                    "version": {
                        "$ref": "#/components/schemas/SchemaVersion"
                    },
                    "name": {
                        "$ref": "#/components/schemas/SchemaName"
                    },
                    "state": {
                        "$ref": "#/components/schemas/SchemaState"
                    },
                    "enabled": {
                        "description": "Set to false if the overall schema is disabled.",
                        "type": "boolean"
                    },
                    "definition": {
                        "$ref": "#/components/schemas/SchemaDefinition"
                    }
                }
            },
            "SchemaDefinition": {
                "description": "Avro schema definition",
                "type": "string",
                "example": "{\\n  \\"type\\": \\"Record\\",\\n  \\"name\\": \\"People\\",\\n  \\"fields\\": [{\\n    \\"name\\": \\"id\\",\\n    \\"type\\": \\"int\\"\\n  }, {\\n    \\"name\\": \\"name\\",\\n    \\"type\\": \\"string\\"\\n  }, {\\n    \\"name\\": \\"salary\\",\\n    \\"type\\": \\"int\\"\\n  }, {\\n    \\"name\\": \\"age\\",\\n    \\"type\\": \\"int\\"\\n  }]\\n}"
            },
            "NewSchemaVersion": {
                "description": "Request payload for uploading new version of an existing schema.",
                "required": [
                    "version",
                    "definition"
                ],
                "properties": {
                    "version": {
                        "description": "The name to give this version of the schema",
                        "type": "string",
                        "example": "v1.0.0"
                    },
                    "definition": {
                        "$ref": "#/components/schemas/SchemaDefinition"
                    },
                    "versionid": {
                        "description": "Requested unique ID for the version.\\n\\nMust be unique within this schema only (different schemas will use\\nthe same version IDs).",
                        "minimum": 1,
                        "type": "integer",
                        "example": 1
                    },
                    "versionstate": {
                        "$ref": "#/components/schemas/SchemaState"
                    },
                    "versionenabled": {
                        "description": "Set to false if the version of the schema is disabled.",
                        "type": "boolean"
                    }
                }
            },
            "NewSchema": {
                "description": "Request payload for uploading new schemas.\\nallOf:\\n  - $ref: '#/components/schemas/NewSchemaVersion'\\n  - type: object\\n    required:\\n      - name\\n    properties:\\n      name:\\n        $ref: '#/components/schemas/SchemaName'\\n      state:\\n        $ref: '#/components/schemas/SchemaState'\\n      enabled:\\n        type: boolean\\n        description: |-\\n          Set to false if the schema is disabled. If the schema is disabled, all\\n          the schema versions are disabled."
            },
            "VersionNumber": {
                "anyOf": [
                    {
                        "description": "Refers to a specific version",
                        "minimum": 1,
                        "type": "integer"
                    }
                ],
                "description": "Used when referring to a schema version in a request."
            },
            "SchemaModificationPatch": {
                "description": "A request payload for use in PATCH requests to modify a schema.",
                "type": "array",
                "items": {
                    "anyOf": [
                        {
                            "$ref": "#/components/schemas/StateModification"
                        },
                        {
                            "$ref": "#/components/schemas/EnabledModification"
                        }
                    ]
                }
            },
            "SchemaVersionModificationPatch": {
                "description": "A request payload for use in PATCH requests to modify an individual schema version.",
                "type": "array",
                "items": {
                    "anyOf": [
                        {
                            "$ref": "#/components/schemas/StateModification"
                        },
                        {
                            "$ref": "#/components/schemas/EnabledModification"
                        }
                    ]
                }
            },
            "StateModification": {
                "description": "A request to modify the state (enabled/deprecated) of a schema or schema version.",
                "required": [
                    "op",
                    "path",
                    "value"
                ],
                "type": "object",
                "properties": {
                    "op": {
                        "enum": [
                            "replace"
                        ],
                        "type": "string"
                    },
                    "path": {
                        "enum": [
                            "/state"
                        ],
                        "type": "string"
                    },
                    "value": {
                        "$ref": "#/components/schemas/SchemaState"
                    }
                }
            },
            "EnabledModification": {
                "description": "A request to modify the enabled flag for a schema or schema version.",
                "required": [
                    "op",
                    "path",
                    "value"
                ],
                "type": "object",
                "properties": {
                    "op": {
                        "enum": [
                            "replace"
                        ],
                        "type": "string"
                    },
                    "path": {
                        "enum": [
                            "/enabled"
                        ],
                        "type": "string"
                    },
                    "value": {
                        "type": "boolean"
                    }
                }
            },
            "Error": {
                "description": "Response payload for returning an error",
                "required": [
                    "message"
                ],
                "type": "object",
                "properties": {
                    "error_code": {
                        "description": "The HTTP status code of the response.\\nUseful when the client is using a framework that doesn't allow direct access to the HTTP response.",
                        "type": "number"
                    },
                    "message": {
                        "type": "string"
                    }
                }
            }
        },
        "responses": {
            "Ok": {
                "content": {
                    "application/json": {
                        "schema": {
                            "required": [
                                "ok"
                            ],
                            "type": "object",
                            "properties": {
                                "ok": {
                                    "description": "This will only ever be true. If the server is not healthy, an Error will be returned instead.",
                                    "type": "boolean"
                                }
                            }
                        }
                    }
                },
                "description": "Healthy"
            },
            "NotFound": {
                "content": {
                    "application/json": {
                        "schema": {
                            "$ref": "#/components/schemas/Error"
                        }
                    }
                },
                "description": "Resource not found"
            },
            "InvalidSchema": {
                "content": {
                    "application/json": {
                        "schema": {
                            "$ref": "#/components/schemas/Error"
                        }
                    }
                },
                "description": "Invalid schema definition or request"
            },
            "UnauthorizedError": {
                "content": {
                    "application/json": {
                        "schema": {
                            "$ref": "#/components/schemas/Error"
                        }
                    }
                },
                "description": "API key missing or invalid"
            },
            "IdenticalSchemaError": {
                "content": {
                    "application/json": {
                        "schema": {
                            "$ref": "#/components/schemas/Error"
                        }
                    }
                },
                "description": "This is returned when a client request is attempting to provide a new version of an existing schema.\\nIt will be returned when either the schema definition is identical to current latest version, or the provided version name is already in use by an existing version of this schema."
            },
            "SchemaVersionIDConflictError": {
                "content": {
                    "application/json": {
                        "schema": {
                            "$ref": "#/components/schemas/Error"
                        }
                    }
                },
                "description": "This is returned when a client request is attempting to provide a new version of a schema.\\nIt will be returned when the provided version ID is already in use by an existing version of this schema."
            }
        },
        "securitySchemes": {
            "ApiKeyAuth": {
                "type": "apiKey",
                "name": "X-Auth-Token",
                "in": "header"
            }
        }
    },
    "security": [
        {
            "ApiKeyAuth": []
        }
    ]
}
        `;
        const oaiContent: any = JSON.parse(oaiString);
        setTimeout( () => {
            this.setMultiState({
                artifactContent: oaiContent,
                isLoading: false
            });
        }, 500);
    }

}
