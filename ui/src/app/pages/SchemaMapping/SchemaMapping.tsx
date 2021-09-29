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
import { SchemaCard } from './SchemaCard'
import { Services } from 'src/services'
import { SchemaEmptyState } from './EmptyState'
import {
  PureComponent,
  PureComponentProps,
  PureComponentState,
} from '../../components/baseComponent'

/**
 * Properties
 */

export interface SchemaMappingProps extends PureComponentProps {
  topicName: string
  groupId:string
  version:string
}

/**
 * State
 */
export interface SchemaMappingState extends PureComponentState {
  hasKeySchema: boolean
  hasValueSchema: boolean
}

export class SchemaMapping extends PureComponent<
  SchemaMappingProps,
  SchemaMappingState
> {
  constructor(props: Readonly<SchemaMappingProps>) {
    super(props)
  }

  public render(): React.ReactElement {
    return this.state.hasKeySchema || this.state.hasValueSchema ? (
      <SchemaCard
        hasKeySchema={this.state.hasKeySchema}
        hasValueSchema={this.state.hasValueSchema}
        topicName={this.props.topicName}
      />
    ) : (
      <SchemaEmptyState artifactName={this.props.topicName} />
    )
  }

  protected initializeState(): SchemaMappingState {
    return {
      hasKeySchema: true,
      hasValueSchema: false,
    }
  }

  // @ts-ignore
  protected createLoaders(): Promise[] | null {
    Services.getLoggerService().info(
      'Loading data for artifact: ',
      this.props.topicName,
    )
    const topicNameValue= this.props.topicName+'-value'
    const topicNameKey= this.props.topicName+'-key'
    return [
      Services.getGroupsService()
        .getArtifactMetaData(this.props.groupId, topicNameKey, this.props.version)
        .then((data) => {
          this.setSingleState('hasKeySchema', true)
        })
        .catch((e) => {
          if (e.error_code == '404') {
            this.setSingleState('hasKeySchema', false)
          } 
        }),

      Services.getGroupsService()
        .getArtifactMetaData(this.props.groupId, topicNameValue, this.props.version)
        .then((data) => {
          this.setSingleState('hasValueSchema', true)
        })
        .catch((e) => {
          if (e.error_code == '404') {
            this.setSingleState('hasValueSchema', false)
          } 
        }),
    ]
  }
}
