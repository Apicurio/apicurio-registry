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

import { Services } from "src/services";

// tslint:disable-next-line:interface-name
export class ArtifactTypes {

    public static async allTypes(): Promise<string[]> {
        return (await Services.getAdminService().getArtifactTypes()).map(t => t.name);
    }

    public static async allTypesWithLabels(): Promise<any[]> {
        return (await this.allTypes()).map(t => { return { id: t, label: this.getLabel(t) }; });
    }

    public static getTitle(type: string): string {
        let title: string = type;
        switch (type) {
            case "AVRO":
                title = "Avro Schema";
                break;
            case "PROTOBUF":
                title = "Protobuf Schema";
                break;
            case "JSON":
                title = "JSON Schema";
                break;
            case "OPENAPI":
                title = "OpenAPI Definition";
                break;
            case "ASYNCAPI":
                title = "AsyncAPI Definition";
                break;
            case "GRAPHQL":
                title = "GraphQL Definition";
                break;
            case "KCONNECT":
                title = "Kafka Connect Schema";
                break;
            case "WSDL":
                title = "WSDL";
                break;
            case "XSD":
                title = "XML Schema";
                break;
            case "XML":
                title = "XML";
                break;
        }
        return title;
    }

    public static getLabel(type: string): string {
        let title: string = type;
        switch (type) {
            case "AVRO":
                title = "Avro Schema";
                break;
            case "PROTOBUF":
                title = "Protocol Buffer Schema";
                break;
            case "JSON":
                title = "JSON Schema";
                break;
            case "OPENAPI":
                title = "OpenAPI";
                break;
            case "ASYNCAPI":
                title = "AsyncAPI";
                break;
            case "GRAPHQL":
                title = "GraphQL";
                break;
            case "KCONNECT":
                title = "Kafka Connect Schema";
                break;
            case "WSDL":
                title = "WSDL";
                break;
            case "XSD":
                title = "XML Schema";
                break;
            case "XML":
                title = "XML";
                break;
        }
        return title;
    }

    public static getClassNames(type: string): string {
        let classes: string = "artifact-type-icon";
        switch (type) {
            case "AVRO":
                classes += " avro-icon24";
                break;
            case "PROTOBUF":
                classes += " protobuf-icon24";
                break;
            case "JSON":
                classes += " json-icon24";
                break;
            case "OPENAPI":
                classes += " oai-icon24";
                break;
            case "ASYNCAPI":
                classes += " aai-icon24";
                break;
            case "GRAPHQL":
                classes += " graphql-icon24";
                break;
            case "KCONNECT":
                classes += " kconnect-icon24";
                break;
            case "WSDL":
                classes += " xml-icon24";
                break;
            case "XSD":
                classes += " xml-icon24";
                break;
            case "XML":
                classes += " xml-icon24";
                break;
            default:
                classes += " questionmark-icon24";
                break;
        }
        return classes;
    }

}
