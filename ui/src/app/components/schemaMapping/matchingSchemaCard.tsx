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
  Button,
  ButtonVariant,
  DescriptionListDescription,
} from '@patternfly/react-core'
import ExternalLinkAltIcon from '@patternfly/react-icons/dist/js/icons/external-link-alt-icon'
import CheckCircleIcon from '@patternfly/react-icons/dist/js/icons/check-circle-icon'

export type MatchingSchemaProps = {
  topicName: string
  keySchema: boolean
}

export const MatchingSchemaCard: React.FC<MatchingSchemaProps> = ({
  topicName,
  keySchema,
}) => {
  return (
    <DescriptionListDescription>
      <Grid hasGutter rowSpan={2}>
        <GridItem rowSpan={2}>
          <CheckCircleIcon /> {topicName + keySchema ? '-key' : '-value'}
        </GridItem>
        <GridItem>
          <Button isInline variant={ButtonVariant.link} component="a" href="#">
            View details <ExternalLinkAltIcon />
          </Button>
        </GridItem>
      </Grid>
    </DescriptionListDescription>
  )
}
