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
  Grid,
  GridItem,
  DescriptionListDescription,
} from "@patternfly/react-core";
import { PureComponent } from "../baseComponent";
import ExternalLinkAltIcon from "@patternfly/react-icons/dist/js/icons/external-link-alt-icon";
import CheckCircleIcon from "@patternfly/react-icons/dist/js/icons/check-circle-icon";

export type MatchingSchemaProps = {
  topicName: string;
  keySchema: boolean;
  routePath: string;
};

export class MatchingSchemaCard extends PureComponent<MatchingSchemaProps, {}> {
  constructor(props: Readonly<MatchingSchemaProps>) {
    super(props);
  }

  protected initializeState() {
    return {};
  }

  public render(): React.ReactElement {
    const { keySchema, topicName, routePath } = this.props;
    const postfix = keySchema ? "-key" : "-value";

    return (
      <DescriptionListDescription>
        <Grid hasGutter rowSpan={2}>
          <GridItem rowSpan={2}>
            <CheckCircleIcon color="#38812f" /> {topicName + postfix}
          </GridItem>
          <GridItem>
            <Link to={routePath}>
              View details <ExternalLinkAltIcon />
            </Link>
          </GridItem>
        </Grid>
      </DescriptionListDescription>
    );
  }
}
