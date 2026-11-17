import { FunctionComponent, useEffect, useState } from "react";
import {
    Alert,
    Button,
    Content,
    Form,
    FormGroup,
    FormHelperText,
    HelperText,
    HelperTextItem,
    Label,
    TextInput,
    Title
} from "@patternfly/react-core";
import { Modal } from "@patternfly/react-core/deprecated";
import { Table, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table";
import { MinusCircleIcon, PlusCircleIcon } from "@patternfly/react-icons";
import {
    McpToolImportRequest,
    McpToolImportResult,
    McpToolImportToolResult
} from "@sdk/lib/generated-client/models";
import { CompatibilityReport } from "@app/components/ruleViolation";
import { toViolationCause } from "@utils/ruleViolation.utils.ts";

type HeaderRow = { name: string; value: string };

/**
 * Properties
 */
export type ImportMcpToolsModalProps = {
    isOpen: boolean;
    groupId: string;
    artifactId: string;
    /** Called with true when an import (not a preview) completed, so the page can reload. */
    onClose: (imported: boolean) => void;
    onImport: (data: McpToolImportRequest) => Promise<McpToolImportResult>;
};

const STATUS_COLORS: Record<string, "green" | "blue" | "grey" | "red" | "orange"> = {
    CREATED: "green",
    NEW_VERSION: "blue",
    UNCHANGED: "grey",
    REJECTED: "red",
    REMOVED: "orange"
};

/**
 * The message of a failed request: the server's title or detail. Never the request itself, which
 * carries the header values.
 */
const errorMessage = (error: any): string => {
    return error?.title || error?.detail || error?.message || "The import failed.";
};

/**
 * Models the dialog that imports the tools of a live MCP server into an MCP_SERVER artifact.
 * <p>
 * Header values are entered in password fields, sent only in the import request, and cleared
 * whenever the dialog opens.
 */
export const ImportMcpToolsModal: FunctionComponent<ImportMcpToolsModalProps> = (props: ImportMcpToolsModalProps) => {
    const [url, setUrl] = useState("");
    const [toolGroupId, setToolGroupId] = useState("");
    const [headers, setHeaders] = useState<HeaderRow[]>([]);
    const [isRunning, setIsRunning] = useState(false);
    const [result, setResult] = useState<McpToolImportResult>();
    const [resultWasPreview, setResultWasPreview] = useState(false);
    const [error, setError] = useState<string>();
    const [imported, setImported] = useState(false);

    useEffect(() => {
        if (props.isOpen) {
            setUrl("");
            setToolGroupId("");
            setHeaders([]);
            setIsRunning(false);
            setResult(undefined);
            setResultWasPreview(false);
            setError(undefined);
            setImported(false);
        }
    }, [props.isOpen]);

    const isValid = (): boolean => {
        return url.trim().length > 0 && headers.every(h => h.name.trim().length > 0 && h.value.length > 0);
    };

    const run = (dryRun: boolean): void => {
        const data: McpToolImportRequest = {
            url: url.trim(),
            dryRun
        };
        if (toolGroupId.trim().length > 0) {
            data.toolGroupId = toolGroupId.trim();
        }
        if (headers.length > 0) {
            const additionalData: Record<string, unknown> = {};
            headers.forEach(h => additionalData[h.name.trim()] = h.value);
            data.headers = { additionalData };
        }
        setIsRunning(true);
        setError(undefined);
        props.onImport(data).then(r => {
            setResult(r);
            setResultWasPreview(dryRun);
            if (!dryRun) {
                setImported(true);
            }
        }).catch(e => {
            setResult(undefined);
            setError(errorMessage(e));
        }).finally(() => setIsRunning(false));
    };

    const close = (): void => {
        // The dialog stays open until the request completes, so its result is not lost and a
        // stale request cannot fill a reopened dialog.
        if (isRunning) {
            return;
        }
        setHeaders([]);
        props.onClose(imported);
    };

    const updateHeader = (index: number, field: keyof HeaderRow, value: string): void => {
        setHeaders(headers.map((h, i) => i === index ? { ...h, [field]: value } : h));
    };

    const summary = (r: McpToolImportResult): string => {
        if (r.status === "UNCHANGED") {
            return `The MCP server is unchanged at version ${r.version}.`;
        }
        if (resultWasPreview) {
            return "Preview: a new version of the MCP server would be created. Nothing was written.";
        }
        return `The MCP server is now at version ${r.version}.`;
    };

    const rejected: McpToolImportToolResult[] = (result?.tools || [])
        .filter(t => t.status === "REJECTED" && (t.causes || []).length > 0);

    return (
        <Modal
            title="Import tools from MCP server"
            variant="large"
            isOpen={props.isOpen}
            onClose={close}
            className="import-mcp-tools pf-m-redhat-font"
            actions={[
                <Button key="import" variant="primary" data-testid="modal-btn-import"
                    isDisabled={!isValid() || isRunning}
                    onClick={() => run(false)}>Import</Button>,
                <Button key="preview" variant="secondary" data-testid="modal-btn-preview"
                    isDisabled={!isValid() || isRunning} onClick={() => run(true)}>Preview</Button>,
                <Button key="close" variant="link" data-testid="modal-btn-close"
                    isDisabled={isRunning} onClick={close}>{result ? "Close" : "Cancel"}</Button>
            ]}
        >
            <Content component="p">
                Connects to a running MCP server, lists its tools, and publishes each one as an MCP_TOOL artifact,
                subject to the configured rules. A new version of <b>{props.artifactId}</b> references the imported tools.
                Use Preview to see the result without writing anything.
            </Content>
            <Form onSubmit={e => e.preventDefault()}>
                <FormGroup label="MCP server URL" isRequired={true} fieldId="form-mcp-url">
                    <TextInput id="form-mcp-url" data-testid="form-mcp-url" type="url" isRequired={true}
                        value={url} placeholder="https://mcp.example.com/mcp"
                        onChange={(_e, v) => setUrl(v)} />
                    <FormHelperText>
                        <HelperText>
                            <HelperTextItem>
                                Streamable HTTP endpoint. The registry must allow its host.
                            </HelperTextItem>
                        </HelperText>
                    </FormHelperText>
                </FormGroup>
                <FormGroup label="Tool group" fieldId="form-mcp-tool-group">
                    <TextInput id="form-mcp-tool-group" data-testid="form-mcp-tool-group" type="text"
                        value={toolGroupId} placeholder={props.groupId}
                        onChange={(_e, v) => setToolGroupId(v)} />
                    <FormHelperText>
                        <HelperText>
                            <HelperTextItem>Group for the MCP_TOOL artifacts. Defaults to the server's group.</HelperTextItem>
                        </HelperText>
                    </FormHelperText>
                </FormGroup>
                <FormGroup label="Headers" fieldId="form-mcp-headers">
                    {headers.map((h, index) => (
                        <div key={index} style={{ display: "flex", gap: "8px", marginBottom: "8px" }}>
                            <TextInput aria-label={`Header ${index + 1} name`} data-testid={`form-mcp-header-name-${index}`}
                                type="text" value={h.name} placeholder="Authorization"
                                onChange={(_e, v) => updateHeader(index, "name", v)} />
                            <TextInput aria-label={`Header ${index + 1} value`} data-testid={`form-mcp-header-value-${index}`}
                                type="password" autoComplete="off" value={h.value}
                                onChange={(_e, v) => updateHeader(index, "value", v)} />
                            <Button variant="plain" aria-label={`Remove header ${index + 1}`}
                                data-testid={`form-mcp-header-remove-${index}`}
                                onClick={() => setHeaders(headers.filter((_h, i) => i !== index))}
                                icon={<MinusCircleIcon />} />
                        </div>
                    ))}
                    <Button variant="link" isInline={true} icon={<PlusCircleIcon />} data-testid="form-mcp-header-add"
                        onClick={() => setHeaders([...headers, { name: "", value: "" }])}>Add header</Button>
                    <FormHelperText>
                        <HelperText>
                            <HelperTextItem>
                                Sent to the MCP server only, for example credentials. They are not stored.
                            </HelperTextItem>
                        </HelperText>
                    </FormHelperText>
                </FormGroup>
            </Form>
            {error && (
                <Alert variant="danger" isInline={true} title={error} data-testid="mcp-import-error"
                    style={{ marginTop: "16px" }} />
            )}
            {result && (
                <div data-testid="mcp-import-result" style={{ marginTop: "16px" }}>
                    <Title headingLevel="h3" size="md">{resultWasPreview ? "Preview" : "Result"}</Title>
                    <Table aria-label="Imported tools" variant="compact">
                        <Thead>
                            <Tr>
                                <Th>Tool</Th>
                                <Th>Group</Th>
                                <Th>Artifact</Th>
                                <Th>Version</Th>
                                <Th>Status</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {(result.tools || []).map((tool, index) => (
                                <Tr key={`${tool.name}-${index}`} data-testid={`mcp-import-tool-${tool.name}`}>
                                    <Td dataLabel="Tool">{tool.name}</Td>
                                    <Td dataLabel="Group">{tool.groupId}</Td>
                                    <Td dataLabel="Artifact">{tool.artifactId}</Td>
                                    <Td dataLabel="Version">{tool.version || "-"}</Td>
                                    <Td dataLabel="Status">
                                        <Label color={STATUS_COLORS[tool.status || ""] || "grey"}>{tool.status}</Label>
                                    </Td>
                                </Tr>
                            ))}
                        </Tbody>
                    </Table>
                    {rejected.map(tool => (
                        <div key={`rejected-${tool.name}`} data-testid={`mcp-import-rejected-${tool.name}`}
                            style={{ marginTop: "12px" }}>
                            <Title headingLevel="h4" size="md">Rejected: {tool.name}</Title>
                            <CompatibilityReport causes={(tool.causes || []).map(toViolationCause)} />
                        </div>
                    ))}
                    <Content component="p" data-testid="mcp-import-summary" style={{ marginTop: "12px" }}>
                        {summary(result)}
                    </Content>
                </div>
            )}
        </Modal>
    );
};
