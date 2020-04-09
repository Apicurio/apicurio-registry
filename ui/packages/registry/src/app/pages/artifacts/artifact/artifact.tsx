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
import {Flex, FlexItem, PageSection, PageSectionVariants, Spinner, Tab, Tabs} from '@patternfly/react-core';
import "./artifact.css";
import {PageComponent, PageProps, PageState} from "../../basePage";
import {ArtifactPageHeader} from "./components/pageheader";
import {ArtifactMetaData} from "@apicurio/registry-models";
import {ApiDocumentationTabContent, ContentTabContent, InfoTabContent, VersionsTabContent} from "./components/tabs";
import {Services} from "@apicurio/registry-services";


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
    activeTabKey: number;
    artifact: ArtifactMetaData | null;
    artifactContent: string;
    artifactIsText: boolean;
}

/**
 * The artifacts page.
 */
export class ArtifactPage extends PageComponent<ArtifactPageProps, ArtifactPageState> {

    constructor(props: Readonly<ArtifactPageProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        const artifact: ArtifactMetaData = this.state.artifact ? this.state.artifact : new ArtifactMetaData();
        const tabs: React.ReactNode[] = [
            <Tab eventKey={0} title="Artifact Info" key="info" tabContentId="tab-info">
                <InfoTabContent artifact={artifact} />
            </Tab>,
            <Tab eventKey={1} title="Content" key="content">
                <ContentTabContent artifactContent={this.state.artifactContent} />
            </Tab>,
            <Tab eventKey={2} title="API Documentation" key="api-documentation">
                <ApiDocumentationTabContent artifactContent={this.state.artifactContent} />
            </Tab>,
            <Tab eventKey={3} title="Versions" key="versions">
                <VersionsTabContent />
            </Tab>
        ];
        if (!this.isOpenApi()) {
            tabs.splice(2, 1);
        }

        return (
            <React.Fragment>
                <PageSection className="ps_artifacts-header" variant={PageSectionVariants.light}>
                    <ArtifactPageHeader onUploadVersion={console.info} />
                </PageSection>
                {
                    this.state.isLoading ?
                <PageSection variant={PageSectionVariants.default} isFilled={true}>
                    <Flex>
                        <FlexItem><Spinner size="lg"/></FlexItem>
                        <FlexItem><span>Loading, please wait...</span></FlexItem>
                    </Flex>
                </PageSection>
                    :
                <PageSection variant={PageSectionVariants.default} isFilled={true} noPadding={true} className="artifact-details-main">
                    <Tabs className="artifact-page-tabs"
                          unmountOnExit={true}
                          isFilled={true}
                          activeKey={this.state.activeTabKey}
                          children={tabs}
                          onSelect={this.handleTabClick}
                    />
                </PageSection>
                }
            </React.Fragment>
        );
    }

    protected initializeState(): ArtifactPageState {
        return {
            activeTabKey: 0,
            artifact: null,
            artifactContent: "",
            artifactIsText: true,
            isLoading: true
        };
    }

    protected loadPageData(): void {
        const artifact: ArtifactMetaData = new ArtifactMetaData();
        artifact.id = "1";
        artifact.name = "Example API";
        artifact.description = "Ut vel molestie quam, ut tristique ante. Nullam fermentum risus at blandit vestibulum. Quisque non consectetur elit. Vivamus vitae ultricies dui. Nulla sollicitudin sodales cursus.";
        artifact.type = "OPENAPI";
        artifact.version = 1;
        artifact.createdBy = "user";
        artifact.createdOn = new Date();
        artifact.modifiedBy = "user";
        artifact.modifiedOn = new Date();
        artifact.globalId = 14298431927;
        artifact.state = "ENABLED";
        setTimeout( () => {
            this.setMultiState({
                artifact,
                artifactContent: SAMPLE_CONTENT,
                isLoading: false
            });
        }, 500);
    }

    private handleTabClick = (event: any, tabIndex: any): void => {
        this.setSingleState("activeTabKey", tabIndex);
    };

    private getArtifactContent(): string {
        if (this.state.artifactContent != null) {
            return JSON.stringify(this.state.artifactContent, null, 4);
        } else {
            return "";
        }
    }

    private isOpenApi(): boolean {
        if (this.state.artifact) {
            return this.state.artifact.type === "OPENAPI";
        } else {
            return false;
        }
    }
}


