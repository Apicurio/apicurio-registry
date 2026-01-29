import { FunctionComponent, useEffect, useState } from "react";
import {
	Button,
	Form,
	FormGroup,
	FormHelperText,
	HelperText,
	HelperTextItem,
	Content
} from '@patternfly/react-core';
import {
	Modal
} from '@patternfly/react-core/deprecated';
import { VersionState, VersionStateObject } from "@sdk/lib/generated-client/models";
import { ObjectSelect } from "@apicurio/common-ui-components";


/**
 * Properties
 */
export type ChangeVersionStateModalProps = {
    isOpen: boolean;
    currentState: VersionState;
    onClose: () => void;
    onChangeState: (newState: VersionState) => void;
};

type StateOption = {
    value: VersionState;
    label: string;
    description: string;
};

const getStateOptions = (currentState: VersionState): StateOption[] => {
    const options: StateOption[] = [];

    // DRAFT can only transition to ENABLED
    if (currentState === VersionStateObject.DRAFT) {
        options.push({
            value: VersionStateObject.ENABLED,
            label: "Enabled",
            description: "The version is active and can be used."
        });
    } else {
        // Non-DRAFT states can transition to ENABLED, DISABLED, or DEPRECATED (but not DRAFT)
        if (currentState !== VersionStateObject.ENABLED) {
            options.push({
                value: VersionStateObject.ENABLED,
                label: "Enabled",
                description: "The version is active and can be used."
            });
        }
        if (currentState !== VersionStateObject.DISABLED) {
            options.push({
                value: VersionStateObject.DISABLED,
                label: "Disabled",
                description: "The version is disabled and cannot be used."
            });
        }
        if (currentState !== VersionStateObject.DEPRECATED) {
            options.push({
                value: VersionStateObject.DEPRECATED,
                label: "Deprecated",
                description: "The version is deprecated and should not be used for new projects."
            });
        }
    }

    return options;
};

const stateToLabel = (state: VersionState | undefined): string => {
    switch (state) {
        case VersionStateObject.ENABLED:
            return "Enabled";
        case VersionStateObject.DISABLED:
            return "Disabled";
        case VersionStateObject.DEPRECATED:
            return "Deprecated";
        case VersionStateObject.DRAFT:
            return "Draft";
        default:
            return "Unknown";
    }
};

export const ChangeVersionStateModal: FunctionComponent<ChangeVersionStateModalProps> = (
    { isOpen, onClose, currentState, onChangeState }: ChangeVersionStateModalProps) => {

    const [isValid, setValid] = useState(false);
    const [newState, setNewState] = useState<VersionState | undefined>();
    const [stateOptions, setStateOptions] = useState<StateOption[]>([]);

    useEffect(() => {
        if (isOpen) {
            setNewState(undefined);
            setStateOptions(getStateOptions(currentState));
        }
    }, [isOpen, currentState]);

    useEffect(() => {
        setValid(newState !== undefined && newState !== currentState);
    }, [newState, currentState]);

    const onStateSelect = (value: StateOption | undefined): void => {
        setNewState(value?.value);
    };

    return (
        <Modal
            title="Change version state"
            variant="medium"
            isOpen={isOpen}
            onClose={onClose}
            className="change-version-state pf-m-redhat-font"
            actions={[
                <Button key="change" variant="primary" data-testid="modal-btn-change" onClick={() => { onChangeState(newState!); }} isDisabled={!isValid}>Change state</Button>,
                <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={onClose}>Cancel</Button>
            ]}
        >
            <Form>
                <FormGroup label="Current state" fieldId="form-current-state">
                    <Content component="p">{ stateToLabel(currentState) }</Content>
                </FormGroup>
                <FormGroup label="New state" fieldId="form-new-state" isRequired={true}>
                    <ObjectSelect
                        value={stateOptions.find(o => o.value === newState)}
                        items={stateOptions}
                        toggleId="form-new-state"
                        testId="form-new-state"
                        onSelect={onStateSelect}
                        itemToString={(item) => item.label}
                        itemToTestId={(item) => `state-option-${item.value}`}
                        noSelectionLabel="Select a state"
                    />
                    <FormHelperText>
                        <HelperText>
                            <HelperTextItem>
                                {newState ? stateOptions.find(o => o.value === newState)?.description : "Select a new state for this version."}
                            </HelperTextItem>
                        </HelperText>
                    </FormHelperText>
                </FormGroup>
            </Form>
        </Modal>
    );

};
