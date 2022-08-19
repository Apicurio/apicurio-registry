/**
 * @license
 * Copyright 2021 Red Hat
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
import { Button, ButtonVariant } from "@patternfly/react-core";
import { PureComponent, PureComponentProps, PureComponentState, } from "../baseComponent";
import { Services } from "../../../services";

/**
 * Properties
 */

export interface DownloadArtifactsProps extends PureComponentProps {
  fileName: string;
  downloadLinkLabel?: string;
}

/**
 * State
 */
export interface DownloadArtifactsState extends PureComponentState {}

export class DownloadArtifacts extends PureComponent<
  DownloadArtifactsProps,
  DownloadArtifactsState
> {
  constructor(props: Readonly<DownloadArtifactsProps>) {
    super(props);
  }

  protected initializeState(): DownloadArtifactsState {
    return {};
  }

  private generateDownloadLink(fileName: string, href: string): void {
    const link = document.createElement("a");
    link.href = href;
    link.download = `${fileName}.zip`;
    link.click();
  }

  private downloadArtifacts = () => {
    const { fileName } = this.props;
    Services.getAdminService()
      .exportAs(fileName)
      .then((response) => {
        this.generateDownloadLink(fileName, response?.href);
      });
  };

  public render(): React.ReactElement {
    const { downloadLinkLabel = "Download artifacts (.zip)" } = this.props;

    return (
      <Button
        variant={ButtonVariant.link}
        onClick={this.downloadArtifacts}
        isInline
        isActive={true}
      >
        {downloadLinkLabel}
      </Button>
    );
  }
}
