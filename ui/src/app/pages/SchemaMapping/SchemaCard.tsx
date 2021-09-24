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

import React from 'react'
import {
  Card,
  CardTitle,
  CardBody,
  DescriptionList,
  DescriptionListTerm,
} from '@patternfly/react-core'
import './style.css'
import { MatchingSchemaCard } from './MatchingSchemaCard'
import { NoMatchingSchema } from './NoMatchingSchema'

export type SchemaCardPropsProps = {
  hasValueSchema: boolean
  hasKeySchema: boolean
  artifactName: string
}

export const SchemaCard: React.FC<SchemaCardPropsProps> = ({
  hasKeySchema,
  hasValueSchema,
  artifactName,
}) => {
  return (
    <Card>
      <CardTitle component="h2">Topic schemas</CardTitle>

      <CardBody>
        <DescriptionList
          className={'pf-c-description-list__RowGap'}
          isHorizontal
          isAutoColumnWidths
          columnModifier={{ lg: '2Col' }}
        >
          <DescriptionListTerm>Value schema artifact ID</DescriptionListTerm>
          {hasValueSchema ? (
            <MatchingSchemaCard artifactName={artifactName} />
          ) : (
            <NoMatchingSchema artifactName={artifactName} keySchema={false} />
          )}

          <DescriptionListTerm>Key schema artifact ID</DescriptionListTerm>
          {hasKeySchema ? (
            <MatchingSchemaCard artifactName={artifactName} />
          ) : (
            <NoMatchingSchema artifactName={artifactName} keySchema={true} />
          )}
        </DescriptionList>
      </CardBody>
    </Card>
  )
}
