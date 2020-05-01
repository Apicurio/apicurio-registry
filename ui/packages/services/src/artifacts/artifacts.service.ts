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

import {Artifact, ArtifactMetaData, Rule, VersionMetaData} from "@apicurio/registry-models";
import {LoggerService} from "../logger";

const vmd = (id: string, name: string, description: string, type: string, version: number, createdBy: string, createdOn: Date, globalId: number, state: string): VersionMetaData => {
    const rval: VersionMetaData = new VersionMetaData();
    rval.id = id;
    rval.name = name;
    rval.description = description;
    rval.type = type;
    rval.version = version;
    rval.createdBy = createdBy;
    rval.createdOn = createdOn;
    rval.globalId = globalId;
    rval.state = state;
    return rval;
};

export interface CreateArtifactData {
    type: string;
    name: string|null;
    description: string|null;
    content: string;
}

export interface CreateVersionData {
    type: string;
    content: string;
}

export interface GetArtifactsCriteria {
    type: string;
    value: string;
    sortAscending: boolean;
}

export interface Paging {
    page: number;
    pageSize: number;
}

export interface ArtifactsSearchResults {
    artifacts: Artifact[];
    count: number;
    firstPage: boolean;
    lastPage: boolean;
    page: number;
    pageSize: number;
}

/**
 * The artifacts service.  Used to query the backend search API to fetch lists of
 * artifacts and also details about individual artifacts.
 */
export class ArtifactsService {

    private logger: LoggerService = null;

    private readonly allArtifacts: Artifact[];
    private readonly artifactContent: any;

    constructor() {
        const artifacts: Artifact[] = [
            Artifact.create("1", "OPENAPI", "Biotech API", "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", [ "books", "library", "authors" ]),
            Artifact.create("2", "ASYNCAPI", "Street Light API", "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.", [ "async", "led", "electric", "utility"] ),
            Artifact.create("3", "AVRO", "Invoice Type", "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur."),
            Artifact.create("4", "AVRO", "Service Record Type", "Sint noster pro ei. Iudico ancillae repudiandae mel ad, errem signiferumque ad est. ", [ "schema", "utility"]),
            Artifact.create("5", "OPENAPI", "GitHub RESTful API", "Web-based Git repository hosting service", [ "public", "github" ]),
            Artifact.create("6", "GRAPHQL", "GitHub GraphQL API", "Web-based Git repository hosting service", [ "public", "github", "async" ]),
            Artifact.create("7", "GRAPHQL", "Yelp API", "User Reviews and Recommendations of Top Restaurants, Shopping, Nightlife, Entertainment, Services and More"),
        ];
        this.allArtifacts = artifacts;
        this.artifactContent = {};
        this.addMockContent();
    }

    public createArtifact(data: CreateArtifactData): Promise<ArtifactMetaData> {
        if (!data.name) {
            data.name = "New Artifact";
        }
        if (!data.description) {
            data.description = "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
        }
        if (!data.type) {
            data.type = "AVRO";
        }

        const artifactId: string = "" + (this.allArtifacts.length + 1);
        const newArtifact: Artifact = Artifact.create(artifactId, data.type, data.name, data.description, []);
        this.allArtifacts.push(newArtifact);
        this.artifactContent[artifactId] = data.content;

        const metaData: ArtifactMetaData = new ArtifactMetaData();
        metaData.id = artifactId;
        metaData.createdOn = new Date();
        metaData.createdBy = "user";
        metaData.name = data.name;
        metaData.description = data.description;
        metaData.globalId = 1;
        metaData.state = "ENABLED";
        metaData.type = data.type;
        metaData.version = 1;

        return new Promise<ArtifactMetaData>(resolve => {
            setTimeout(() => {
                resolve(metaData);
            }, 500);
        });
    }

    public createArtifactVersion(artifactId: string, data: CreateVersionData): Promise<VersionMetaData> {
        const rval: VersionMetaData = vmd(artifactId, "", "", data.type, 110, "user", new Date(), 12345, "ENABLED");
        return new Promise<VersionMetaData>(resolve => {
            setTimeout(() => {
                resolve(rval);
            }, 500);
        });
    }

