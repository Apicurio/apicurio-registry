import { FunctionComponent } from "react";
import "./JsonSchemaViewer.css";
import {
    Card,
    CardBody,
    CardHeader,
    CardTitle,
    DescriptionList,
    DescriptionListDescription,
    DescriptionListGroup,
    DescriptionListTerm,
    Divider,
    Flex,
    FlexItem,
    Label,
    Title
} from "@patternfly/react-core";
import { JsonSchemaProperties } from "./JsonSchemaProperties";

/**
 * Properties for the JsonSchemaViewer component.
 */
export type JsonSchemaViewerProps = {
    schema: any;
    className?: string;
};

/**
 * Component to display a JSON Schema document in a structured, read-only view.
 * Renders schema metadata (title, description, $schema) and recursively renders
 * properties with type badges, constraints, and expandable nested structures.
 */
export const JsonSchemaViewer: FunctionComponent<JsonSchemaViewerProps> = (props: JsonSchemaViewerProps) => {
    const { schema, className } = props;

    const title = schema?.title || "JSON Schema";
    const description = schema?.description;
    const schemaVersion = schema?.$schema;
    const rootType = schema?.type;

    return (
        <Card className={`json-schema-viewer ${className || ""}`} isPlain={true}>
            <CardHeader>
                <CardTitle>
                    <Flex>
                        <FlexItem>
                            <Title headingLevel="h2" className="json-schema-title">
                                {title}
                            </Title>
                        </FlexItem>
                        {rootType && (
                            <FlexItem align={{ default: "alignRight" }}>
                                <Label color="blue" isCompact>
                                    {rootType}
                                </Label>
                            </FlexItem>
                        )}
                    </Flex>
                    {description && (
                        <p className="json-schema-description">{description}</p>
                    )}
                </CardTitle>
            </CardHeader>
            <CardBody>
                {schemaVersion && (
                    <DescriptionList isCompact className="json-schema-meta">
                        <DescriptionListGroup>
                            <DescriptionListTerm>Schema</DescriptionListTerm>
                            <DescriptionListDescription>{schemaVersion}</DescriptionListDescription>
                        </DescriptionListGroup>
                    </DescriptionList>
                )}

                <Divider className="section-divider" />

                <Title headingLevel="h3" size="md" className="json-schema-section-title">
                    Properties
                </Title>
                <JsonSchemaProperties schema={schema} depth={0} />
            </CardBody>
        </Card>
    );
};
