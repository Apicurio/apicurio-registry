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
import { PageComponent, PageProps, PageState } from "../basePage";
import { Services } from "../../../services";

export type DownloadArtifactsProps = PageProps & {
  downloadLinkLabel: string | undefined;
  instanceName: string;
};
export type DownloadArtifactsState = PageState;

export class DownloadArtifacts extends PageComponent<
  DownloadArtifactsProps,
  DownloadArtifactsState
> {
  constructor(props: Readonly<DownloadArtifactsProps>) {
    super(props);
  }

  protected initializePageState(): DownloadArtifactsState {
    return {};
  }

  private createDownloadLink = (response: string): void => {
    const { instanceName = "artifacts" } = this.props;
    const downloadUrl = window.URL.createObjectURL(
      new Blob([response], { type: "application/zip" })
    );

    const link = document.createElement("a");
    link.href = downloadUrl;
    link.setAttribute("download", `${instanceName}.zip`);
    document.body.appendChild(link);
    link.click();
    link.remove();
  };

  private downloadArtifacts = (
    event: React.MouseEvent<HTMLButtonElement>
  ): void => {
    event.preventDefault();
    Services.getAdminService()
      .downloadArtifacts()
      .then((response: string) => {
        this.createDownloadLink(response);
      });
  };

  public renderPage(): React.ReactElement {
    const { downloadLinkLabel } = this.props;
    return (
      <Button
        variant={ButtonVariant.link}
        onClick={this.downloadArtifacts}
        isInline
        isActive={true}
      >
        {downloadLinkLabel || "Download artifacts (.zip)"}
      </Button>
    );
  }
}
