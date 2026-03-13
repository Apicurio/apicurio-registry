import { FunctionComponent } from "react";
import "./ModelSchemaViewer.css";
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
    ExpandableSection,
    Flex,
    FlexItem,
    Label,
    LabelGroup,
    Title
} from "@patternfly/react-core";
import { JsonSchemaProperties } from "@app/components/jsonSchema/JsonSchemaProperties";

export interface ModelSchemaMetadata {
    contextWindow?: number;
    maxOutputTokens?: number;
    capabilities?: string[];
    pricing?: {
        input?: number;
        output?: number;
        inputTokens?: number;
        outputTokens?: number;
        currency?: string;
        unit?: string;
    };
    trainingDataCutoff?: string;
    supportedLanguages?: string[];
    deprecated?: boolean;
    deprecationDate?: string;
    successorModel?: string;
}

export interface ModelSchema {
    modelId?: string;
    provider?: string;
    version?: string;
    description?: string;
    input?: any;
    output?: any;
    metadata?: ModelSchemaMetadata;
}

export type ModelSchemaViewerProps = {
    modelSchema: ModelSchema;
    className?: string;
};

export const ModelSchemaViewer: FunctionComponent<ModelSchemaViewerProps> = (props: ModelSchemaViewerProps) => {
    const { modelSchema, className } = props;
    const meta = modelSchema.metadata;

    const inputPrice = meta?.pricing?.inputTokens ?? meta?.pricing?.input;
    const outputPrice = meta?.pricing?.outputTokens ?? meta?.pricing?.output;

    return (
        <Card className={`model-schema-viewer ${className || ""}`}>
            <CardHeader>
                <CardTitle>
                    <Flex>
                        <FlexItem>
                            <Title headingLevel="h2" className="model-name">
                                {modelSchema.modelId || "Unknown Model"}
                            </Title>
                        </FlexItem>
                        {modelSchema.version && (
                            <FlexItem align={{ default: "alignRight" }}>
                                <Label color="blue" isCompact>
                                    v{modelSchema.version}
                                </Label>
                            </FlexItem>
                        )}
                    </Flex>
                    {modelSchema.description && (
                        <p className="model-description">{modelSchema.description}</p>
                    )}
                </CardTitle>
            </CardHeader>
            <CardBody>
                <DescriptionList isCompact className="model-basic-info">
                    {modelSchema.provider && (
                        <DescriptionListGroup>
                            <DescriptionListTerm>Provider</DescriptionListTerm>
                            <DescriptionListDescription>
                                <Label color="blue" isCompact>{modelSchema.provider}</Label>
                            </DescriptionListDescription>
                        </DescriptionListGroup>
                    )}
                    {meta?.contextWindow !== undefined && (
                        <DescriptionListGroup>
                            <DescriptionListTerm>Context Window</DescriptionListTerm>
                            <DescriptionListDescription>{meta.contextWindow.toLocaleString()} tokens</DescriptionListDescription>
                        </DescriptionListGroup>
                    )}
                    {meta?.maxOutputTokens !== undefined && (
                        <DescriptionListGroup>
                            <DescriptionListTerm>Max Output Tokens</DescriptionListTerm>
                            <DescriptionListDescription>{meta.maxOutputTokens.toLocaleString()}</DescriptionListDescription>
                        </DescriptionListGroup>
                    )}
                    {meta?.trainingDataCutoff && (
                        <DescriptionListGroup>
                            <DescriptionListTerm>Training Data Cutoff</DescriptionListTerm>
                            <DescriptionListDescription>{meta.trainingDataCutoff}</DescriptionListDescription>
                        </DescriptionListGroup>
                    )}
                </DescriptionList>

                {meta?.capabilities && meta.capabilities.length > 0 && (
                    <>
                        <Divider className="section-divider" />
                        <Title headingLevel="h3" size="md">Capabilities</Title>
                        <LabelGroup className="section-content">
                            {meta.capabilities.map((cap, index) => (
                                <Label key={index} color="teal" isCompact>
                                    {cap}
                                </Label>
                            ))}
                        </LabelGroup>
                    </>
                )}

                {meta?.pricing && (inputPrice !== undefined || outputPrice !== undefined) && (
                    <>
                        <Divider className="section-divider" />
                        <Title headingLevel="h3" size="md">Pricing</Title>
                        <DescriptionList isCompact className="section-content">
                            {inputPrice !== undefined && (
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Input</DescriptionListTerm>
                                    <DescriptionListDescription>
                                        {meta.pricing!.currency || "USD"} {inputPrice} / {meta.pricing!.unit || "1M tokens"}
                                    </DescriptionListDescription>
                                </DescriptionListGroup>
                            )}
                            {outputPrice !== undefined && (
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Output</DescriptionListTerm>
                                    <DescriptionListDescription>
                                        {meta.pricing!.currency || "USD"} {outputPrice} / {meta.pricing!.unit || "1M tokens"}
                                    </DescriptionListDescription>
                                </DescriptionListGroup>
                            )}
                        </DescriptionList>
                    </>
                )}

                {meta?.supportedLanguages && meta.supportedLanguages.length > 0 && (
                    <>
                        <Divider className="section-divider" />
                        <Title headingLevel="h3" size="md">Supported Languages</Title>
                        <LabelGroup className="section-content">
                            {meta.supportedLanguages.map((lang, index) => (
                                <Label key={index} color="grey" isCompact>
                                    {lang}
                                </Label>
                            ))}
                        </LabelGroup>
                    </>
                )}

                {meta?.deprecated && (
                    <>
                        <Divider className="section-divider" />
                        <Title headingLevel="h3" size="md">Deprecation</Title>
                        <DescriptionList isCompact className="section-content">
                            <DescriptionListGroup>
                                <DescriptionListTerm>Status</DescriptionListTerm>
                                <DescriptionListDescription>
                                    <Label color="orange" isCompact>Deprecated</Label>
                                </DescriptionListDescription>
                            </DescriptionListGroup>
                            {meta.deprecationDate && (
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Deprecation Date</DescriptionListTerm>
                                    <DescriptionListDescription>{meta.deprecationDate}</DescriptionListDescription>
                                </DescriptionListGroup>
                            )}
                            {meta.successorModel && (
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Successor Model</DescriptionListTerm>
                                    <DescriptionListDescription>{meta.successorModel}</DescriptionListDescription>
                                </DescriptionListGroup>
                            )}
                        </DescriptionList>
                    </>
                )}

                {modelSchema.input && (
                    <>
                        <Divider className="section-divider" />
                        <ExpandableSection toggleText="Input Schema">
                            <JsonSchemaProperties schema={modelSchema.input} depth={0} />
                        </ExpandableSection>
                    </>
                )}

                {modelSchema.output && (
                    <>
                        <Divider className="section-divider" />
                        <ExpandableSection toggleText="Output Schema">
                            <JsonSchemaProperties schema={modelSchema.output} depth={0} />
                        </ExpandableSection>
                    </>
                )}
            </CardBody>
        </Card>
    );
};
