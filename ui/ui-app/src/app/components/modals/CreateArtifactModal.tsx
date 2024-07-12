import { FunctionComponent, useEffect, useState } from "react";
import "./CreateArtifactModal.css";
import {
    FileUpload,
    Form,
    FormGroup,
    FormHelperText,
    Grid,
    GridItem,
    HelperText,
    HelperTextItem,
    Modal,
    Tab,
    Tabs,
    TabTitleText,
    TextArea,
    TextInput,
    Wizard,
    WizardStep
} from "@patternfly/react-core";
import { CreateArtifact } from "@sdk/lib/generated-client/models";
import { If, ObjectSelect, UrlUpload } from "@apicurio/common-ui-components";
import { ExclamationCircleIcon } from "@patternfly/react-icons";
import { UrlService, useUrlService } from "@services/useUrlService.ts";
import { ArtifactTypesService, useArtifactTypesService } from "@services/useArtifactTypesService.ts";
import { ArtifactLabel, LabelsFormGroup } from "@app/components";
import { listToLabels } from "@utils/labels.utils.ts";
import { detectContentType } from "@utils/content.utils.ts";
import { WizardFooterProps } from "@patternfly/react-core/src/components/Wizard/WizardFooter.tsx";


export type ValidType = "default" | "success" | "error";

export type Validities = {
    groupId?: ValidType;
    artifactId?: ValidType;
    artifactType?: ValidType;
    artifactName?: ValidType;
    artifactDescription?: ValidType;
    versionNumber?: ValidType;
    versionName?: ValidType;
    versionDescription?: ValidType;
};

const checkIdValid = (id: string | undefined): boolean => {
    if (!id) {
        //id is optional, server can generate it
        return true;
    } else {
        // character % breaks the ui
        const isAscii = (str: string) => {
            for (let i = 0; i < str.length; i++){
                if (str.charCodeAt(i) > 127){
                    return false;
                }
            }
            return true;
        };
        return id.indexOf("%") == -1 && isAscii(id);
    }
};

const validateField = (value: string | undefined): ValidType => {
    const isValid: boolean = checkIdValid(value);
    if (!isValid) {
        return "error";
    }
    if (value === undefined || value === null || value === "") {
        return "default";
    }
    return "success";
};


const EMPTY_FORM_DATA: CreateArtifact = {
};


type ArtifactTypeItem = {
    value?: string;
    label?: string;
    isDivider?: boolean;
};

const DEFAULT_ARTIFACT_TYPE: ArtifactTypeItem = {
    value: "",
    label: "Auto-Detect"
};
const DIVIDER: ArtifactTypeItem = {
    isDivider: true
};

/**
 * Properties
 */
export type CreateArtifactModalProps = {
    groupId?: string;
    isOpen: boolean;
    onClose: () => void;
    onCreate: (groupId: string | undefined, data: CreateArtifact) => void;
};

/**
 * Models the Create Artifact modal dialog.
 */
