/**
 * @license
 * Copyright 2020 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import React from "react";
import "./invalidContentModal.css";
import {
    Button,
    DataList,
    DataListCell,
    DataListItemCells,
    DataListItemRow,
    Modal,
    ModalVariant
} from "@patternfly/react-core";
import { PureComponent, PureComponentProps, PureComponentState } from "../baseComponent";
import { ApiError } from "src/models/apiError.model";
import { ExclamationCircleIcon } from "@patternfly/react-icons";


/**
 * Properties
 */
export interface InvalidContentModalProps extends PureComponentProps {
    error: ApiError|null;
    isOpen: boolean;
    onClose: () => void;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface InvalidContentModalState extends PureComponentState {
}

/**
 * Models the "invalid content" modal.  This is shown when the user tries to upload content
 * that is not valid.
 */
export class InvalidContentModal extends PureComponent<InvalidContentModalProps, InvalidContentModalState> {

    constructor(props: Readonly<InvalidContentModalProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <Modal
                title="Invalid Content Error"
                variant={ModalVariant.large}
                isOpen={this.props.isOpen}
                onClose={this.props.onClose}
                className="edit-artifact-metaData pf-m-redhat-font"
                actions={[
                    <Button key="close" variant="link" data-testid="modal-btn-close" onClick={this.props.onClose}>Close</Button>
                ]}
            >
                <p className="modal-desc" >The content you attempted to upload violated one or more of the established content rules.</p>
                { this.errorDetail() }
            </Modal>
        );
    }

    protected initializeState(): InvalidContentModalState {
        return {};
    }

    private errorDetail(): React.ReactElement {
        if (this.props.error) {
            if (this.props.error.name === "RuleViolationException" && this.props.error.causes != null && this.props.error.causes.length > 0 ) {
                return (
                    <DataList aria-label="Error causes" className="error-causes" >
                        {
                            this.props.error.causes.map( (cause, idx) =>
                                <DataListItemRow key={""+idx} className="error-causes-item">
                                    <DataListItemCells
                                        dataListCells={[
                                            <DataListCell key="error icon" className="type-icon-cell">
                                                <ExclamationCircleIcon/>
                                            </DataListCell>,
                                            <DataListCell key="main content">
                                                <div className="error-causes-item-title">
                                                    <span>{cause.context != null ? (<b>{cause.context}</b>) : cause.description}</span>
                                                </div>
                                                <div className="error-causes-item-description">{cause.context != null ? cause.description : cause.context }</div>
                                            </DataListCell>
                                        ]}
                                    />
                                </DataListItemRow>
                            )
                        }
                    </DataList>
                );
            } else if (this.props.error.detail) {
                return (
                    <pre className="error-detail">
                        {this.props.error.detail}
                    </pre>
                );
            }
        }
        return <p/>;
    }

}