    public getArtifacts(criteria: GetArtifactsCriteria, paging: Paging): Promise<ArtifactsSearchResults> {
        this.logger.debug("[ArtifactsService] Getting artifacts: ", criteria, paging);
        let artifacts: Artifact[] = [...this.allArtifacts];
        artifacts = artifacts.sort( (a1: Artifact, a2: Artifact) => {
            if (criteria.sortAscending) {
                return a1.name.localeCompare(a2.name);
            } else {
                return a2.name.localeCompare(a1.name);
            }
        });
        artifacts = artifacts.filter( artifact => {
            if (criteria.value) {
                return artifact.name.toLowerCase().indexOf(criteria.value.toLowerCase()) !== -1 ||
                    artifact.description.toLowerCase().indexOf(criteria.value.toLowerCase()) !== -1 ||
                    artifact.labels.indexOf(criteria.value) !== -1;
            } else {
                return true;
            }
        });
        const artifactCount: number = artifacts.length;
        const start: number = (paging.page - 1) * paging.pageSize;
        const end: number = start + paging.pageSize;
        artifacts = artifacts.slice(start, end);

        return new Promise<ArtifactsSearchResults>(resolve => {
            setTimeout(() => {
                const results: ArtifactsSearchResults = {
                    artifacts,
                    count: artifactCount,
                    firstPage: true,
                    lastPage: false,
                    page: paging.page,
                    pageSize: paging.pageSize
                };
                resolve(results);
            }, 200);
        });
    }

    public getArtifactMetaData(artifactId: string, version: string): Promise<ArtifactMetaData> {
        return new Promise<ArtifactMetaData>( resolve => {
            setTimeout(() => {
                const rval: ArtifactMetaData = this.allArtifacts.filter(artifact => artifact.id === artifactId).map(artifact => {
                    const md: ArtifactMetaData = new ArtifactMetaData();
                    md.id = artifact.id;
                    md.state = "ENABLED";
                    md.description = artifact.description;
                    md.name = artifact.name;
                    md.version = 1;
                    md.createdOn = artifact.createdOn;
                    md.createdBy = artifact.createdBy;
                    md.modifiedOn = artifact.createdOn
                    md.modifiedBy = artifact.createdBy;
                    md.globalId = 1245;
                    md.type = artifact.type;
                    return md;
                })[0];
                resolve(rval);
            }, 200);
        });
    }

    public getArtifactContent(artifactId: string, version: string): Promise<string> {
        return new Promise<string>( resolve => {
            setTimeout(() => {
                const content = this.artifactContent[artifactId];
                resolve(content);
            }, 200);
        });
    }

