import React, { FunctionComponent, useEffect, useState } from "react";
import "./GlobalContractRulesPage.css";
import {
    ActionList,
    ActionListItem,
    Alert,
    Button,
    Content,
    EmptyState,
    EmptyStateBody,
    Form,
    FormGroup,
    FormHelperText,
    FormSelect,
    FormSelectOption,
    HelperText,
    HelperTextItem,
    Label,
    Modal,
    ModalBody,
    ModalFooter,
    ModalHeader,
    ModalVariant,
    PageSection,
    PageSectionVariants,
    TextInput,
} from "@patternfly/react-core";
import { Table, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table";
import { TrashIcon } from "@patternfly/react-icons";
import { RootPageHeader } from "@app/components";
import { CONTRACT_RULES_PAGE_IDX, PageDataLoader, PageError, PageErrorHandler, PageProperties, toPageError } from "@app/pages";
import {
    ContractRule,
    ContractRuleSet,
    ContractsService,
    useContractsService,
} from "@services/useContractsService.ts";

const EMPTY_RULE: ContractRule = {
    name: "",
    kind: "CONDITION",
    type: "CEL",
    mode: "WRITE",
    expr: "",
    onFailure: "ERROR",
    disabled: false,
};

export const GlobalContractRulesPage: FunctionComponent<PageProperties> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [ruleset, setRuleset] = useState<ContractRuleSet>({ domainRules: [], migrationRules: [] });
    const [isAddModalOpen, setIsAddModalOpen] = useState(false);
    const [addRuleCategory, setAddRuleCategory] = useState<"domain" | "migration">("domain");
    const [newRule, setNewRule] = useState<ContractRule>({ ...EMPTY_RULE });
    const [actionError, setActionError] = useState<string>();

    const contracts: ContractsService = useContractsService();

    const createLoaders = (): Promise<any> => {
        return contracts.getGlobalContractRuleset().then(setRuleset).catch(error => {
            setPageError(toPageError(error, "Error loading global contract rules."));
        });
    };

    useEffect(() => {
        setLoaders(createLoaders());
    }, []);

    const allRules = [...(ruleset.domainRules || []), ...(ruleset.migrationRules || [])];

    const saveRuleset = (updated: ContractRuleSet) => {
        setActionError(undefined);
        contracts.setGlobalContractRuleset(updated).then(saved => {
            setRuleset(saved);
        }).catch(e => {
            setActionError("Failed to save: " + (e?.response?.data?.detail || e?.message || "unknown"));
        });
    };

    const doAddRule = () => {
        setActionError(undefined);
        if (!newRule.name || !newRule.expr) {
            setActionError("Name and expression are required.");
            return;
        }
        const updated: ContractRuleSet = { ...ruleset };
        if (addRuleCategory === "domain") {
            updated.domainRules = [...(updated.domainRules || []), newRule];
        } else {
            updated.migrationRules = [...(updated.migrationRules || []), newRule];
        }
        contracts.setGlobalContractRuleset(updated).then(saved => {
            setRuleset(saved);
            setIsAddModalOpen(false);
            setNewRule({ ...EMPTY_RULE });
        }).catch(e => {
            setActionError("Failed to add rule: " + (e?.response?.data?.detail || e?.message || "unknown"));
        });
    };

    const doDeleteRule = (idx: number, category: "domain" | "migration") => {
        const updated: ContractRuleSet = { ...ruleset };
        if (category === "domain") {
            updated.domainRules = (updated.domainRules || []).filter((_, i) => i !== idx);
        } else {
            updated.migrationRules = (updated.migrationRules || []).filter((_, i) => i !== idx);
        }
        saveRuleset(updated);
    };

    const doDeleteAll = () => {
        setActionError(undefined);
        contracts.deleteGlobalContractRuleset().then(() => {
            setRuleset({ domainRules: [], migrationRules: [] });
        }).catch(e => {
            setActionError("Failed to delete: " + (e?.response?.data?.detail || e?.message || "unknown"));
        });
    };

    const ruleKindLabel = (kind: string | undefined): React.ReactNode => {
        if (kind === "CONDITION") return <Label color="blue">Condition</Label>;
        if (kind === "TRANSFORM") return <Label color="purple">Transform</Label>;
        return <Label>{kind || "-"}</Label>;
    };

    const openAddModal = (category: "domain" | "migration") => {
        setAddRuleCategory(category);
        setNewRule({
            ...EMPTY_RULE,
            kind: category === "domain" ? "CONDITION" : "TRANSFORM",
            mode: category === "domain" ? "WRITE" : "UPGRADE",
            type: category === "domain" ? "CEL" : "JSONATA",
        });
        setActionError(undefined);
        setIsAddModalOpen(true);
    };

    const renderRulesTable = (rules: ContractRule[], category: "domain" | "migration", title: string) => (
        <>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 20, marginBottom: 10 }}>
                <strong>{title}</strong>
                <Button variant="secondary" size="sm" onClick={() => openAddModal(category)}>
                    Add {category} rule
                </Button>
            </div>
            {rules.length > 0 ? (
                <Table aria-label={`${title}`} variant="compact">
                    <Thead>
                        <Tr>
                            <Th>Name</Th>
                            <Th>Kind</Th>
                            <Th>Type</Th>
                            <Th>Mode</Th>
                            <Th>Expression</Th>
                            <Th>On Failure</Th>
                            <Th>Enabled</Th>
                            <Th />
                        </Tr>
                    </Thead>
                    <Tbody>
                        {rules.map((rule, idx) => (
                            <Tr key={idx}>
                                <Td>{rule.name || "-"}</Td>
                                <Td>{ruleKindLabel(rule.kind)}</Td>
                                <Td><Label isCompact>{rule.type || "-"}</Label></Td>
                                <Td>{rule.mode || "-"}</Td>
                                <Td><code>{rule.expr || "-"}</code></Td>
                                <Td>{rule.onFailure || "-"}</Td>
                                <Td>{rule.disabled ? <Label color="grey">Disabled</Label> : <Label color="green">Active</Label>}</Td>
                                <Td>
                                    <Button variant="plain" aria-label="Delete rule"
                                        onClick={() => doDeleteRule(idx, category)}>
                                        <TrashIcon />
                                    </Button>
                                </Td>
                            </Tr>
                        ))}
                    </Tbody>
                </Table>
            ) : (
                <Content component="p" style={{ color: "#666", fontStyle: "italic" }}>
                    No {category} rules defined.
                </Content>
            )}
        </>
    );

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection hasBodyWrapper={false} className="ps_contract-rules-header" padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={CONTRACT_RULES_PAGE_IDX} />
                </PageSection>
                <PageSection hasBodyWrapper={false} className="ps_contract-rules-description">
                    <Content>
                        Manage global contract rules that apply to all artifacts in this registry.
                        These rules are evaluated during Kafka SerDes serialization/deserialization when
                        contract rule enforcement is enabled. Rules at the artifact or version level override
                        global rules with the same name.
                    </Content>
                </PageSection>
                <PageSection hasBodyWrapper={false} variant={PageSectionVariants.default} isFilled={true}>
                    {actionError && (
                        <Alert variant="danger" title={actionError} isInline
                            style={{ marginBottom: 15 }}
                            actionClose={<Button variant="plain" onClick={() => setActionError(undefined)}>Dismiss</Button>} />
                    )}

                    {renderRulesTable(ruleset.domainRules || [], "domain", "Domain Rules")}
                    {renderRulesTable(ruleset.migrationRules || [], "migration", "Migration Rules")}

                    {allRules.length > 0 && (
                        <div style={{ marginTop: 20 }}>
                            <Button variant="danger" onClick={doDeleteAll}>
                                Delete all global contract rules
                            </Button>
                        </div>
                    )}
                </PageSection>
            </PageDataLoader>

            {/* Add rule modal */}
            <Modal variant={ModalVariant.medium}
                isOpen={isAddModalOpen}
                onClose={() => setIsAddModalOpen(false)}>
                <ModalHeader title={`Add ${addRuleCategory} rule`} />
                <ModalBody>
                    <Form isHorizontal>
                        <FormGroup label="Name" isRequired fieldId="rule-name">
                            <TextInput id="rule-name" value={newRule.name}
                                onChange={(_e, val) => setNewRule({ ...newRule, name: val })} />
                            <FormHelperText>
                                <HelperText><HelperTextItem>Unique name for this rule.</HelperTextItem></HelperText>
                            </FormHelperText>
                        </FormGroup>
                        <FormGroup label="Kind" fieldId="rule-kind">
                            <FormSelect id="rule-kind" value={newRule.kind}
                                onChange={(_e, val) => setNewRule({ ...newRule, kind: val })}>
                                <FormSelectOption value="CONDITION" label="Condition (validate)" />
                                <FormSelectOption value="TRANSFORM" label="Transform (modify)" />
                            </FormSelect>
                        </FormGroup>
                        <FormGroup label="Type" fieldId="rule-type">
                            <FormSelect id="rule-type" value={newRule.type}
                                onChange={(_e, val) => setNewRule({ ...newRule, type: val })}>
                                <FormSelectOption value="CEL" label="CEL" />
                                <FormSelectOption value="JSONATA" label="JSONata" />
                            </FormSelect>
                        </FormGroup>
                        <FormGroup label="Mode" fieldId="rule-mode">
                            <FormSelect id="rule-mode" value={newRule.mode}
                                onChange={(_e, val) => setNewRule({ ...newRule, mode: val })}>
                                <FormSelectOption value="WRITE" label="Write" />
                                <FormSelectOption value="READ" label="Read" />
                                <FormSelectOption value="WRITEREAD" label="Write + Read" />
                                <FormSelectOption value="UPGRADE" label="Upgrade" />
                                <FormSelectOption value="DOWNGRADE" label="Downgrade" />
                            </FormSelect>
                        </FormGroup>
                        <FormGroup label="Expression" isRequired fieldId="rule-expr">
                            <TextInput id="rule-expr" value={newRule.expr}
                                onChange={(_e, val) => setNewRule({ ...newRule, expr: val })} />
                            <FormHelperText>
                                <HelperText><HelperTextItem>CEL or JSONata expression.</HelperTextItem></HelperText>
                            </FormHelperText>
                        </FormGroup>
                        <FormGroup label="On Failure" fieldId="rule-on-failure">
                            <FormSelect id="rule-on-failure" value={newRule.onFailure}
                                onChange={(_e, val) => setNewRule({ ...newRule, onFailure: val })}>
                                <FormSelectOption value="ERROR" label="Error (reject)" />
                                <FormSelectOption value="DLQ" label="DLQ (dead letter queue)" />
                                <FormSelectOption value="NONE" label="None (log only)" />
                            </FormSelect>
                        </FormGroup>
                    </Form>
                    {actionError && <Alert variant="danger" title={actionError} isInline style={{ marginTop: 15 }} />}
                </ModalBody>
                <ModalFooter>
                    <ActionList>
                        <ActionListItem>
                            <Button variant="primary" onClick={doAddRule}>Add rule</Button>
                        </ActionListItem>
                        <ActionListItem>
                            <Button variant="link" onClick={() => setIsAddModalOpen(false)}>Cancel</Button>
                        </ActionListItem>
                    </ActionList>
                </ModalFooter>
            </Modal>
        </PageErrorHandler>
    );
};
