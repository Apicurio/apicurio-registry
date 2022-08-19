/**
 * @license
 * Copyright 2022 JBoss Inc
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
import "./progressModal.css";
import { Modal, Progress } from "@patternfly/react-core";
import { PureComponent, PureComponentProps, PureComponentState } from "../baseComponent";


/**
 * Properties
 */
export interface ProgressModalProps extends PureComponentProps {
    title: string;
    isCloseable: boolean;
    message: string;
    isOpen: boolean;
    progress: number | undefined;
    onClose: () => void;
}

/**
 * State
 */
// tslint:disable-next-line:no-empty-interface
export interface ProgressModalState extends PureComponentState {
}

/**
 * Models the "progress" modal.  This is shown when the user performs an asynchronous operation
 * with trackable progress (by percentage).
 */
export class ProgressModal extends PureComponent<ProgressModalProps, ProgressModalState> {

    constructor(props: Readonly<ProgressModalProps>) {
        super(props);
    }

    public render(): React.ReactElement {
        return (
            <Modal
                title={this.props.title}
                variant="small"
                isOpen={this.props.isOpen}
                showClose={this.props.isCloseable}
                onClose={this.props.onClose}
                className="progress pf-m-redhat-font"
                aria-label="progress-modal"
            >
                <Progress title={this.props.message} value={this.props.progress} />
            </Modal>
        );
    }

    protected initializeState(): ProgressModalState {
        return {};
    }

}
