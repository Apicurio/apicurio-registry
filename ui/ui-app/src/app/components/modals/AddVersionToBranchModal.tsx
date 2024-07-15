import { FunctionComponent, useEffect, useState } from "react";
import { Button, Form, FormGroup, Modal } from "@patternfly/react-core";
import { SearchedBranch, SearchedVersion } from "@sdk/lib/generated-client/models";
import { IfNotLoading, ObjectSelect } from "@apicurio/common-ui-components";
import { GroupsService, useGroupsService } from "@services/useGroupsService.ts";
import { shash } from "@utils/string.utils.ts";


/**
 * Props
 */
export type AddVersionToBranchModalProps = {
    isOpen: boolean;
    version: SearchedVersion;
    onClose: () => void;
    onAdd: (branch: SearchedBranch) => void;
};

/**
 * Models the Add Version to Branch dialog.
 */
export const AddVersionToBranchModal: FunctionComponent<AddVersionToBranchModalProps> = (props: AddVersionToBranchModalProps) => {
    const [isLoading, setIsLoading] = useState(false);
    const [branches, setBranches] = useState<SearchedBranch[]>();
    const [selectedBranch, setSelectedBranch] = useState<SearchedBranch>();

    const groups: GroupsService = useGroupsService();

    const onAdd = (): void => {
        props.onAdd(selectedBranch as SearchedBranch);
    };

    const search = (): void => {
        setIsLoading(true);
        groups.getArtifactBranches(props.version.groupId || "default", props.version.artifactId!, {
            page: 1,
            pageSize: 50
        }).then(results => {
            setIsLoading(false);
            setBranches(results.branches?.filter(branch => !branch.systemDefined));
        });
    };

    useEffect(() => {
        if (props.isOpen) {
            search();
        }
    }, [props.isOpen]);

    return (
        <Modal
            title="Add to Branch"
            variant="medium"
            isOpen={props.isOpen}
            onClose={props.onClose}
            className="add-to-branch pf-m-redhat-font"
            actions={[
                <Button
                    key="add"
                    variant="primary"
                    data-testid="modal-btn-add"
                    isDisabled={selectedBranch === undefined}
                    onClick={onAdd}>Add</Button>,
                <Button
                    key="cancel"
                    variant="link"
                    data-testid="modal-btn-cancel"
                    onClick={props.onClose}>Cancel</Button>
            ]}
        >
            <Form>
                <FormGroup label="Branch" isRequired={false} fieldId="form-branch">
                    <IfNotLoading isLoading={isLoading}>
                        <ObjectSelect
                            noSelectionLabel="Select a branch"
                            value={selectedBranch}
                            items={branches || []}
                            onSelect={setSelectedBranch}
                            itemToString={item => item.branchId}
                            itemToTestId={item => `branch-${shash(item.branchId)}`}
                            toggleId="select-branch-toggle"
                            appendTo="document"
                        />
                    </IfNotLoading>
                </FormGroup>
            </Form>
        </Modal>
    );
};
