import { FunctionComponent, useEffect, useState } from "react";
import "./ArtifactContractTabContent.css";
import {
    ActionList,
    ActionListItem,
    Alert,
    Button,
    Card,
    CardBody,
    CardTitle,
    DescriptionList,
    DescriptionListDescription,
    DescriptionListGroup,
    DescriptionListTerm,
    Divider,
    EmptyState,
    EmptyStateBody,
    Grid,
    GridItem,
    HelperText,
    HelperTextItem,
    Label,
    Modal,
    ModalBody,
    ModalFooter,
    ModalHeader,
    ModalVariant,
    Timestamp,
    TimestampFormat,
} from "@patternfly/react-core";
import { ArrowUpIcon, ExternalLinkAltIcon } from "@patternfly/react-icons";
import { Table, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table";
import { ArtifactMetaData } from "@sdk/lib/generated-client/models";
import { ContractStatusBadge, QualityScoreGauge } from "@app/components/contracts";
import { useAppNavigation } from "@services/useAppNavigation.ts";
import {
    ContractAuditEntry,
    ContractMetadata,
    ContractRuleSet,
    ContractsService,
    QualityScore,
    useContractsService
} from "@services/useContractsService.ts";

export type ArtifactContractTabContentProps = {
    artifact: ArtifactMetaData;
};

export const ArtifactContractTabContent: FunctionComponent<ArtifactContractTabContentProps> = (props: ArtifactContractTabContentProps) => {

    const [contractMetadata, setContractMetadata] = useState<ContractMetadata>();
    const [qualityScore, setQualityScore] = useState<QualityScore>();
    const [auditLog, setAuditLog] = useState<ContractAuditEntry[]>([]);
    const [ruleset, setRuleset] = useState<ContractRuleSet>();
    const [hasContract, setHasContract] = useState<boolean>(false);
    const [actionError, setActionError] = useState<string>();
    const [isPromoteModalOpen, setIsPromoteModalOpen] = useState(false);
    const [isStatusModalOpen, setIsStatusModalOpen] = useState(false);
    const [isOdcsEditorOpen, setIsOdcsEditorOpen] = useState(false);
    const [odcsEditorValue, setOdcsEditorValue] = useState<string>("");
    const [odcsSaving, setOdcsSaving] = useState(false);

    const contracts: ContractsService = useContractsService();
    const appNavigation = useAppNavigation();

    const groupId = props.artifact?.groupId || null;
    const artifactId = props.artifact?.artifactId;

    const loadData = () => {
        if (!artifactId) return;

        contracts.getContractMetadata(groupId, artifactId).then(metadata => {
            setContractMetadata(metadata);
            setHasContract(!!metadata.status);
        }).catch(() => setHasContract(false));

        contracts.getContractQuality(groupId, artifactId, contractId()).then(setQualityScore).catch(() => {});
        contracts.getContractAuditLog(groupId, artifactId, 0, 10).then(setAuditLog).catch(() => {});
        contracts.getContractRuleset(groupId, artifactId).then(setRuleset).catch(() => {});
    };

    useEffect(loadData, [props.artifact]);

    const openOdcsEditor = () => {
        setActionError(undefined);
        const cid = contractId();
        if (cid && cid !== "default") {
            contracts.getContractYaml(groupId, cid).then(yaml => {
                setOdcsEditorValue(yaml);
                setIsOdcsEditorOpen(true);
            }).catch(() => {
                contracts.exportContractAsOdcs(groupId, artifactId!).then(yaml => {
                    setOdcsEditorValue(yaml);
                    setIsOdcsEditorOpen(true);
                }).catch(e => {
                    setActionError("Could not load contract YAML: " + (e?.message || "unknown"));
                });
            });
        } else {
            contracts.exportContractAsOdcs(groupId, artifactId!).then(yaml => {
                setOdcsEditorValue(yaml);
                setIsOdcsEditorOpen(true);
            }).catch(e => {
                setActionError("Could not export contract: " + (e?.message || "unknown"));
            });
        }
    };

    const saveOdcsContract = () => {
        setOdcsSaving(true);
        setActionError(undefined);
        const cid = contractId();
        const save = cid && cid !== "default"
            ? contracts.updateContract(groupId, cid, odcsEditorValue)
            : contracts.submitContract(groupId, odcsEditorValue);
        save.then(() => {
            setOdcsSaving(false);
            setIsOdcsEditorOpen(false);
            loadData();
        }).catch(e => {
            setOdcsSaving(false);
            setActionError("Failed to save contract: "
                + (e?.response?.data?.detail || e?.message || "unknown"));
        });
    };

    const contractId = (): string => {
        const labels = (props.artifact?.labels as any)?.additionalData
            || props.artifact?.labels || {};
        for (const key of Object.keys(labels)) {
            if (key.startsWith("contract.") && key.endsWith(".id")) {
                return labels[key] as string;
            }
        }
        return "default";
    };

    const contractArtifactLink = (): string => {
        const gid = encodeURIComponent(groupId || "default");
        const cid = contractId();
        if (cid && cid !== "default") {
            return `/explore/${gid}/${encodeURIComponent(cid)}`;
        }
        return "";
    };

    const nextStage = (): string | null => {
        const stage = contractMetadata?.stage?.toUpperCase();
        if (!stage) return "DEV";
        if (stage === "DEV") return "STAGE";
        if (stage === "STAGE") return "PROD";
        return null;
    };

    const doPromote = () => {
        const target = nextStage();
        if (!target || !artifactId) return;
        setActionError(undefined);
        contracts.promoteContract(groupId, artifactId, contractId(), target).then(() => {
            setIsPromoteModalOpen(false);
            loadData();
        }).catch(e => {
            setActionError("Promotion failed: " + (e?.response?.data?.detail || e?.message || "unknown error"));
        });
    };

    const doTransitionStatus = (status: string) => {
        if (!artifactId) return;
        setActionError(undefined);
        contracts.transitionContractStatus(groupId, artifactId, status).then(() => {
            setIsStatusModalOpen(false);
            loadData();
        }).catch(e => {
            setActionError("Status transition failed: " + (e?.response?.data?.detail || e?.message || "unknown error"));
        });
    };

    const stageLabel = (stage: string | undefined): React.ReactNode => {
        if (!stage) return <Label color="grey">Not promoted</Label>;
        switch (stage.toUpperCase()) {
            case "DEV": return <Label color="teal">DEV</Label>;
            case "STAGE": return <Label color="purple">STAGE</Label>;
            case "PROD": return <Label color="green">PROD</Label>;
            default: return <Label>{stage}</Label>;
        }
    };

    const ruleKindLabel = (kind: string | undefined): React.ReactNode => {
        if (kind === "CONDITION") return <Label color="blue">Condition</Label>;
        if (kind === "TRANSFORM") return <Label color="purple">Transform</Label>;
        return <Label>{kind || "-"}</Label>;
    };

    if (!hasContract) {
        return (
            <div className="artifact-contract-tab-content">
                <EmptyState>
                    <EmptyStateBody>
                        <p>No contract metadata found for this artifact.</p>
                        <p style={{ marginTop: 10 }}>
                            Submit an ODCS contract to define ownership, quality rules, SLA,
                            and governance for this schema.
                        </p>
                    </EmptyStateBody>
                </EmptyState>
            </div>
        );
    }

    const contractLink = contractArtifactLink();
    const domainRules = ruleset?.domainRules || [];
    const migrationRules = ruleset?.migrationRules || [];
    const allRules = [...domainRules, ...migrationRules];
    const promoteTarget = nextStage();

    return (
        <div className="artifact-contract-tab-content">
            {actionError && (
                <Alert variant="danger" title={actionError} isInline
                    style={{ marginBottom: 15 }}
                    actionClose={<Button variant="plain" onClick={() => setActionError(undefined)}>Dismiss</Button>} />
            )}
            <Grid hasGutter>
                {/* Metadata card */}
                <GridItem span={6}>
                    <Card className="contract-section" variant="secondary" style={{ backgroundColor: "white" }}>
                        <CardTitle>Contract Metadata</CardTitle>
                        <Divider />
                        <CardBody>
                            <DescriptionList isHorizontal>
                                {contractLink && (
                                    <DescriptionListGroup>
                                        <DescriptionListTerm>ODCS Contract</DescriptionListTerm>
                                        <DescriptionListDescription>
                                            <Button variant="link" isInline
                                                icon={<ExternalLinkAltIcon />}
                                                iconPosition="end"
                                                onClick={() => appNavigation.navigateTo(contractLink)}>
                                                View contract artifact
                                            </Button>
                                            {" | "}
                                            <Button variant="link" isInline
                                                onClick={openOdcsEditor}>
                                                Edit YAML
                                            </Button>
                                        </DescriptionListDescription>
                                    </DescriptionListGroup>
                                )}
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Status</DescriptionListTerm>
                                    <DescriptionListDescription>
                                        <ContractStatusBadge status={contractMetadata?.status} />
                                        {" "}
                                        <Button variant="link" isInline size="sm"
                                            onClick={() => { setActionError(undefined); setIsStatusModalOpen(true); }}>
                                            Change
                                        </Button>
                                    </DescriptionListDescription>
                                </DescriptionListGroup>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Promotion Stage</DescriptionListTerm>
                                    <DescriptionListDescription>
                                        {stageLabel(contractMetadata?.stage)}
                                        {promoteTarget && (
                                            <>
                                                {" "}
                                                <Button variant="link" isInline size="sm"
                                                    icon={<ArrowUpIcon />}
                                                    onClick={() => { setActionError(undefined); setIsPromoteModalOpen(true); }}>
                                                    Promote to {promoteTarget}
                                                </Button>
                                            </>
                                        )}
                                    </DescriptionListDescription>
                                </DescriptionListGroup>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Owner Team</DescriptionListTerm>
                                    <DescriptionListDescription>{contractMetadata?.ownerTeam || "-"}</DescriptionListDescription>
                                </DescriptionListGroup>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Owner Domain</DescriptionListTerm>
                                    <DescriptionListDescription>{contractMetadata?.ownerDomain || "-"}</DescriptionListDescription>
                                </DescriptionListGroup>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Classification</DescriptionListTerm>
                                    <DescriptionListDescription>{contractMetadata?.classification || "-"}</DescriptionListDescription>
                                </DescriptionListGroup>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Support Contact</DescriptionListTerm>
                                    <DescriptionListDescription>{contractMetadata?.supportContact || "-"}</DescriptionListDescription>
                                </DescriptionListGroup>
                                <DescriptionListGroup>
                                    <DescriptionListTerm>Compatibility Group</DescriptionListTerm>
                                    <DescriptionListDescription>{contractMetadata?.compatibilityGroup || "-"}</DescriptionListDescription>
                                </DescriptionListGroup>
                            </DescriptionList>
                        </CardBody>
                    </Card>
                </GridItem>

                {/* Quality score card */}
                <GridItem span={6}>
                    <Card className="contract-section" variant="secondary" style={{ backgroundColor: "white" }}>
                        <CardTitle>Quality Score</CardTitle>
                        <Divider />
                        <CardBody>
                            {qualityScore ? (
                                <div className="quality-scores">
                                    <div className="quality-score-row">
                                        <QualityScoreGauge label="Overall" score={qualityScore.overall} />
                                        <HelperText>
                                            <HelperTextItem variant="indeterminate">
                                                Weighted average: Completeness (30%) + Compliance (40%) + Stability (30%).
                                                The quality score is informational and does not block any operations.
                                            </HelperTextItem>
                                        </HelperText>
                                    </div>
                                    <div className="quality-score-row">
                                        <QualityScoreGauge label="Completeness (30%)" score={qualityScore.completeness} />
                                        <HelperText>
                                            <HelperTextItem variant={qualityScore.completeness >= 1.0 ? "success" : "warning"}>
                                                {qualityScore.completeness >= 1.0
                                                    ? "All metadata fields are set."
                                                    : "To improve: ensure the ODCS contract YAML includes all of: info.description, team.name, team.domain, team.contact, info.dataClassification, and serviceLevel.availability. These are projected onto the artifact as contract labels when the contract is submitted."}
                                            </HelperTextItem>
                                        </HelperText>
                                    </div>
                                    <div className="quality-score-row">
                                        <QualityScoreGauge label="Compliance (40%)" score={qualityScore.compliance} />
                                        <HelperText>
                                            <HelperTextItem variant={qualityScore.compliance >= 1.0 ? "success" : "warning"}>
                                                {qualityScore.compliance >= 1.0
                                                    ? "Domain rules are defined and contract status is set."
                                                    : "To improve: add at least one domain rule by editing the ODCS contract (quality.accuracy section) and re-submitting it. Also ensure the contract status is set (info.status: active/draft/deprecated)."}
                                            </HelperTextItem>
                                        </HelperText>
                                    </div>
                                    <div className="quality-score-row">
                                        <QualityScoreGauge label="Stability (30%)" score={qualityScore.stability} />
                                        <HelperText>
                                            <HelperTextItem variant={qualityScore.stability >= 1.0 ? "success" : "warning"}>
                                                {qualityScore.stability >= 1.0
                                                    ? "Contract is stable with version history."
                                                    : "To improve: use the Change button above to transition the status to STABLE, publish more than one schema version (so there is version history), and ensure the ODCS contract has an id field."}
                                            </HelperTextItem>
                                        </HelperText>
                                    </div>
                                </div>
                            ) : (
                                <p>Quality score not available.</p>
                            )}
                        </CardBody>
                    </Card>
                </GridItem>

                {/* Contract rules card */}
                <GridItem span={12}>
                    <Card className="contract-section" variant="secondary" style={{ backgroundColor: "white" }}>
                        <CardTitle>Contract Rules</CardTitle>
                        <Divider />
                        <CardBody>
                            {allRules.length > 0 ? (
                                <Table aria-label="Contract rules" variant="compact">
                                    <Thead>
                                        <Tr>
                                            <Th>Name</Th>
                                            <Th>Kind</Th>
                                            <Th>Type</Th>
                                            <Th>Mode</Th>
                                            <Th>Expression</Th>
                                            <Th>On Failure</Th>
                                            <Th>Enabled</Th>
                                        </Tr>
                                    </Thead>
                                    <Tbody>
                                        {allRules.map((rule, idx) => (
                                            <Tr key={idx}>
                                                <Td>{rule.name || "-"}</Td>
                                                <Td>{ruleKindLabel(rule.kind)}</Td>
                                                <Td><Label isCompact>{rule.type || "-"}</Label></Td>
                                                <Td>{rule.mode || "-"}</Td>
                                                <Td><code>{rule.expr || "-"}</code></Td>
                                                <Td>{rule.onFailure || "-"}</Td>
                                                <Td>{rule.disabled ? <Label color="grey">Disabled</Label> : <Label color="green">Active</Label>}</Td>
                                            </Tr>
                                        ))}
                                    </Tbody>
                                </Table>
                            ) : (
                                <>
                                    <p>No contract rules defined. Add quality rules by editing the ODCS contract YAML
                                        (quality.accuracy section) and re-submitting it.</p>
                                    <Button variant="secondary" onClick={openOdcsEditor}
                                        style={{ marginTop: 10 }}>
                                        Edit ODCS Contract
                                    </Button>
                                </>
                            )}
                            <div style={{ marginTop: 10, display: "flex", gap: 10, alignItems: "center" }}>
                                <Button variant="secondary" onClick={openOdcsEditor}>
                                    Edit ODCS Contract
                                </Button>
                                <HelperText>
                                    <HelperTextItem variant="indeterminate">
                                        <strong>Condition</strong> rules validate data (pass/fail). <strong>Transform</strong> rules modify data (e.g. schema migration).
                                        Rules come from the ODCS contract's <code>quality.accuracy</code> section. Edit the contract to add or modify rules.
                                    </HelperTextItem>
                                </HelperText>
                            </div>
                        </CardBody>
                    </Card>
                </GridItem>

                {/* Audit log card */}
                <GridItem span={12}>
                    <Card className="contract-section" variant="secondary" style={{ backgroundColor: "white" }}>
                        <CardTitle>Audit Log</CardTitle>
                        <Divider />
                        <CardBody>
                            {auditLog.length > 0 ? (
                                <Table aria-label="Contract audit log" variant="compact" className="audit-table">
                                    <Thead>
                                        <Tr>
                                            <Th>Action</Th>
                                            <Th>Principal</Th>
                                            <Th>Details</Th>
                                            <Th>Date</Th>
                                        </Tr>
                                    </Thead>
                                    <Tbody>
                                        {auditLog.map((entry) => (
                                            <Tr key={entry.auditId}>
                                                <Td>{entry.action}</Td>
                                                <Td>{entry.principal || "-"}</Td>
                                                <Td>{entry.details || "-"}</Td>
                                                <Td>
                                                    {entry.createdOn ? (
                                                        <Timestamp
                                                            date={new Date(entry.createdOn)}
                                                            dateFormat={TimestampFormat.long}
                                                        />
                                                    ) : "-"}
                                                </Td>
                                            </Tr>
                                        ))}
                                    </Tbody>
                                </Table>
                            ) : (
                                <p>No audit entries yet.</p>
                            )}
                        </CardBody>
                    </Card>
                </GridItem>
            </Grid>

            {/* Promote modal */}
            <Modal variant={ModalVariant.small}
                isOpen={isPromoteModalOpen}
                onClose={() => setIsPromoteModalOpen(false)}>
                <ModalHeader title={`Promote to ${promoteTarget}`} />
                <ModalBody>
                    <p>
                        Promote the contract from <strong>{contractMetadata?.stage || "unset"}</strong> to <strong>{promoteTarget}</strong>?
                    </p>
                    <p style={{ marginTop: 10, color: "#666" }}>
                        Promotion stages: DEV → STAGE → PROD. Promotion to PROD requires the contract status to be STABLE.
                    </p>
                    {actionError && <Alert variant="danger" title={actionError} isInline style={{ marginTop: 10 }} />}
                </ModalBody>
                <ModalFooter>
                    <ActionList>
                        <ActionListItem>
                            <Button variant="primary" onClick={doPromote}>Promote</Button>
                        </ActionListItem>
                        <ActionListItem>
                            <Button variant="link" onClick={() => setIsPromoteModalOpen(false)}>Cancel</Button>
                        </ActionListItem>
                    </ActionList>
                </ModalFooter>
            </Modal>

            {/* Status transition modal */}
            <Modal variant={ModalVariant.small}
                isOpen={isStatusModalOpen}
                onClose={() => setIsStatusModalOpen(false)}>
                <ModalHeader title="Change contract status" />
                <ModalBody>
                    <p>Current status: <ContractStatusBadge status={contractMetadata?.status} /></p>
                    <p style={{ marginTop: 10 }}>Allowed transitions:</p>
                    <ul style={{ marginTop: 5, paddingLeft: 20 }}>
                        <li>DRAFT → STABLE or DEPRECATED</li>
                        <li>STABLE → DEPRECATED</li>
                    </ul>
                    {actionError && <Alert variant="danger" title={actionError} isInline style={{ marginTop: 10 }} />}
                </ModalBody>
                <ModalFooter>
                    <ActionList>
                        {contractMetadata?.status !== "STABLE" && (
                            <ActionListItem>
                                <Button variant="primary" onClick={() => doTransitionStatus("STABLE")}>
                                    Mark as Stable
                                </Button>
                            </ActionListItem>
                        )}
                        {contractMetadata?.status !== "DEPRECATED" && (
                            <ActionListItem>
                                <Button variant="warning" onClick={() => doTransitionStatus("DEPRECATED")}>
                                    Deprecate
                                </Button>
                            </ActionListItem>
                        )}
                        <ActionListItem>
                            <Button variant="link" onClick={() => setIsStatusModalOpen(false)}>Cancel</Button>
                        </ActionListItem>
                    </ActionList>
                </ModalFooter>
            </Modal>

            {/* ODCS YAML editor modal */}
            <Modal variant={ModalVariant.large}
                isOpen={isOdcsEditorOpen}
                onClose={() => setIsOdcsEditorOpen(false)}>
                <ModalHeader title="Edit ODCS Contract" description={
                    "Edit the ODCS v3.1 contract YAML. Changes to quality.accuracy rules, team metadata, "
                    + "and service levels will be projected onto the schema artifact when saved."
                } />
                <ModalBody>
                    <textarea
                        value={odcsEditorValue}
                        onChange={(e) => setOdcsEditorValue(e.target.value)}
                        style={{
                            width: "100%",
                            minHeight: "400px",
                            fontFamily: "monospace",
                            fontSize: "13px",
                            padding: "12px",
                            border: "1px solid #ccc",
                            borderRadius: "4px",
                            resize: "vertical",
                        }}
                        spellCheck={false}
                    />
                    {actionError && <Alert variant="danger" title={actionError} isInline
                        style={{ marginTop: 10 }} />}
                </ModalBody>
                <ModalFooter>
                    <ActionList>
                        <ActionListItem>
                            <Button variant="primary" onClick={saveOdcsContract}
                                isLoading={odcsSaving} isDisabled={odcsSaving}>
                                Save &amp; Re-project
                            </Button>
                        </ActionListItem>
                        <ActionListItem>
                            <Button variant="link"
                                onClick={() => setIsOdcsEditorOpen(false)}>
                                Cancel
                            </Button>
                        </ActionListItem>
                    </ActionList>
                </ModalFooter>
            </Modal>
        </div>
    );
};
