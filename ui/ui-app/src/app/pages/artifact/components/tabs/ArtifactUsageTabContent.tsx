import React, { FunctionComponent, useEffect, useState } from "react";
import {
    Alert,
    Card,
    CardBody,
    CardTitle,
    DescriptionList,
    DescriptionListDescription,
    DescriptionListGroup,
    DescriptionListTerm,
    EmptyState,
    EmptyStateBody,
    EmptyStateVariant,
    Flex,
    FlexItem,
    Label,
    Spinner,
    Title
} from "@patternfly/react-core";
import { CubesIcon } from "@patternfly/react-icons";
import {
    Table,
    Thead,
    Tbody,
    Tr,
    Th,
    Td
} from "@patternfly/react-table";
import { ArtifactMetaData, ConsumerVersionHeatmap, DeprecationReadiness } from "@sdk/lib/generated-client/models";
import { UsageService, useUsageService } from "@services/useUsageService.ts";

export type ArtifactUsageTabContentProps = {
    artifact: ArtifactMetaData;
};

export const ArtifactUsageTabContent: FunctionComponent<ArtifactUsageTabContentProps> = (props) => {
    const [heatmap, setHeatmap] = useState<ConsumerVersionHeatmap | null>(null);
    const [deprecation, setDeprecation] = useState<DeprecationReadiness | null>(null);
    const [selectedVersion, setSelectedVersion] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const usage: UsageService = useUsageService();

    useEffect(() => {
        setIsLoading(true);
        setError(null);
        const groupId = props.artifact.groupId || "default";
        const artifactId = props.artifact.artifactId;
        if (!artifactId) {
            setError("Artifact ID is not available.");
            setIsLoading(false);
            return;
        }
        usage.getConsumerVersionHeatmap(groupId, artifactId).then(data => {
            setHeatmap(data);
            setIsLoading(false);
        }).catch((err) => {
            console.error("Failed to load usage data:", err);
            setError("Could not load usage data. Usage telemetry may not be enabled on this registry.");
            setIsLoading(false);
        });
    }, [props.artifact]);

    const loadDeprecation = async (version: string) => {
        setSelectedVersion(version);
        setDeprecation(null);
        const groupId = props.artifact.groupId || "default";
        const artifactId = props.artifact.artifactId;
        if (!artifactId) return;
        try {
            const data = await usage.getDeprecationReadiness(groupId, artifactId, version);
            if (data) {
                setDeprecation(data);
            }
        } catch (err) {
            console.error("Failed to load deprecation readiness:", err);
        }
    };

    const cellColor = (count: number | undefined): string => {
        if (!count || count === 0) return "";
        if (count > 100) return "#bee1f4";
        if (count > 10) return "#d2d2d2";
        return "#ededed";
    };

    if (isLoading) {
        return (
            <div style={{ padding: "48px", textAlign: "center" }}>
                <Spinner size="xl" />
                <p style={{ marginTop: "16px", color: "#6a6e73" }}>Loading usage data...</p>
            </div>
        );
    }

    if (error) {
        return (
            <div style={{ padding: "24px" }}>
                <Alert variant="warning" title="Usage data unavailable" isInline>
                    {error}
                </Alert>
            </div>
        );
    }

    if (!heatmap || !heatmap.consumers || heatmap.consumers.length === 0) {
        return (
            <div style={{ padding: "24px" }}>
                <EmptyState headingLevel="h4" icon={CubesIcon} titleText="No usage data" variant={EmptyStateVariant.lg}>
                    <EmptyStateBody>
                        No clients have fetched versions of this artifact yet.
                        To track usage, configure your SerDes client with
                        <code> apicurio.registry.usage-telemetry.client-id</code>.
                    </EmptyStateBody>
                </EmptyState>
            </div>
        );
    }

    return (
        <div style={{ padding: "24px" }}>
            <Flex direction={{ default: "column" }} spaceItems={{ default: "spaceItemsLg" }}>
                <FlexItem>
                    <Card>
                        <CardTitle>
                            <Title headingLevel="h3">Consumer Version Heatmap</Title>
                        </CardTitle>
                        <CardBody>
                            <p style={{ marginBottom: "16px", color: "#6a6e73" }}>
                                Which client applications are using which versions.
                                Click a version column header to check deprecation readiness.
                            </p>
                            <Table aria-label="Consumer version heatmap" variant="compact" borders>
                                <Thead>
                                    <Tr>
                                        <Th>Consumer</Th>
                                        {(heatmap.versions || []).map(v => (
                                            <Th key={v} style={{ textAlign: "center", cursor: "pointer" }}
                                                onClick={() => loadDeprecation(v)}>
                                                {v}
                                            </Th>
                                        ))}
                                        <Th>Drift</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {(heatmap.consumers || []).map(consumer => (
                                        <Tr key={consumer.clientId || ""}>
                                            <Td>
                                                <strong>{consumer.clientId}</strong>
                                            </Td>
                                            {(heatmap.versions || []).map(v => {
                                                const count = consumer.versions?.additionalData?.[v] as number | undefined;
                                                return (
                                                    <Td key={v} style={{
                                                        textAlign: "center",
                                                        backgroundColor: cellColor(count),
                                                        fontWeight: count ? "bold" : "normal",
                                                        color: count ? "#000" : "#ccc"
                                                    }}>
                                                        {count || "—"}
                                                    </Td>
                                                );
                                            })}
                                            <Td style={{ textAlign: "center" }}>
                                                {consumer.driftAlert ? (
                                                    <Label color="red">{consumer.versionsBehind} behind</Label>
                                                ) : (
                                                    <Label color="green">Current</Label>
                                                )}
                                            </Td>
                                        </Tr>
                                    ))}
                                </Tbody>
                            </Table>
                        </CardBody>
                    </Card>
                </FlexItem>

                {selectedVersion && (
                    <FlexItem>
                        <Card>
                            <CardTitle>
                                <Flex spaceItems={{ default: "spaceItemsMd" }} alignItems={{ default: "alignItemsCenter" }}>
                                    <FlexItem>
                                        <Title headingLevel="h3">Deprecation Readiness — v{selectedVersion}</Title>
                                    </FlexItem>
                                    <FlexItem>
                                        {deprecation === null ? (
                                            <Spinner size="sm" />
                                        ) : deprecation.safeToDeprecate ? (
                                            <Label color="green">Safe to deprecate</Label>
                                        ) : (
                                            <Label color="red">Active consumers — not safe</Label>
                                        )}
                                    </FlexItem>
                                </Flex>
                            </CardTitle>
                            <CardBody>
                                {deprecation === null ? (
                                    <p style={{ color: "#6a6e73" }}>Loading deprecation readiness...</p>
                                ) : !deprecation.activeConsumers || deprecation.activeConsumers.length === 0 ? (
                                    <p>No active consumers are using this version. It is safe to deprecate or sunset.</p>
                                ) : (
                                    <React.Fragment>
                                        <p style={{ marginBottom: "12px" }}>
                                            Deprecating v{selectedVersion} will affect <strong>{(deprecation.activeConsumers || []).length}</strong> active consumer(s):
                                        </p>
                                        <DescriptionList isHorizontal>
                                            {(deprecation.activeConsumers || []).map(c => (
                                                <DescriptionListGroup key={c.clientId || ""}>
                                                    <DescriptionListTerm>{c.clientId}</DescriptionListTerm>
                                                    <DescriptionListDescription>
                                                        {c.fetchCount} fetches, last seen {new Date(c.lastFetched || 0).toLocaleDateString()}
                                                    </DescriptionListDescription>
                                                </DescriptionListGroup>
                                            ))}
                                        </DescriptionList>
                                    </React.Fragment>
                                )}
                            </CardBody>
                        </Card>
                    </FlexItem>
                )}
            </Flex>
        </div>
    );
};
