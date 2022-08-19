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
import { Link } from "react-router-dom";
import {
    ClipboardCopyButton,
    CodeBlock,
    CodeBlockAction,
    CodeBlockCode,
    DescriptionListDescription,
    ExpandableSection,
    Grid,
    GridItem,
} from "@patternfly/react-core";
import { PureComponent, PureComponentState } from "../baseComponent";
import CheckCircleIcon from "@patternfly/react-icons/dist/js/icons/check-circle-icon";
import { Services } from "src/services";

export type MatchingSchemaProps = {
  topicName: string;
  keySchema: boolean;
  routePath: string;
};

export interface MatchingSchemaSate extends PureComponentState {
  schemaContentData: any
  copySchemaContent: boolean;
}

export class MatchingSchemaCard extends PureComponent<MatchingSchemaProps, MatchingSchemaSate> {
  clipboardCopyFunc: (event: any, text: any) => void;
  onClick: (event: any, text: any) => void;
  timer: number;
  constructor(props: Readonly<MatchingSchemaProps>) {
    super(props);

    this.timer = 0;

    this.clipboardCopyFunc = (event, text) => {
      const clipboard = event.currentTarget.parentElement;
      const el = document.createElement('textarea');
      el.value = text.toString();
      clipboard.appendChild(el);
      el.select();
      clipboard.removeChild(el);
    };

    this.onClick = (event, text) => {
      if (this.timer) {
        window.clearTimeout(this.timer);
        this.setState({ copySchemaContent: false });
      }
      this.clipboardCopyFunc(event, text);
      this.setState({ copySchemaContent: true }, () => {
        this.timer = window.setTimeout(() => {
          this.setState({ copySchemaContent: false });
          this.timer = 0;
        }, 1000);
      });
    };
  }

  protected initializeState() {
    return {
      schemaContentData: undefined,
      copySchemaContent: false
    };
  }

  componentDidMount() {
    this.createLoaders();
  }

  // @ts-ignore
  protected createLoaders(): Promise[] | null {
    const { topicName } = this.props;

    const artifactId1 = topicName + "-key";
    const artifactId2 = topicName + "-value";

    if (this.props.keySchema) {
      return [
        Services.getGroupsService()
          .getLatestArtifact("default", artifactId1).then((response => this.setSingleState("schemaContentData", response)))
      ]
    }
    else {
      return [
        Services.getGroupsService()
          .getLatestArtifact("default", artifactId2).then((response => this.setSingleState("schemaContentData", response)))
      ]
    }
  }

  public render(): React.ReactElement {
    const { keySchema, topicName, routePath } = this.props;
    const postfix = keySchema ? "-key" : "-value";

    return (
      <DescriptionListDescription>
        <Grid hasGutter span={2}>
          <GridItem rowSpan={2}>
            <CheckCircleIcon color="#38812f" /> {topicName + postfix}
          </GridItem>
          <GridItem>
            <Link to={routePath}>
              View details
            </Link>
          </GridItem>
        </Grid>
        <ExpandableSection toggleText={"Display schema content"} displaySize="large" isWidthLimited className={"pf-c-change-background-color"}>
          <CodeBlock actions={<CodeBlockAction>
            <ClipboardCopyButton id="copy-button" aria-label="Copy to clipboard" textId="code-content" onClick={e => this.onClick(e, this.state.schemaContentData)} exitDelay={600}
              maxWidth="110px"
              variant="plain" >
              {this.state.copySchemaContent ? 'Successfully copied to clipboard!' : 'Copy to clipboard'}
            </ClipboardCopyButton>
          </CodeBlockAction>}>
            <CodeBlockCode>
              {this.state.schemaContentData}
            </CodeBlockCode>
          </CodeBlock>
        </ExpandableSection>
      </DescriptionListDescription >
    );
  }
}
