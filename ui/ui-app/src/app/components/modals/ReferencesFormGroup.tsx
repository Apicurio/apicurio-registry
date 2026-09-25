import React, { FunctionComponent, useEffect, useState } from "react";
import "./ReferencesFormGroup.css";
import { Button, FormGroup, Grid, GridItem, Spinner, TextInput } from "@patternfly/react-core";
import { MinusCircleIcon, PlusCircleIcon, SearchIcon } from "@patternfly/react-icons";
import { ArtifactReference } from "@sdk/lib/generated-client/models";
import { SelectTypeaheadCreatable } from "./TypeAheadComponent";
import { FilterBy, useSearchService } from "@services/useSearchService";
import { SortOrder } from "@models/SortOrder";
import { GroupsSortBy } from "@models/GroupsSortBy";
import { VersionsSortBy } from "@models/VersionsSortBy";
import { ArtifactsSortBy } from "@models/ArtifactsSortBy";

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

    /**
     * Searches for groups using @param searchTerm 
     * @returns an array of groupIds
     */
    const searchService = useSearchService();
    const [groupSearchTerm, setGroupSearchTerm] = useState<string>();
    const [artifactSearchTerm, setArtifactSearchTerm] = useState<string>();
    const [versionSearchTerm, setVersionSearchTerm] = useState<string>();
    const [groupOptions, setGroupOptions] = useState<string[]>([]);
    const [artifactOptions, setArtifactOptions] = useState<string[]>([]);
    const [versionOptions, setVersionOptions] = useState<string[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(false);


    function useDebouncedSearch(searchTerm: string | undefined, fetchFn: (term: string | undefined) => Promise<void>, delay = 300) {
        useEffect(() => {
            const timeoutId = setTimeout(() => {
                fetchFn(searchTerm);
            }, delay);

            return () => clearTimeout(timeoutId);
        }, [searchTerm, delay]);
    }


    useDebouncedSearch(groupSearchTerm, async (term) => {
        setIsLoading(true)
        try {
            const filters = term
                ? [{ by: FilterBy.groupId, value: term }]
                : [];

            const searchResults = await searchService.searchGroups(filters, GroupsSortBy.groupId,
                SortOrder.asc,
                { page: 1, pageSize: 10 })

            const groupIds = (searchResults.groups ?? []).map((group) => group.groupId)
                .filter((id): id is string => typeof id === 'string');
            setGroupOptions(groupIds);
        } catch (error) {
            console.error("Failed to fetch groups", error);
        } finally {
            setIsLoading(false)
            console.log("Finished searching Groups")
        }
    });

    useDebouncedSearch(artifactSearchTerm, async (term) => {
        setIsLoading(true)
        try {
            const filters = term
                ? [{ by: FilterBy.artifactId, value: term }]
                : [];

            const searchResults = await searchService.searchArtifacts(filters, ArtifactsSortBy.artifactId,
                SortOrder.asc,
                { page: 1, pageSize: 10 })

            const artifacts = (searchResults.artifacts ?? []).map((artifact) => artifact.artifactId)
                .filter((id): id is string => typeof id === 'string');
            setArtifactOptions(artifacts);
        } catch (error) {
            console.error("Failed to fetch artifacts", error);
        } finally {
            setIsLoading(false)
            console.log("Finished searching artifacts")
        }
    });

    useDebouncedSearch(versionSearchTerm, async (term) => {
        setIsLoading(true)
        try {
            const filters = term
                ? [{ by: FilterBy.version, value: term }]
                : [];

            const searchResults = await searchService.searchVersions(filters, VersionsSortBy.version,
                SortOrder.asc,
                { page: 1, pageSize: 10 })

            const versions = (searchResults.versions ?? []).map((version) => version.version)
                .filter((id): id is string => typeof id === 'string');
            setVersionOptions(versions);
        } catch (error) {
            console.error("Failed to fetch Versions", error);
        } finally {
            setIsLoading(false)
            console.log("Finished searching Versions")
        }
    });

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
                            <GridItem span={4}>
                                <FormGroup
                                    fieldId={`form-ref-group-${idx}`}
                                    label={idx === 0 ? "Group I" : ""}
                                >
                                    <SelectTypeaheadCreatable
                                        id={`form-ref-group-${idx}`}
                                        data-testid={`references-form-group-id-${idx}`}
                                        name=""
                                        autoCompleteResults={groupOptions}
                                        onTextInputChanged={(value: string) => setGroupSearchTerm(value)}
                                        value={ref.groupId}
                                        validated={validateRefField(ref.groupId)}
                                        onSelectOption={(selectedValue: string) => {
                                            ref.groupId = selectedValue;
                                            onChange([...references]);
                                        }}
                                    />
                                </FormGroup>
                            </GridItem>
                            <GridItem span={4}>
                                <FormGroup
                                    fieldId={`form-ref-artifact-${idx}`}
                                    label={idx === 0 ? "Artifact Id" : ""}
                                >
                                    <SelectTypeaheadCreatable
                                        id={`form-ref-artifact-${idx}`}
                                        data-testid={`references-form-artifact-id-${idx}`}
                                        name={`form-ref-artifact-${idx}`}
                                        autoCompleteResults={artifactOptions}
                                        onTextInputChanged={(value: string) => setArtifactSearchTerm(value)}
                                        value={ref.artifactId}
                                        validated={validateRefField(ref.artifactId)}
                                        onSelectOption={(selectedValue: string) => {
                                            ref.artifactId = selectedValue;
                                            onChange([...references]);
                                        }}
                                    />
                                </FormGroup>
                            </GridItem>
                            <GridItem span={4}>
                                <FormGroup
                                    fieldId={`form-ref-version-${idx}`}
                                    label={idx === 0 ? "Version" : ""}
                                >
                                    <div className="ref-field-group">
                                        <SelectTypeaheadCreatable
                                            id={`form-ref-version-${idx}`}
                                            data-testid={`references-form-version-${idx}`}
                                            name={`form-ref-version-${idx}`}
                                            autoCompleteResults={versionOptions}
                                            onTextInputChanged={(value: string) => setVersionSearchTerm(value)}
                                            value={ref.version}
                                            validated={validateRefField(ref.version)}
                                            onSelectOption={(selectedValue: string) => {
                                                ref.version = selectedValue;
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





