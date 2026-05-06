import React, { FunctionComponent } from "react";
import "./ReferencesFormGroup.css";
import { Button, FormGroup, Grid, GridItem, Spinner, TextInput } from "@patternfly/react-core";
import { MinusCircleIcon, PlusCircleIcon, SearchIcon } from "@patternfly/react-icons";
import { ArtifactReference } from "@sdk/lib/generated-client/models";

export type ArtifactReferenceFormItem = {
    groupId: string;
    artifactId: string;
    version: string;
    name: string;
};

type ValidType = "default" | "success" | "error";

const validateRefField = (value: string): ValidType => {
    if (value === "") {
        return "error";
    }
    return "success";
};

/**
 * Returns true if all reference rows have all four fields populated.
 * Returns true if there are no references (empty list is valid).
 */
export const isReferencesValid = (items: ArtifactReferenceFormItem[]): boolean => {
    return items.every(item =>
        item.name !== "" && item.groupId !== "" && item.artifactId !== "" && item.version !== ""
    );
};

/**
 * Converts form items to SDK ArtifactReference objects.
 * Returns undefined if no references exist (avoids sending empty arrays).
 */
export const formItemsToReferences = (items: ArtifactReferenceFormItem[]): ArtifactReference[] | undefined => {
    if (items.length === 0) {
        return undefined;
    }
    const refs: ArtifactReference[] = items.map(item => ({
        groupId: item.groupId,
        artifactId: item.artifactId,
        version: item.version,
        name: item.name,
    }));
    return refs;
};


/**
 * Properties
 */
export type ReferencesFormGroupProps = {
    references: ArtifactReferenceFormItem[];
    onChange: (references: ArtifactReferenceFormItem[]) => void;
    onDetect?: () => void;
    isDetecting?: boolean;
};

/**
 * A reusable form group for managing a list of artifact references.
 * Follows the same pattern as LabelsFormGroup.
 */
export const ReferencesFormGroup: FunctionComponent<ReferencesFormGroupProps> = (
    { references, onChange, onDetect, isDetecting }: ReferencesFormGroupProps
) => {

    const addReference = (): void => {
        const newRefs: ArtifactReferenceFormItem[] = [...references, {
            groupId: "",
            artifactId: "",
            version: "",
            name: ""
        }];
        onChange(newRefs);
    };

    const removeReference = (refToRemove: ArtifactReferenceFormItem): void => {
        const newRefs: ArtifactReferenceFormItem[] = references.filter(ref => ref !== refToRemove);
        onChange(newRefs);
    };

    return (
        <React.Fragment>
            <Grid hasGutter>
                <GridItem span={12}>
                    <FormGroup label="Artifact References"></FormGroup>
                </GridItem>
                {
                    references.map((ref, idx) => (
                        <React.Fragment key={idx}>
                            <GridItem span={4}>
                                <FormGroup
                                    fieldId={`form-ref-name-${idx}`}
                                    label={idx === 0 ? "Reference Name" : ""}
                                >
                                    <TextInput
                                        type="text"
                                        id={`form-ref-name-${idx}`}
                                        data-testid={`references-form-name-${idx}`}
                                        name={`form-ref-name-${idx}`}
                                        value={ref.name}
                                        validated={validateRefField(ref.name)}
                                        onChange={(_event, newVal) => {
                                            ref.name = newVal;
                                            onChange([...references]);
                                        }}
                                    />
                                </FormGroup>
                            </GridItem>
                            <GridItem span={3}>
                                <FormGroup
                                    fieldId={`form-ref-group-${idx}`}
                                    label={idx === 0 ? "Group Id" : ""}
                                >
                                    <TextInput
                                        type="text"
                                        id={`form-ref-group-${idx}`}
                                        data-testid={`references-form-group-id-${idx}`}
                                        name={`form-ref-group-${idx}`}
                                        value={ref.groupId}
                                        validated={validateRefField(ref.groupId)}
                                        onChange={(_event, newVal) => {
                                            ref.groupId = newVal;
                                            onChange([...references]);
                                        }}
                                    />
                                </FormGroup>
                            </GridItem>
                            <GridItem span={3}>
                                <FormGroup
                                    fieldId={`form-ref-artifact-${idx}`}
                                    label={idx === 0 ? "Artifact Id" : ""}
                                >
                                    <TextInput
                                        type="text"
                                        id={`form-ref-artifact-${idx}`}
                                        data-testid={`references-form-artifact-id-${idx}`}
                                        name={`form-ref-artifact-${idx}`}
                                        value={ref.artifactId}
                                        validated={validateRefField(ref.artifactId)}
                                        onChange={(_event, newVal) => {
                                            ref.artifactId = newVal;
                                            onChange([...references]);
                                        }}
                                    />
                                </FormGroup>
                            </GridItem>
                            <GridItem span={2}>
                                <FormGroup
                                    fieldId={`form-ref-version-${idx}`}
                                    label={idx === 0 ? "Version" : ""}
                                >
                                    <div className="ref-field-group">
                                        <TextInput
                                            type="text"
                                            id={`form-ref-version-${idx}`}
                                            data-testid={`references-form-version-${idx}`}
                                            name={`form-ref-version-${idx}`}
                                            value={ref.version}
                                            validated={validateRefField(ref.version)}
                                            onChange={(_event, newVal) => {
                                                ref.version = newVal;
                                                onChange([...references]);
                                            }}
                                        />
                                        <Button
                                            key={`remove-ref-${idx}`}
                                            variant="link"
                                            icon={<MinusCircleIcon />}
                                            iconPosition="right"
                                            className="pf-m-plain"
                                            data-testid={`references-form-remove-${idx}`}
                                            onClick={() => {
                                                removeReference(ref);
                                            }}
                                        />
                                    </div>
                                </FormGroup>
                            </GridItem>
                        </React.Fragment>
                    ))
                }
                <GridItem span={12}>
                    <Button
                        variant="link"
                        icon={<PlusCircleIcon />}
                        className="add-reference-button"
                        data-testid="references-form-add"
                        onClick={() => addReference()}
                    >
                        Add reference
                    </Button>
                    {onDetect && (
                        <Button
                            variant="link"
                            icon={isDetecting ? <Spinner size="sm" /> : <SearchIcon />}
                            className="detect-references-button"
                            data-testid="references-form-detect"
                            onClick={onDetect}
                            isDisabled={isDetecting}
                        >
                            {isDetecting ? "Detecting..." : "Detect references"}
                        </Button>
                    )}
                </GridItem>
            </Grid>
        </React.Fragment>
    );
};
