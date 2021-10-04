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

import React from 'react'
import {
  Grid,
  GridItem,
  Alert,
  AlertVariant,
  DescriptionListDescription,
  Popover,
} from '@patternfly/react-core'
import { OutlinedQuestionCircleIcon } from '@patternfly/react-icons'

export type NoMatchingSchemaProps = {
  topicName: string
  keySchema: boolean
}

export const NoMatchingSchema: React.FC<NoMatchingSchemaProps> = ({
  topicName,
  keySchema,
}) => {
  return (
    <DescriptionListDescription>
      <Grid hasGutter span={2}>
        <GridItem>
          <Alert
            className="pf-c-alert pf-m-info pf-m-plain pf-m-inline"
            variant={AlertVariant.info}
            title="No matching schema"
          />
        </GridItem>
        <GridItem>
          <Popover
            aria-label="No schema popover"
            headerContent={
              <div>{keySchema ? 'Key schema' : 'Value schema'}</div>
            }
            bodyContent={
              <div>
                The system couldn't find a matching schema for this topic in the
                selected Service Registry instance. Please make sure to use the
                following naming format for the Artifact ID:{topicName+ keySchema?'-key': '-value'}
              </div>
            }
          >
            <OutlinedQuestionCircleIcon />
          </Popover>
        </GridItem>
      </Grid>
    </DescriptionListDescription>
  )
}
