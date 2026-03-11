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

export interface ModelSchemaMetadata {
    contextWindow?: number;
    maxOutputTokens?: number;
    capabilities?: string[];
    pricing?: {
        input?: number;
        output?: number;
        currency?: string;
        unit?: string;
    };
    trainingDataCutoff?: string;
    supportedLanguages?: string[];
    deprecation?: {
        deprecated?: boolean;
        sunset_date?: string;
        replacement_model?: string;
    };
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

                {meta?.pricing && (
                    <>
                        <Divider className="section-divider" />
                        <Title headingLevel="h3" size="md">Pricing</Title>
                        <DescriptionList isCompact className="section-content">
                            {meta.pricing.input !== undefined && (
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Input</DescriptionListTerm>
                                    <DescriptionListDescription>
                                        {meta.pricing.currency || "USD"} {meta.pricing.input} / {meta.pricing.unit || "1K tokens"}
                                    </DescriptionListDescription>
                                </DescriptionListGroup>
                            )}
                            {meta.pricing.output !== undefined && (
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Output</DescriptionListTerm>
                                    <DescriptionListDescription>
                                        {meta.pricing.currency || "USD"} {meta.pricing.output} / {meta.pricing.unit || "1K tokens"}
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

                {meta?.deprecation?.deprecated && (
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
                            {meta.deprecation.sunset_date && (
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Sunset Date</DescriptionListTerm>
                                    <DescriptionListDescription>{meta.deprecation.sunset_date}</DescriptionListDescription>
                                </DescriptionListGroup>
                            )}
                            {meta.deprecation.replacement_model && (
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Replacement</DescriptionListTerm>
                                    <DescriptionListDescription>{meta.deprecation.replacement_model}</DescriptionListDescription>
                                </DescriptionListGroup>
                            )}
                        </DescriptionList>
                    </>
                )}

                {modelSchema.input && (
                    <>
                        <Divider className="section-divider" />
                        <ExpandableSection toggleText="Input Schema">
                            <pre className="schema-json">
                                {JSON.stringify(modelSchema.input, null, 2)}
                            </pre>
                        </ExpandableSection>
                    </>
                )}

                {modelSchema.output && (
                    <>
                        <Divider className="section-divider" />
                        <ExpandableSection toggleText="Output Schema">
                            <pre className="schema-json">
                                {JSON.stringify(modelSchema.output, null, 2)}
                            </pre>
                        </ExpandableSection>
                    </>
                )}
            </CardBody>
        </Card>
    );
};
