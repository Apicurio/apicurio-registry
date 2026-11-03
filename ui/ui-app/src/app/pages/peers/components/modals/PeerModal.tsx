import { FunctionComponent, useEffect, useState } from "react";
import {
    Alert,
    Button,
    Form,
    FormGroup,
    FormHelperText,
    HelperText,
    HelperTextItem,
    Switch,
    TextArea,
    TextInput
} from "@patternfly/react-core";
import {
    Modal
} from "@patternfly/react-core/deprecated";
import "./PeerModal.css";
import { NewPeer, Peer } from "@sdk/lib/generated-client/models";
import {
    peerErrorMessage,
    validateCredentialSecretRef,
    validatePeerDescription,
    validatePeerId,
    validatePeerName,
    validatePeerUrl
} from "@app/pages/peers/peerValidation.ts";

/**
 * Properties
 */
export type PeerModalProps = {
    isOpen: boolean;
    // The peer being edited, or undefined when a new peer is being added.
    peer?: Peer;
    onClose: () => void;
    // Rejects with the server's error when the peer cannot be saved, so the modal can show it.
    onSave: (peer: NewPeer, isEdit: boolean) => Promise<void>;
};

type Field = "peerId" | "url" | "name" | "description" | "credentialSecretRef";

/**
 * Models the modal dialog for adding or editing a remote registry (a peer).
 */
export const PeerModal: FunctionComponent<PeerModalProps> = (props: PeerModalProps) => {
    const [peerId, setPeerId] = useState("");
    const [url, setUrl] = useState("");
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [credentialSecretRef, setCredentialSecretRef] = useState("");
    const [enabled, setEnabled] = useState(true);
    const [touched, setTouched] = useState<Field[]>([]);
    const [isSaving, setIsSaving] = useState(false);
    const [saveError, setSaveError] = useState<string>();

    const isEdit: boolean = props.peer !== undefined;

    useEffect(() => {
        if (props.isOpen) {
            setPeerId(props.peer?.peerId || "");
            setUrl(props.peer?.url || "");
            setName(props.peer?.name || "");
            setDescription(props.peer?.description || "");
            setCredentialSecretRef(props.peer?.credentialSecretRef || "");
            setEnabled(props.peer?.enabled ?? true);
            setTouched([]);
            setIsSaving(false);
            setSaveError(undefined);
        }
    }, [props.isOpen, props.peer]);

    const errors: Record<Field, string | undefined> = {
        peerId: isEdit ? undefined : validatePeerId(peerId),
        url: validatePeerUrl(url),
        name: validatePeerName(name),
        description: validatePeerDescription(description),
        credentialSecretRef: validateCredentialSecretRef(credentialSecretRef)
    };
    const isValid: boolean = Object.values(errors).every(error => error === undefined);

    const touch = (field: Field): void => {
        setTouched(current => current.includes(field) ? current : [...current, field]);
    };

    const errorFor = (field: Field): string | undefined => {
        return touched.includes(field) ? errors[field] : undefined;
    };

    const validatedFor = (field: Field): "error" | "default" => {
        return errorFor(field) ? "error" : "default";
    };

    const save = (): void => {
        if (!isValid) {
            setTouched(["peerId", "url", "name", "description", "credentialSecretRef"]);
            return;
        }
        const peer: NewPeer = {
            peerId: peerId.trim(),
            url: url.trim(),
            name: name.trim() || undefined,
            description: description.trim() || undefined,
            credentialSecretRef: credentialSecretRef.trim() || undefined,
            enabled
        };
        setIsSaving(true);
        setSaveError(undefined);
        props.onSave(peer, isEdit).catch(error => {
            setSaveError(peerErrorMessage(error, "The remote registry could not be saved."));
            setIsSaving(false);
        });
    };

    const onKey = (event: any): void => {
        if (event.key === "Enter") {
            event.stopPropagation();
            event.preventDefault();
        }
    };

    const fieldError = (field: Field) => {
        const message: string | undefined = errorFor(field);
        return message ? (
            <FormHelperText>
                <HelperText>
                    <HelperTextItem variant="error">{message}</HelperTextItem>
                </HelperText>
            </FormHelperText>
        ) : null;
    };

    return (
        <Modal
            title={isEdit ? "Edit remote registry" : "Add remote registry"}
            description="A remote registry is another Apicurio Registry whose public agents are included in federated agent search."
            variant="medium"
            isOpen={props.isOpen}
            onClose={props.onClose}
            className="peer-modal pf-m-redhat-font"
            actions={[
                <Button key="save" variant="primary" data-testid="modal-btn-save" onClick={save}
                    isDisabled={isSaving} isLoading={isSaving}>{isEdit ? "Save" : "Add"}</Button>,
                <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={props.onClose}
                    isDisabled={isSaving}>Cancel</Button>
            ]}
        >
            <Form onKeyDown={onKey}>
                {saveError ? <Alert variant="danger" isInline title={saveError} data-testid="peer-save-error" /> : null}
                <FormGroup label="Peer ID" isRequired={true} fieldId="peer-id">
                    <TextInput id="peer-id" data-testid="peer-id" type="text" value={peerId}
                        isDisabled={isEdit} validated={validatedFor("peerId")}
                        onChange={(_event, value) => setPeerId(value)} onBlur={() => touch("peerId")} />
                    {isEdit ? null : (
                        <FormHelperText>
                            <HelperText>
                                <HelperTextItem>Lowercase letters, digits, '.', '_' and '-'. It cannot be changed later.</HelperTextItem>
                            </HelperText>
                        </FormHelperText>
                    )}
                    {fieldError("peerId")}
                </FormGroup>
                <FormGroup label="URL" isRequired={true} fieldId="peer-url">
                    <TextInput id="peer-url" data-testid="peer-url" type="url" value={url}
                        placeholder="https://registry.example.com" validated={validatedFor("url")}
                        onChange={(_event, value) => setUrl(value)} onBlur={() => touch("url")} />
                    {fieldError("url")}
                </FormGroup>
                <FormGroup label="Name" fieldId="peer-name">
                    <TextInput id="peer-name" data-testid="peer-name" type="text" value={name}
                        validated={validatedFor("name")}
                        onChange={(_event, value) => setName(value)} onBlur={() => touch("name")} />
                    {fieldError("name")}
                </FormGroup>
                <FormGroup label="Description" fieldId="peer-description">
                    <TextArea id="peer-description" data-testid="peer-description" value={description}
                        validated={validatedFor("description")} resizeOrientation="vertical"
                        onChange={(_event, value) => setDescription(value)} onBlur={() => touch("description")} />
                    {fieldError("description")}
                </FormGroup>
                <FormGroup label="Credential reference" fieldId="peer-credential-ref">
                    <TextInput id="peer-credential-ref" data-testid="peer-credential-ref" type="text"
                        value={credentialSecretRef} validated={validatedFor("credentialSecretRef")}
                        onChange={(_event, value) => setCredentialSecretRef(value)}
                        onBlur={() => touch("credentialSecretRef")} />
                    <FormHelperText>
                        <HelperText>
                            <HelperTextItem>
                                Optional. The name of a key in the registry's peer credentials directory.
                                Only the name is stored; the credential itself is never shown here.
                            </HelperTextItem>
                        </HelperText>
                    </FormHelperText>
                    {fieldError("credentialSecretRef")}
                </FormGroup>
                <FormGroup fieldId="peer-enabled">
                    <Switch id="peer-enabled" data-testid="peer-enabled" label="Enabled"
                        isChecked={enabled} onChange={(_event, checked) => setEnabled(checked)} />
                </FormGroup>
            </Form>
        </Modal>
    );
};
