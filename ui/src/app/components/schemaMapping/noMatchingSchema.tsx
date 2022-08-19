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
    Alert,
    AlertVariant,
    ClipboardCopy,
    DescriptionListDescription,
    Popover,
    Split,
    SplitItem,
    Stack,
    StackItem,
} from "@patternfly/react-core";
import ArrowRightIcon from "@patternfly/react-icons/dist/js/icons/arrow-icon";
import HelpIcon from "@patternfly/react-icons/dist/js/icons/help-icon";

export type NoMatchingSchemaProps = {
  basename: string;
  registryId: string;
  artifactId: string;
  title: string;
};

export const NoMatchingSchema: React.FC<NoMatchingSchemaProps> = ({
  basename,
  registryId,
  artifactId,
  title,
}) => {
  return (
    <DescriptionListDescription>
      <Split hasGutter>
        <SplitItem>
          <Alert
            className="pf-c-alert pf-m-info pf-m-plain pf-m-inline"
            variant={AlertVariant.info}
            title="No matching schema"
          />
        </SplitItem>
        <SplitItem>
          <Popover
            hasAutoWidth
            aria-label="No schema popover"
            headerContent={title}
            bodyContent={
              <Stack hasGutter>
                <StackItem>
                  The system couldn't find a matching schema for this topic in
                  the selected Service Registry instance. Please make sure to
                  use the following naming format for the Artifact ID:
                </StackItem>
                <StackItem>
                  <ClipboardCopy isReadOnly hoverTip="Copy" clickTip="Copied">
                    {artifactId}
                  </ClipboardCopy>
                </StackItem>
                <StackItem>
                  <Link to={`${basename}/t/${registryId}`}>
                    Go to Service Registry instance <ArrowRightIcon />
                  </Link>
                </StackItem>
              </Stack>
            }
          >
            <HelpIcon />
          </Popover>
        </SplitItem>
      </Split>
    </DescriptionListDescription>
  );
};
