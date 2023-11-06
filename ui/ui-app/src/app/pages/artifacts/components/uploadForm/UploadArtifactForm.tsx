import { FunctionComponent, useEffect, useState } from "react";
import "./UploadArtifactForm.css";
import { If, UrlUpload } from "@app/components";
import {
    FileUpload,
    Form,
    FormGroup,
    FormHelperText,
    HelperText,
    HelperTextItem,
    Tab,
    Tabs,
    TabTitleText,
    TextInput
} from "@patternfly/react-core";
import { ExclamationCircleIcon } from "@patternfly/react-icons";
import { CreateArtifactData } from "@services/groups";
import { ArtifactTypes } from "@models/artifactTypes.model.ts";
import { ObjectSelect } from "@app/components/common/ObjectSelect.tsx";

/**
 * Properties
 */
export type UploadArtifactFormProps = {
    onValid: (valid: boolean) => void;
    onChange: (data: CreateArtifactData) => void;
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
 * Models the toolbar for the Artifacts page.
 */
export const UploadArtifactForm: FunctionComponent<UploadArtifactFormProps> = (props: UploadArtifactFormProps) => {
    const [content, setContent] = useState<string>();
    const [contentIsLoading, setContentIsLoading] = useState(false);
    const [id, setId] = useState("");
    const [group, setGroup] = useState("");
    const [type, setType] = useState("");
    const [tabKey, setTabKey] = useState(0);
    const [formValid, setFormValid] = useState(false);
    const [idValid, setIdValid] = useState(true);
    const [groupValid, setGroupValid] = useState(true);
    const [artifactTypes, setArtifactTypes] = useState<any[]>([]);
    const [artifactTypeOptions, setArtifactTypeOptions] = useState<ArtifactTypeItem[]>([]);
    const [selectedType, setSelectedType] = useState<ArtifactTypeItem>(DEFAULT_ARTIFACT_TYPE);

    const onFileTextChange = (_event: any, value: string | undefined): void => {
        setContent(value);
    };

    const onFileClear = (): void => {
        onFileTextChange(null, "");
    };

    const onFileReadStarted = (): void => {
        setContentIsLoading(true);
    };

    const onFileReadFinished = (): void => {
        setContentIsLoading(false);
    };

    const isFormValid = (data: CreateArtifactData): boolean => {
        return !!data.content && isIdValid(data.id) && isIdValid(data.groupId);
    };

    const isIdValid = (id: string|null): boolean => {
        if (!id) {
            //id is optional, server can generate it
            return true;
        } else {
            // character % breaks the ui
            const isAscii = (str: string) => {
                for (let i = 0; i < str.length; i++){
                    if(str.charCodeAt(i)>127){
                        return false;
                    }
                }
                return true;
            };
            return id.indexOf("%") == -1 && isAscii(id);
        }
    };

    const currentData = (): CreateArtifactData => {
        return {
            content: content,
            groupId: group,
            id: id,
            type: type
        };
    };

    const fireOnChange = (data: CreateArtifactData): void => {
        if (props.onChange) {
            props.onChange(data);
        }
    };

    const fireOnFormValid = (): void => {
        if (props.onValid) {
            props.onValid(formValid);
        }
    };

    const idValidated = (): any => {
        if (idValid) {
            if (!id) {
                return "default";
            }
            return "success";
        } else {
            return "error";
        }
    };

    const groupValidated = (): any => {
        if (groupValid) {
            if (!group) {
                return "default";
            }
            return "success";
        } else {
            return "error";
        }
    };

    useEffect(() => {
        ArtifactTypes.allTypesWithLabels().then(setArtifactTypes);
    }, []);

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
        setType(selectedType.value as string);
    }, [selectedType]);

    useEffect(() => {
        const data: CreateArtifactData = currentData();

        setIdValid(isIdValid(id));
        setGroupValid(isIdValid(group));
        setFormValid(isFormValid(data));
        fireOnChange(data);
    }, [type, content, id, group]);

    useEffect(() => {
        fireOnFormValid();
    }, [formValid]);

    return (
        <Form>
            <FormGroup
                label="Group & ID"
                fieldId="form-id"
            >
                <div className="group-and-id">
                    <TextInput
                        className="group"
                        isRequired={false}
                        type="text"
                        id="form-group"
                        data-testid="form-group"
                        name="form-group"
                        aria-describedby="form-group-helper"
                        value={group}
                        placeholder="Group"
                        onChange={(_evt, value) => setGroup(value)}
                        validated={groupValidated()}
                    />
                    <span className="separator">/</span>
                    <TextInput
                        className="artifact-id"
                        isRequired={false}
                        type="text"
                        id="form-id"
                        data-testid="form-id"
                        name="form-id"
                        aria-describedby="form-id-helper"
                        value={id}
                        placeholder="ID of the artifact"
                        onChange={(_evt, value) => setId(value)}
                        validated={idValidated()}
                    />
                </div>
                <If condition={!idValid || !groupValid}>
                    <FormHelperText>
                        <HelperText>
                            <HelperTextItem variant="error" icon={ <ExclamationCircleIcon /> }>Character % and non ASCII characters are not allowed</HelperTextItem>
                        </HelperText>
                    </FormHelperText>
                </If>
                <FormHelperText>
                    <HelperText>
                        <HelperTextItem>(Optional) Group and Artifact ID are optional.  If Artifact ID is left blank, the server will generate one for you.</HelperTextItem>
                    </HelperText>
                </FormHelperText>
            </FormGroup>
            <FormGroup
                label="Type"
                fieldId="form-type"
                isRequired={true}
            >
                <div>
                    <ObjectSelect value={selectedType}
                        items={artifactTypeOptions}
                        onSelect={setSelectedType}
                        itemIsDivider={(item) => item.isDivider}
                        itemToString={(item) => item.label} />
                </div>
            </FormGroup>
            <FormGroup
                label="Artifact"
                isRequired={true}
                fieldId="form-artifact"
            >
                <Tabs
                    className="create-tabs"
                    style={{ marginBottom: "8px" }}
                    activeKey={tabKey}
                    onSelect={(_event, eventKey) => {
                        setTabKey(eventKey as number);
                        onFileTextChange( null, undefined);
                        _event.preventDefault();
                        _event.stopPropagation();
                    }}
                    isBox={false}
                    role="region"
                >
                    <Tab eventKey={0} data-testid="tab-from-file" title={<TabTitleText>From file</TabTitleText>} aria-label="Default content - from file">
                        <FileUpload
                            id="artifact-content"
                            data-testid="form-upload"
                            type="text"
                            value={content!}
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
                    <Tab eventKey={1} data-testid="tab-from-url" title={<TabTitleText>From URL</TabTitleText>}>
                        <UrlUpload
                            id="artifact-content-url"
                            urlPlaceholder="Enter a valid and accessible URL"
                            onChange={(value) => {
                                onFileTextChange(null, value);
                            }}
                        />
                    </Tab>
                </Tabs>
            </FormGroup>
        </Form>
    );

};
