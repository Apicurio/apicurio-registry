import { Services } from "@services/services.ts";

export class ArtifactTypes {

    public static AVRO = "AVRO";
    public static PROTOBUF = "PROTOBUF";
    public static JSON = "JSON";
    public static OPENAPI = "OPENAPI";
    public static ASYNCAPI = "ASYNCAPI";
    public static GRAPHQL = "GRAPHQL";
    public static KCONNECT = "KCONNECT";
    public static WSDL = "WSDL";
    public static XSD = "XSD";
    public static XML = "XML";

    public static async allTypes(): Promise<string[]> {
        try {
            // TODO cache the result of this
            return (await Services.getAdminService().getArtifactTypes()).map(t => t.name);
        } catch (e) {
            return ["AVRO", "PROTOBUF", "JSON", "OPENAPI", "ASYNCAPI", "GRAPHQL", "KCONNECT", "WSDL", "XSD", "XML"];
        }
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