export const CreateArtifactModal: FunctionComponent<CreateArtifactModalProps> = (props: CreateArtifactModalProps) => {
    const [validities, setValidities] = useState<Validities>({});
    const [groupId, setGroupId] = useState<string | undefined>();
    const [data, setData] = useState<CreateArtifact>(EMPTY_FORM_DATA);
    const [artifactTypes, setArtifactTypes] = useState<any[]>([]);
    const [artifactTypeOptions, setArtifactTypeOptions] = useState<ArtifactTypeItem[]>([]);
    const [selectedType, setSelectedType] = useState<ArtifactTypeItem>(DEFAULT_ARTIFACT_TYPE);
    const [artifactLabels, setArtifactLabels] = useState<ArtifactLabel[]>([]);
    const [contentTabKey, setContentTabKey] = useState(0);
    const [contentIsLoading, setContentIsLoading] = useState(false);
    const [versionLabels, setVersionLabels] = useState<ArtifactLabel[]>([]);

    const urlService: UrlService = useUrlService();
    const atService: ArtifactTypesService = useArtifactTypesService();

    const setArtifactId = (newArtifactId: string): void => {
        setData({
            ...data,
            artifactId: newArtifactId
        });
    };

    const setArtifactType = (newArtifactType: string): void => {
        setData({
            ...data,
            artifactType: newArtifactType
        });
    };

    const setArtifactName = (newName: string): void => {
        setData({
            ...data,
            name: newName
        });
    };

    const setArtifactDescription = (newDescription: string): void => {
        setData({
            ...data,
            description: newDescription
        });
    };

    const _setArtifactLabels = (newLabels: ArtifactLabel[]): void => {
        setArtifactLabels(newLabels);
        setData({
            ...data,
            labels: listToLabels(newLabels)
        });
    };

    const onFileTextChange = (_event: any, value: string | undefined): void => {
        setData({
            ...data,
            firstVersion: {
                ...data.firstVersion,
                content: {
                    content: value,
                    contentType: detectContentType(data.artifactType, value)
                }
            }
        });
    };

    const onFileClear = (): void => {
        onFileTextChange(null, undefined);
    };

    const onFileReadStarted = (): void => {
        setContentIsLoading(true);
    };

    const onFileReadFinished = (): void => {
        setContentIsLoading(false);
    };

    const setVersionNumber = (newVersion: string): void => {
        setData({
            ...data,
            firstVersion: {
                ...data.firstVersion,
                version: newVersion
            }
        });
    };

    const setVersionName = (newName: string): void => {
        setData({
            ...data,
            firstVersion: {
                ...data.firstVersion,
                name: newName
            }
        });
    };

    const setVersionDescription = (newDescription: string): void => {
        setData({
            ...data,
            firstVersion: {
                ...data.firstVersion,
                description: newDescription
            }
        });
    };

    const _setVersionLabels = (newLabels: ArtifactLabel[]): void => {
        setVersionLabels(newLabels);
        setData({
            ...data,
            firstVersion: {
                ...data.firstVersion,
                labels: listToLabels(newLabels)
            }
        });
    };

    const fireCloseEvent = (): void => {
        props.onClose();
    };

    const fireCreateEvent = (): void => {
        console.debug("---");
        console.debug(data);
        props.onCreate(groupId, data);
    };

    useEffect(() => {
        atService.allTypesWithLabels().then(setArtifactTypes);
    }, []);

    useEffect(() => {
        if (props.isOpen) {
            setData(EMPTY_FORM_DATA);
            if (props.groupId) {
                setGroupId(props.groupId);
            } else {
                setGroupId("");
            }
        }
    }, [props.isOpen]);

    useEffect(() => {
        const items: ArtifactTypeItem[] = artifactTypes.map(item => ({
            value: item.id,
            label: item.label
        }));
        setArtifactTypeOptions([
            DEFAULT_ARTIFACT_TYPE,
            DIVIDER,
            ...items
        ]);
    }, [artifactTypes]);

    useEffect(() => {
        setArtifactType(selectedType.value as string);
    }, [selectedType]);

    useEffect(() => {
        setValidities({
            groupId: validateField(groupId),
            artifactId: validateField(data.artifactId)
        });
    }, [groupId, data]);

    const hasArtifactType: boolean = data.artifactType !== undefined && data.artifactType !== null && data.artifactType !== "";
    const hasVersionContent: boolean = data.firstVersion?.content?.content !== undefined;
    const versionContentTitle: string = hasArtifactType ? "Version Content (optional)" : "Version Content";

    const isGroupIdValid: boolean = validities.groupId !== "error";
    const isArtifactIdValid: boolean = validities.artifactId !== "error";
    const isCoordinates1Valid: boolean = isGroupIdValid && isArtifactIdValid;
    const isValid: boolean = isCoordinates1Valid;

    const coordinatesStepFooter: Partial<WizardFooterProps> = {
        onClose: props.onClose,
        isNextDisabled: !isCoordinates1Valid
    };
    const artifactMetadataStepFooter: Partial<WizardFooterProps> = {
        onClose: props.onClose
    };
    const versionContentStepFooter: Partial<WizardFooterProps> = {
        onClose: props.onClose
    };
    if (!hasVersionContent) {
        versionContentStepFooter.onNext = fireCreateEvent;
        versionContentStepFooter.nextButtonText = "Create";
        versionContentStepFooter.isNextDisabled = !isValid;
    }
    const versionMetadataStepFooter: Partial<WizardFooterProps> = {
        nextButtonText: "Create",
        isNextDisabled: !isValid,
        onNext: fireCreateEvent,
        onClose: props.onClose,
    };

    return (
        <Modal
            title="Create Artifact"
            variant="large"
            isOpen={props.isOpen}
            onClose={fireCloseEvent}
            className="create-artifact-modal pf-m-redhat-font"
        >
            <Wizard title="Create Artifact" height={600}>
                <WizardStep name="Artifact Coordinates" id="coordinates-step" key={0} footer={coordinatesStepFooter}>
                    <Form>
                        <FormGroup label="Group Id" fieldId="group-id">
                            <TextInput
                                className="group"
                                isRequired={false}
                                type="text"
                                id="form-group"
                                data-testid="create-artifact-form-group"
                                name="form-group"
                                aria-describedby="form-group-helper"
                                value={groupId as string || ""}
                                placeholder="Group Id (optional) will use 'default' if left empty"
                                isDisabled={props.groupId !== undefined}
                                onChange={(_evt, value) => setGroupId(value)}
                                validated={validities.groupId}
                            />
                            <If condition={!isGroupIdValid}>
                                <FormHelperText>
                                    <HelperText>
                                        <HelperTextItem variant="error" icon={ <ExclamationCircleIcon /> }>Character % and non ASCII characters are not allowed</HelperTextItem>
                                    </HelperText>
                                </FormHelperText>
                            </If>
                        </FormGroup>
                        <FormGroup label="Artifact Id" fieldId="artifact-id">
                            <TextInput
                                className="artifact-id"
                                isRequired={false}
                                type="text"
                                id="form-id"
                                data-testid="create-artifact-form-id"
                                name="form-id"
                                aria-describedby="form-id-helper"
                                value={data.artifactId || ""}
                                placeholder="Artifact Id (optional) unique ID will be generated if empty"
                                onChange={(_evt, value) => setArtifactId(value)}
                                validated={validities.artifactId}
                            />
                            <If condition={!isArtifactIdValid}>
                                <FormHelperText>
                                    <HelperText>
                                        <HelperTextItem variant="error" icon={ <ExclamationCircleIcon /> }>Character % and non ASCII characters are not allowed</HelperTextItem>
                                    </HelperText>
                                </FormHelperText>
                            </If>
                        </FormGroup>
                        <FormGroup label="Type" fieldId="form-artifact-type" isRequired={true}>
                            <ObjectSelect
                                value={selectedType}
                                items={artifactTypeOptions}
                                testId="create-artifact-form-type-select"
                                onSelect={setSelectedType}
                                itemIsDivider={(item) => item.isDivider}
                                itemToTestId={(item) => `create-artifact-form-${item.value}`}
                                itemToString={(item) => item.label}
                                appendTo="document"
                            />
                            <FormHelperText>
                                <HelperText>
                                    <HelperTextItem>Note: If "Auto-Detect" is chosen, Version Content will be required.</HelperTextItem>
                                </HelperText>
                            </FormHelperText>
                        </FormGroup>
                    </Form>
                </WizardStep>
                <WizardStep name="Artifact Metadata" id="artifact-metadata-step" key={1} footer={artifactMetadataStepFooter}>
                    <Form>
                        <Grid hasGutter md={6}>
                            <GridItem span={12}>
                                <FormGroup label="Name" fieldId="artifact-name">
                                    <TextInput
                                        isRequired={false}
                                        type="text"
                                        id="artifact-name"
                                        data-testid="artifact-metadata-name"
                                        name="artifact-name"
                                        aria-describedby="artifact-name-helper"
                                        value={data.name || ""}
                                        placeholder="Name of the artifact (optional)"
                                        onChange={(_evt, value) => setArtifactName(value)}
                                    />
                                </FormGroup>
                            </GridItem>

                            <GridItem span={12}>
                                <FormGroup label="Description" fieldId="artifact-description">
                                    <TextArea
                                        isRequired={false}
                                        id="artifact-description"
                                        data-testid="artifact-metadata-description"
                                        name="artifact-description"
                                        aria-describedby="artifact-description-helper"
                                        value={data.description || ""}
                                        placeholder="Description of the artifact (optional)"
                                        style={{ height: "100px" }}
                                        onChange={(_evt, value) => setArtifactDescription(value)}
                                    />
                                </FormGroup>
                            </GridItem>
                            <LabelsFormGroup labels={artifactLabels} onChange={_setArtifactLabels} />
                        </Grid>
                    </Form>
                </WizardStep>
                <WizardStep
                    name={versionContentTitle}
                    id="version-content-step"
                    key={2}
                    footer={versionContentStepFooter}
                >
                    <Form>
                        <FormGroup label="Version Number" fieldId="version-number">
                            <TextInput
                                className="version"
                                isRequired={false}
                                type="text"
                                id="form-version"
                                data-testid="create-artifact-form-version"
                                name="form-version"
                                aria-describedby="form-version-helper"
                                value={data.firstVersion?.version || ""}
                                placeholder="1.0.0 (optional) will be generated if left blank"
                                onChange={(_evt, value) => setVersionNumber(value)}
                                // validated={groupValidated()}
                            />
                        </FormGroup>
                        <FormGroup label="Content" isRequired={false} fieldId="form-content">
                            <Tabs
                                className="content-tabs"
                                style={{ marginBottom: "8px" }}
                                activeKey={contentTabKey}
                                onSelect={(_event, eventKey) => {
                                    setContentTabKey(eventKey as number);
                                    onFileTextChange( null, undefined);
                                    _event.preventDefault();
                                    _event.stopPropagation();
                                }}
                                isBox={false}
                                role="region"
                            >
                                <Tab
                                    eventKey={0}
                                    data-testid="create-artifact-from-file"
                                    title={<TabTitleText>From file</TabTitleText>}
                                    aria-label="Upload from file"
                                >
                                    <FileUpload
                                        id="artifact-content"
                                        data-testid="create-artifact-form-file-upload"
                                        type="text"
                                        value={data.firstVersion?.content?.content || ""}
                                        isRequired={false}
                                        allowEditingUploadedText={true}
                                        onDataChange={onFileTextChange}
                                        onTextChange={onFileTextChange}
                                        onClearClick={onFileClear}
                                        onReadStarted={onFileReadStarted}
                                        onReadFinished={onFileReadFinished}
                                        isLoading={contentIsLoading}
                                    />
                                </Tab>
                                <Tab
                                    eventKey={1}
                                    data-testid="create-artifact-from-url"
                                    title={<TabTitleText>From URL</TabTitleText>}
                                    aria-label="Upload from URL"
                                >
                                    <UrlUpload
                                        id="artifact-content-url"
                                        urlPlaceholder="Enter a valid and accessible URL"
                                        testId="create-artifact-form-url-upload"
                                        onChange={(value) => {
                                            onFileTextChange(null, value);
                                        }}
                                        onUrlFetch={(url) => urlService.fetchUrlContent(url)}
                                    />
                                </Tab>
                            </Tabs>
                            <FormHelperText>
                                <HelperText>
                                    <HelperTextItem>Note: If version content is not provided, an empty artifact (no versions) will be created.</HelperTextItem>
                                </HelperText>
                            </FormHelperText>
                        </FormGroup>
                    </Form>
                </WizardStep>
                <WizardStep
                    name="Version Metadata (optional)"
                    id="version-metadata-step"
                    key={3}
                    isDisabled={!hasVersionContent}
                    footer={versionMetadataStepFooter}
                >
                    <Form>
                        <Grid hasGutter md={6}>
                            <GridItem span={12}>
                                <FormGroup label="Name" fieldId="version-name">
                                    <TextInput
                                        isRequired={false}
                                        type="text"
                                        id="version-name"
                                        data-testid="version-metadata-name"
                                        name="version-name"
                                        aria-describedby="version-name-helper"
                                        value={data.firstVersion?.name || ""}
                                        placeholder="Name of the version (optional)"
                                        onChange={(_evt, value) => setVersionName(value)}
                                    />
                                </FormGroup>
                            </GridItem>

                            <GridItem span={12}>
                                <FormGroup label="Description" fieldId="version-description">
                                    <TextArea
                                        isRequired={false}
                                        id="version-description"
                                        data-testid="version-metadata-description"
                                        name="version-description"
                                        aria-describedby="version-description-helper"
                                        value={data.firstVersion?.description || ""}
                                        placeholder="Description of the version (optional)"
                                        style={{ height: "100px" }}
                                        onChange={(_evt, value) => setVersionDescription(value)}
                                    />
                                </FormGroup>
                            </GridItem>
                            <LabelsFormGroup labels={versionLabels} onChange={_setVersionLabels} />
                        </Grid>
                    </Form>
                </WizardStep>
            </Wizard>
        </Modal>
    );

};
