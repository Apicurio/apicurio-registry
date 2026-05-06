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
    Flex,
    FlexItem,
    Label,
    Spinner,
    Title
} from "@patternfly/react-core";
import {
    Table,
    Thead,
    Tbody,
    Tr,
    Th,
    Td
} from "@patternfly/react-table";
import { ArtifactMetaData } from "@sdk/lib/generated-client/models";
import { UsageService, useUsageService } from "@services/useUsageService.ts";

export type ArtifactUsageTabContentProps = {
    artifact: ArtifactMetaData;
};

interface HeatmapData {
    groupId: string;
    artifactId: string;
    versions: string[];
    consumers: ConsumerEntry[];
}

interface ConsumerEntry {
    clientId: string;
    versions: Record<string, number>;
    versionsBehind: number;
    driftAlert: boolean;
}

interface DeprecationData {
    version: string;
    activeConsumers: { clientId: string; lastFetched: number; fetchCount: number }[];
    safeToDeprecate: boolean;
}

export const ArtifactUsageTabContent: FunctionComponent<ArtifactUsageTabContentProps> = (props) => {
    const [heatmap, setHeatmap] = useState<HeatmapData | null>(null);
    const [deprecation, setDeprecation] = useState<DeprecationData | null>(null);
    const [selectedVersion, setSelectedVersion] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const usage: UsageService = useUsageService();

    useEffect(() => {
        setIsLoading(true);
        const groupId = props.artifact.groupId || "default";
        const artifactId = props.artifact.artifactId;
        if (!artifactId) {
            setError("Artifact ID is not available.");
            setIsLoading(false);
            return;
        }
        usage.getConsumerVersionHeatmap(groupId, artifactId).then(data => {
            if (data) {
                setHeatmap(data);
            } else {
                setError("Usage telemetry is not enabled or no data available.");
            }
            setIsLoading(false);
        }).catch((err) => {
            console.error("Failed to load heatmap data:", err);
            setError("Usage telemetry is not enabled or no data available.");
            setIsLoading(false);
        });
    }, [props.artifact]);

    const loadDeprecation = async (version: string) => {
        setSelectedVersion(version);
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
            // ignore
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
            <div style={{ padding: "24px", textAlign: "center" }}>
                <Spinner size="lg" />
                <p>Loading usage data...</p>
            </div>
        );
    }

    if (error) {
        return (
            <div style={{ padding: "24px" }}>
                <Alert variant="info" title={error} />
            </div>
        );
    }

    if (!heatmap || heatmap.consumers.length === 0) {
        return (
            <div style={{ padding: "24px" }}>
                <Alert variant="info" title="No usage data available for this artifact." />
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
                                Shows which client applications are using which versions. Cells show fetch counts.
                                Consumers flagged with drift alerts are using outdated versions.
                            </p>
                            <Table aria-label="Consumer version heatmap" variant="compact" borders>
                                <Thead>
                                    <Tr>
                                        <Th>Consumer</Th>
                                        {heatmap.versions.map(v => (
                                            <Th key={v} style={{ textAlign: "center", cursor: "pointer" }}
                                                onClick={() => loadDeprecation(v)}>
                                                {v}
                                            </Th>
                                        ))}
                                        <Th>Drift</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {heatmap.consumers.map(consumer => (
                                        <Tr key={consumer.clientId}>
                                            <Td>
                                                <strong>{consumer.clientId}</strong>
                                            </Td>
                                            {heatmap.versions.map(v => {
                                                const count = consumer.versions?.[v];
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

                {deprecation && selectedVersion && (
                    <FlexItem>
                        <Card>
                            <CardTitle>
                                <Flex spaceItems={{ default: "spaceItemsMd" }}>
                                    <FlexItem>
                                        <Title headingLevel="h3">Deprecation Readiness — v{selectedVersion}</Title>
                                    </FlexItem>
                                    <FlexItem>
                                        {deprecation.safeToDeprecate ? (
                                            <Label color="green">Safe to deprecate</Label>
                                        ) : (
                                            <Label color="red">Active consumers — not safe</Label>
                                        )}
                                    </FlexItem>
                                </Flex>
                            </CardTitle>
                            <CardBody>
                                {deprecation.activeConsumers.length === 0 ? (
                                    <p>No active consumers are using this version. It is safe to deprecate or sunset.</p>
                                ) : (
                                    <React.Fragment>
                                        <p style={{ marginBottom: "12px" }}>
                                            Deprecating v{selectedVersion} will affect <strong>{deprecation.activeConsumers.length}</strong> active consumer(s):
                                        </p>
                                        <DescriptionList isHorizontal>
                                            {deprecation.activeConsumers.map(c => (
                                                <DescriptionListGroup key={c.clientId}>
                                                    <DescriptionListTerm>{c.clientId}</DescriptionListTerm>
                                                    <DescriptionListDescription>
                                                        {c.fetchCount} fetches, last seen {new Date(c.lastFetched).toLocaleDateString()}
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