const SAMPLE_CONTENT: string = `{
    "openapi": "3.0.2",
    "info": {
        "title": "Collaboration API",
        "version": "1.0.0",
        "description": "A sample API that uses a petstore as an example to demonstrate features in the OpenAPI 3.0 specification",
        "termsOfService": "http://swagger.io/terms/",
        "contact": {
            "name": "Swagger API Team",
            "url": "http://swagger.io",
            "email": "apiteam@swagger.io"
        },
        "license": {
            "name": "Apache 2.0",
            "url": "https://www.apache.org/licenses/LICENSE-2.0.html"
        }
    },
    "servers": [
        {
            "url": "http://petstore.swagger.io/api"
        }
    ],
    "paths": {
        "/pets": {
            "get": {
                "parameters": [
                    {
                        "style": "form",
                        "name": "tags",
                        "description": "tags to filter by",
                        "schema": {
                            "type": "array",
                            "items": {
                                "type": "string"
                            }
                        },
                        "in": "query",
                        "required": false
                    },
                    {
                        "name": "limit",
                        "description": "maximum number of results to return",
                        "schema": {
                            "format": "int32",
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
                                        "$ref": "#/components/schemas/Pet"
                                    }
                                }
                            }
                        },
                        "description": "pet response"
                    },
                    "default": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/Error"
                                }
                            }
                        },
                        "description": "unexpected error"
                    }
                },
                "operationId": "findPets",
                "description": "Returns all pets from the system that the user has access to\\nNam sed condimentum est. Maecenas tempor sagittis sapien, nec rhoncus sem sagittis sit amet. Aenean at gravida augue, ac iaculis sem. Curabitur odio lorem, ornare eget elementum nec, cursus id lectus. Duis mi turpis, pulvinar ac eros ac, tincidunt varius justo. In hac habitasse platea dictumst. Integer at adipiscing ante, a sagittis ligula. Aenean pharetra tempor ante molestie imperdiet. Vivamus id aliquam diam. Cras quis velit non tortor eleifend sagittis. Praesent at enim pharetra urna volutpat venenatis eget eget mauris. In eleifend fermentum facilisis. Praesent enim enim, gravida ac sodales sed, placerat id erat. Suspendisse lacus dolor, consectetur non augue vel, vehicula interdum libero. Morbi euismod sagittis libero sed lacinia.\\n\\nSed tempus felis lobortis leo pulvinar rutrum. Nam mattis velit nisl, eu condimentum ligula luctus nec. Phasellus semper velit eget aliquet faucibus. In a mattis elit. Phasellus vel urna viverra, condimentum lorem id, rhoncus nibh. Ut pellentesque posuere elementum. Sed a varius odio. Morbi rhoncus ligula libero, vel eleifend nunc tristique vitae. Fusce et sem dui. Aenean nec scelerisque tortor. Fusce malesuada accumsan magna vel tempus. Quisque mollis felis eu dolor tristique, sit amet auctor felis gravida. Sed libero lorem, molestie sed nisl in, accumsan tempor nisi. Fusce sollicitudin massa ut lacinia mattis. Sed vel eleifend lorem. Pellentesque vitae felis pretium, pulvinar elit eu, euismod sapien.\\n"
            },
            "post": {
                "requestBody": {
                    "description": "Pet to add to the store",
                    "content": {
                        "application/json": {
                            "schema": {
                                "$ref": "#/components/schemas/NewPet"
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
                                    "$ref": "#/components/schemas/Pet"
                                }
                            }
                        },
                        "description": "pet response"
                    },
                    "default": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/Error"
                                }
                            }
                        },
                        "description": "unexpected error"
                    }
                },
                "operationId": "addPet",
                "description": "Creates a new pet in the store.  Duplicates are allowed"
            }
        },
        "/pets/{id}": {
            "get": {
                "parameters": [
                    {
                        "name": "id",
                        "description": "ID of pet to fetch",
                        "schema": {
                            "format": "int64",
                            "type": "integer"
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
                                    "$ref": "#/components/schemas/Pet"
                                }
                            }
                        },
                        "description": "pet response"
                    },
                    "default": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/Error"
                                }
                            }
                        },
                        "description": "unexpected error"
                    }
                },
                "operationId": "find pet by id",
                "description": "Returns a user based on a single ID, if the user does not have access to the pet"
            },
            "delete": {
                "parameters": [
                    {
                        "name": "id",
                        "description": "ID of pet to delete",
                        "schema": {
                            "format": "int64",
                            "type": "integer"
                        },
                        "in": "path",
                        "required": true
                    }
                ],
                "responses": {
                    "204": {
                        "description": "pet deleted"
                    },
                    "default": {
                        "content": {
                            "application/json": {
                                "schema": {
                                    "$ref": "#/components/schemas/Error"
                                }
                            }
                        },
                        "description": "unexpected error"
                    }
                },
                "operationId": "deletePet",
                "description": "deletes a single pet based on the ID supplied"
            }
        }
    },
    "components": {
        "schemas": {
            "Pet": {
                "allOf": [
                    {
                        "$ref": "#/components/schemas/NewPet"
                    },
                    {
                        "required": [
                            "id"
                        ],
                        "properties": {
                            "id": {
                                "format": "int64",
                                "type": "integer"
                            }
                        }
                    }
                ]
            },
            "NewPet": {
                "required": [
                    "name"
                ],
                "properties": {
                    "name": {
                        "type": "string"
                    },
                    "tag": {
                        "type": "string"
                    }
                }
            },
            "Error": {
                "required": [
                    "code",
                    "message"
                ],
                "properties": {
                    "code": {
                        "format": "int32",
                        "type": "integer"
                    },
                    "message": {
                        "type": "string"
                    }
                }
            }
        }
    }
}
`;