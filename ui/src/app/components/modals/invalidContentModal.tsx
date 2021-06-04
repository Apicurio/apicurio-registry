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
import React from 'react';
import "./invalidContentModal.css";
import {Button, Form, FormGroup, Modal, TextArea, TextInput} from "@patternfly/react-core";
import {PureComponent, PureComponentProps, PureComponentState} from "../baseComponent";


/**
 * Properties
 */
export interface InvalidContentModalProps extends PureComponentProps {
    error: any;
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
                isLarge={true}
                isOpen={this.props.isOpen}
                onClose={this.props.onClose}
                className="edit-artifact-metaData pf-m-redhat-font"
                actions={[
                    <Button key="close" variant="link" data-testid="modal-btn-close" onClick={this.props.onClose}>Close</Button>
                ]}
            >
                <p>The content you attempted to upload violated one or more of the established content rules.</p>
                <pre className="error-detail">
                    { this.errorDetail() }
                </pre>
            </Modal>
        );
    }

    protected initializeState(): InvalidContentModalState {
        return {};
    }

    private errorDetail(): string {
        if (this.props.error && this.props.error.detail) {
            return this.props.error.detail;
        }
        return "";
    }

}