    public getArtifactVersions(artifactId: string): Promise<VersionMetaData[]> {
        return new Promise<VersionMetaData[]>( resolve => {
            setTimeout(() => {
                resolve([
                    vmd(artifactId, "Name of Version 1", "Description of version 1", "OPENAPI", 1, "user", new Date(), 101, "ENABLED"),
                    vmd(artifactId, "Name of Version 2", "Description of version 2", "OPENAPI", 2, "user", new Date(), 102, "ENABLED"),
                    vmd(artifactId, "Name of Version 3", "Description of version 3", "OPENAPI", 3, "user", new Date(), 103, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 4, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 5", "Description of version 4", "OPENAPI", 5, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 6, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 7, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 8, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 9, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 10, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 11, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 12, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 13, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 14, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 15, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 16, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 17, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 18, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 19, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 20, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 21, "user", new Date(), 104, "ENABLED"),
                    vmd(artifactId, "Name of Version 4", "Description of version 4", "OPENAPI", 22, "user", new Date(), 104, "ENABLED"),
                ]);
            }, 200);
        });
    }

    public getArtifactRules(artifactId: string): Promise<Rule[]> {
        this.logger.info("[ArtifactsService] Getting the list of rules for artifact: ", artifactId);
        return new Promise<Rule[]>(resolve => {
            setTimeout(() => {
                resolve([]);
            }, 200);
        });
    }

    public updateArtifactRule(artiactId: string, type: string, config: string|null): Promise<Rule|null> {
        this.logger.info("[ArtifactsService] Updating rule:", type);
        return new Promise<Rule>(resolve => {
            setTimeout(() => {
                resolve(null);
            }, 200);
        });
    }

    private addMockContent(): void {
        this.artifactContent["1"] = `{
    "openapi": "3.0.2",
    "info": {
        "title": "Library API",
        "version": "1.0.0",
        "description": "A simple API for managing authors and books.",
        "contact": {
            "name": "Eric Wittmann",
            "email": "eric.wittmann@redhat.com"
        },
        "license": {
            "name": "Mozilla 2.0",
            "url": "https://www.mozilla.org/en-US/MPL/2.0/"
        }
    },
    "paths": {
        "/authors": {
            "summary": "Path used to manage the list of authors.",
            "description": "The REST endpoint/path used to list and create zero or more \`Author\` entities.  This path contains a \`GET\` and \`POST\` operation to perform the list and create tasks, respectively.",
            "get": {
                "responses": {
                    "200": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "array",
                                    "items": {
                                        "$ref": "#/components/schemas/Author"
                                    }
                                }
                            }
                        },
                        "description": "Successful response - returns an array of \`Author\` entities."
                    }
                },
                "operationId": "getauthors",
                "summary": "List All authors",
                "description": "Gets a list of all \`Author\` entities."
            },
            "post": {
                "requestBody": {
                    "description": "A new \`Author\` to be created.",
                    "content": {
                        "application/json": {
                            "schema": {
                                "$ref": "#/components/schemas/Author"
                            }
                        }
                    },
                    "required": true
                },
                "responses": {
                    "201": {
                        "description": "Successful response."
                    }
                },
                "operationId": "createAuthor",
                "summary": "Create a Author",
                "description": "Creates a new instance of a \`Author\`."
            }
        },
        "/authors/{authorId}": {
            "summary": "Path used to manage a single Author.",
            "description": "The REST endpoint/path used to get, update, and delete single instances of an \`Author\`.  This path contains \`GET\`, \`PUT\`, and \`DELETE\` operations used to perform the get, update, and delete tasks, respectively.",
            "get": {
                "tags": [],
                "responses": {
                    "200": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/Author"
                                }
                            }
                        },
                        "description": "Successful response - returns a single \`Author\`."
                    },
                    "404": {
                        "$ref": "#/components/responses/NotFound"
                    }
                },
                "operationId": "getAuthor",
                "summary": "Get a Author",
                "description": "Gets the details of a single instance of a \`Author\`."
            },
            "put": {
                "requestBody": {
                    "description": "Updated \`Author\` information.",
                    "content": {
                        "application/json": {
                            "schema": {
                                "$ref": "#/components/schemas/Author"
                            }
                        }
                    },
                    "required": true
                },
                "responses": {
                    "202": {
                        "description": "Successful response."
                    },
                    "404": {
                        "$ref": "#/components/responses/NotFound"
                    }
                },
                "operationId": "updateAuthor",
                "summary": "Update a Author",
                "description": "Updates an existing \`Author\`."
            },
            "delete": {
                "responses": {
                    "204": {
                        "description": "Successful response."
                    },
                    "404": {
                        "$ref": "#/components/responses/NotFound"
                    }
                },
                "operationId": "deleteAuthor",
                "summary": "Delete a Author",
                "description": "Deletes an existing \`Author\`."
            },
            "parameters": [
                {
                    "name": "authorId",
                    "description": "A unique identifier for a \`Author\`.",
                    "schema": {
                        "type": "string"
                    },
                    "in": "path",
                    "required": true
                }
            ]
        },
        "/books": {
            "summary": "Path used to manage the list of books.",
            "description": "The REST endpoint/path used to list and create zero or more \`Book\` entities.  This path contains a \`GET\` and \`POST\` operation to perform the list and create tasks, respectively.",
            "get": {
                "responses": {
                    "200": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "array",
                                    "items": {
                                        "$ref": "#/components/schemas/Book"
                                    }
                                }
                            }
                        },
                        "description": "Successful response - returns an array of \`Book\` entities."
                    }
                },
                "operationId": "getbooks",
                "summary": "List All books",
                "description": "Gets a list of all \`Book\` entities."
            },
            "post": {
                "requestBody": {
                    "description": "A new \`Book\` to be created.",
                    "content": {
                        "application/json": {
                            "schema": {
                                "$ref": "#/components/schemas/Book"
                            }
                        }
                    },
                    "required": true
                },
                "responses": {
                    "201": {
                        "description": "Successful response."
                    }
                },
                "operationId": "createBook",
                "summary": "Create a Book",
                "description": "Creates a new instance of a \`Book\`."
            }
        },
        "/books/{bookId}": {
            "summary": "Path used to manage a single Book.",
            "description": "The REST endpoint/path used to get, update, and delete single instances of an \`Book\`.  This path contains \`GET\`, \`PUT\`, and \`DELETE\` operations used to perform the get, update, and delete tasks, respectively.",
            "get": {
                "responses": {
                    "200": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/Book"
                                }
                            }
                        },
                        "description": "Successful response - returns a single \`Book\`."
                    }
                },
                "operationId": "getBook",
                "summary": "Get a Book",
                "description": "Gets the details of a single instance of a \`Book\`."
            },
            "put": {
                "requestBody": {
                    "description": "Updated \`Book\` information.",
                    "content": {
                        "application/json": {
                            "schema": {
                                "$ref": "#/components/schemas/Book"
                            }
                        }
                    },
                    "required": true
                },
                "responses": {
                    "202": {
                        "description": "Successful response."
                    }
                },
                "operationId": "updateBook",
                "summary": "Update a Book",
                "description": "Updates an existing \`Book\`."
            },
            "delete": {
                "responses": {
                    "204": {
                        "description": "Successful response."
                    }
                },
                "operationId": "deleteBook",
                "summary": "Delete a Book",
                "description": "Deletes an existing \`Book\`."
            },
            "parameters": [
                {
                    "name": "bookId",
                    "description": "A unique identifier for a \`Book\`.",
                    "schema": {
                        "type": "string"
                    },
                    "in": "path",
                    "required": true
                }
            ]
        },
        "/widgets": {
            "summary": "Path used to manage the list of widgets.",
            "description": "The REST endpoint/path used to list and create zero or more \`Widget\` entities.  This path contains a \`GET\` and \`POST\` operation to perform the list and create tasks, respectively.",
            "get": {
                "responses": {
                    "200": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "type": "array",
                                    "items": {
                                        "$ref": "#/components/schemas/Widget"
                                    }
                                }
                            }
                        },
                        "description": "Successful response - returns an array of \`Widget\` entities."
                    }
                },
                "operationId": "getwidgets",
                "summary": "List All widgets",
                "description": "Gets a list of all \`Widget\` entities."
            },
            "post": {
                "requestBody": {
                    "description": "A new \`Widget\` to be created.",
                    "content": {
                        "application/json": {
                            "schema": {
                                "$ref": "#/components/schemas/Widget"
                            }
                        }
                    },
                    "required": true
                },
                "responses": {
                    "201": {
                        "description": "Successful response."
                    }
                },
                "operationId": "createWidget",
                "summary": "Create a Widget",
                "description": "Creates a new instance of a \`Widget\`."
            },
            "delete": {
                "responses": {
                    "204": {}
                },
                "operationId": "deleteAllWidgets",
                "summary": "deleteAllWidgets"
            }
        },
        "/widgets/{widgetId}": {
            "summary": "Path used to manage a single Widget.",
            "description": "The REST endpoint/path used to get, update, and delete single instances of an \`Widget\`.  This path contains \`GET\`, \`PUT\`, and \`DELETE\` operations used to perform the get, update, and delete tasks, respectively.",
            "get": {
                "responses": {
                    "200": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/Widget"
                                }
                            }
                        },
                        "description": "Successful response - returns a single \`Widget\`."
                    }
                },
                "operationId": "getWidget",
                "summary": "Get a Widget",
                "description": "Gets the details of a single instance of a \`Widget\`."
            },
            "put": {
                "requestBody": {
                    "description": "Updated \`Widget\` information.",
                    "content": {
                        "application/json": {
                            "schema": {
                                "$ref": "#/components/schemas/Widget"
                            }
                        }
                    },
                    "required": true
                },
                "responses": {
                    "202": {
                        "description": "Successful response."
                    }
                },
                "operationId": "updateWidget",
                "summary": "Update a Widget",
                "description": "Updates an existing \`Widget\`."
            },
            "delete": {
                "responses": {
                    "204": {
                        "description": "Successful response."
                    }
                },
                "operationId": "deleteWidget",
                "summary": "Delete a Widget",
                "description": "Deletes an existing \`Widget\`."
            },
            "parameters": [
                {
                    "name": "widgetId",
                    "description": "A unique identifier for a \`Widget\`.",
                    "schema": {
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
            "Author": {
                "title": "Root Type for Author",
                "description": "The author of a book.",
                "type": "object",
                "properties": {
                    "id": {
                        "type": "string"
                    },
                    "name": {
                        "type": "string"
                    },
                    "dob": {
                        "format": "date",
                        "type": "string"
                    }
                },
                "example": {
                    "id": "jk-rowling",
                    "name": "JK Rowling",
                    "dob": "1968-01-01"
                }
            },
            "Book": {
                "title": "Root Type for Book",
                "description": "Information about a book.",
                "type": "object",
                "properties": {
                    "ddsn": {
                        "type": "string"
                    },
                    "title": {
                        "type": "string"
                    },
                    "author": {
                        "$ref": "#/components/schemas/Author"
                    },
                    "publish-date": {
                        "format": "date",
                        "type": "string"
                    }
                },
                "example": {
                    "ddsn": "632.4",
                    "title": "SQL For Dummies",
                    "publish-date": "2001-05-13"
                }
            },
            "Widget": {
                "description": "",
                "type": "object"
            }
        },
        "responses": {
            "NotFound": {
                "content": {
                    "application/json": {
                        "schema": {
                            "type": "string"
                        }
                    }
                },
                "description": "Generic response when not found."
            }
        }
    }
}`;
        this.artifactContent["2"] = `{
  "asyncapi": "1.0.0",
  "info": {
    "title": "Streetlights API",
    "version": "0.1.0"
  },
  "host": "test.mosquitto.org",
  "baseTopic": "smartylighting.streetlights.1.0",
  "schemes": [
    "mqtt"
  ],
  "topics": {
    "action.{streetlight_id}.turn.on": {
      "subscribe": {
        "$ref": "#/components/messages/turnOnOff"
      }
    },
    "action.{streetlight_id}.turn.off": {
      "subscribe": {
        "$ref": "#/components/messages/turnOnOff"
      }
    },
    "event.{streetlight_id}.lighting.measured": {
      "publish": {
        "$ref": "#/components/messages/lightMeasured"
      }
    }
  },
  "components": {
    "messages": {
      "lightMeasured": {
        "summary": "A message to inform about environmental lighting conditions.",
        "payload": {
          "$ref": "#/components/schemas/lightMeasuredPayload"
        }
      },
      "turnOnOff": {
        "summary": "A message indicating us if we should turn the light on or off.",
        "payload": {
          "$ref": "#/components/schemas/turnOnOffPayload"
        }
      }
    },
    "schemas": {
      "lightMeasuredPayload": {
        "type": "object",
        "properties": {
          "lumens": {
            "type": "number",
            "minimum": 0,
            "description": "Light intensity measured in lumens."
          },
          "sent_at": {
            "type": "string",
            "format": "date-time"
          }
        }
      },
      "turnOnOffPayload": {
        "type": "object",
        "properties": {
          "command": {
            "type": "string",
            "enum": [
              "on",
              "off"
            ],
            "description": "Whether to turn on or off the light."
          },
          "sent_at": {
            "type": "string",
            "format": "date-time"
          }
        }
      }
    }
  }
}`;
        this.artifactContent["3"] = `{
  "type": "record",
  "name": "User",
  "namespace": "com.example.avro",
  "doc": "This is a user record in a fictitious to-do-list management app. It supports arbitrary grouping and nesting of items, and allows you to add items by email or by tweeting.\\n\\nNote this app doesn't actually exist. The schema is just a demo for [Avrodoc](https://github.com/ept/avrodoc)!",
  "fields": [
    {
      "name": "id",
      "doc": "System-assigned numeric user ID. Cannot be changed by the user.",
      "type": "int"
    },
    {
      "name": "username",
      "doc": "The username chosen by the user. Can be changed by the user.",
      "type": "string"
    },
    {
      "name": "passwordHash",
      "doc": "The user's password, hashed using [scrypt](http://www.tarsnap.com/scrypt.html).",
      "type": "string"
    },
    {
      "name": "signupDate",
      "doc": "Timestamp (milliseconds since epoch) when the user signed up",
      "type": "long"
    },
    {
      "name": "emailAddresses",
      "doc": "All email addresses on the user's account",
      "type": {
        "type": "array",
        "items": {
          "type": "record",
          "name": "EmailAddress",
          "doc": "Stores details about an email address that a user has associated with their account.",
          "fields": [
            {
              "name": "address",
              "doc": "The email address, e.g. \`foo@example.com\`",
              "type": "string"
            },
            {
              "name": "verified",
              "doc": "true if the user has clicked the link in a confirmation email to this address.",
              "type": "boolean",
              "default": false
            },
            {
              "name": "dateAdded",
              "doc": "Timestamp (milliseconds since epoch) when the email address was added to the account.",
              "type": "long"
            },
            {
              "name": "dateBounced",
              "doc": "Timestamp (milliseconds since epoch) when an email sent to this address last bounced. Reset to null when the address no longer bounces.",
              "type": ["null", "long"]
            }
          ]
        }
      }
    },
    {
      "name": "twitterAccounts",
      "doc": "All Twitter accounts that the user has OAuthed",
      "type": {
        "type": "array",
        "items": {
          "type": "record",
          "name": "TwitterAccount",
          "doc": "Stores access credentials for one Twitter account, as granted to us by the user by OAuth.",
          "fields": [
            {
              "name": "status",
              "doc": "Indicator of whether this authorization is currently active, or has been revoked",
              "type": {
                "type": "enum",
                "name": "OAuthStatus",
                "doc": "* \`PENDING\`: the user has started authorizing, but not yet finished\\n* \`ACTIVE\`: the token should work\\n* \`DENIED\`: the user declined the authorization\\n* \`EXPIRED\`: the token used to work, but now it doesn't\\n* \`REVOKED\`: the user has explicitly revoked the token",
                "symbols": ["PENDING", "ACTIVE", "DENIED", "EXPIRED", "REVOKED"]
              }
            },
            {
              "name": "userId",
              "doc": "Twitter's numeric ID for this user",
              "type": "long"
            },
            {
              "name": "screenName",
              "doc": "The twitter username for this account (can be changed by the user)",
              "type": "string"
            },
            {
              "name": "oauthToken",
              "doc": "The OAuth token for this Twitter account",
              "type": "string"
            },
            {
              "name": "oauthTokenSecret",
              "doc": "The OAuth secret, used for signing requests on behalf of this Twitter account. \`null\` whilst the OAuth flow is not yet complete.",
              "type": ["null", "string"]
            },
            {
              "name": "dateAuthorized",
              "doc": "Timestamp (milliseconds since epoch) when the user last authorized this Twitter account",
              "type": "long"
            }
          ]
        }
      }
    },
    {
      "name": "toDoItems",
      "doc": "The top-level items in the user's to-do list",
      "type": {
        "type": "array",
        "items": {
          "type": "record",
          "name": "ToDoItem",
          "doc": "A record is one node in a To-Do item tree (every record can contain nested sub-records).",
          "fields": [
            {
              "name": "status",
              "doc": "User-selected state for this item (e.g. whether or not it is marked as done)",
              "type": {
                "type": "enum",
                "name": "ToDoStatus",
                "doc": "* \`HIDDEN\`: not currently visible, e.g. because it becomes actionable in future\\n* \`ACTIONABLE\`: appears in the current to-do list\\n* \`DONE\`: marked as done, but still appears in the list\\n* \`ARCHIVED\`: marked as done and no longer visible\\n* \`DELETED\`: not done and removed from list (preserved for undo purposes)",
                "symbols": ["HIDDEN", "ACTIONABLE", "DONE", "ARCHIVED", "DELETED"]
              }
            },
            {
              "name": "title",
              "doc": "One-line summary of the item",
              "type": "string"
            },
            {
              "name": "description",
              "doc": "Detailed description (may contain HTML markup)",
              "type": ["null", "string"]
            },
            {
              "name": "snoozeDate",
              "doc": "Timestamp (milliseconds since epoch) at which the item should go from \`HIDDEN\` to \`ACTIONABLE\` status",
              "type": ["null", "long"]
            },
            {
              "name": "subItems",
              "doc": "List of children of this to-do tree node",
              "type": {
                "type": "array",
                "items": "ToDoItem"
              }
            }
          ]
        }
      }
    }
  ]
}`;
    }

}
