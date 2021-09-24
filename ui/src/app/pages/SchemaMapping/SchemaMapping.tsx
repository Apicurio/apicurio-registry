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
import { PageComponent, PageProps, PageState } from '../basePage'
import { SchemaCard } from './SchemaCard'
import { Services } from 'src/services'
import { SchemaEmptyState } from './EmptyState'

/**
 * Properties
 */
// tslint:disable-next-line:no-empty-interface
export interface SchemaMappingProps extends PageProps {}

/**
 * State
 */
export interface SchemaMappingState extends PageState {
  hasKeySchema: boolean
  hasValueSchema: boolean
  artifactId: string
}

function is404(e: any) {
  if (typeof e === 'string') {
    try {
      const eo: any = JSON.parse(e)
      if (eo && eo.error_code && eo.error_code === 404) {
        return true
      }
    } catch (e) {
      // Do nothing
    }
  }
  return false
}

export class SchemaMapping extends PageComponent<
  SchemaMappingState,
  SchemaMappingProps
> {
  constructor(props: Readonly<SchemaMappingState>) {
    super(props)
  }

  public renderPage(): React.ReactElement {
    return (
    this.props.hasKeySchema || this.props.hasValueSchema?
      <SchemaCard
        hasKeySchema={this.props.hasKeySchema}
        hasValueSchema={this.props.hasValueSchema}
        artifactName={this.props.artifactId}
      />:
      <SchemaEmptyState/>
    )
  }

  protected initializePageState(): SchemaMappingProps {
    return {}
  }

  protected groupIdParam(): string {
    return this.getPathParam('groupId')
  }

  protected artifactIdParam(): string {
    return this.getPathParam('artifactId')
  }

  protected versionParam(): string {
    return this.getPathParam('version')
  }

  // @ts-ignore
  protected getSchema(): Promise[] | null {
    let groupId: string | null = this.groupIdParam()
    if (groupId == 'default') {
      groupId = null
    }
    const artifactId: string = this.artifactIdParam()
    Services.getLoggerService().info('Loading data for artifact: ', artifactId)
    return [
      Services.getGroupsService()
        .getArtifactMetaData(groupId, artifactId, this.versionParam())
        .catch((e) => {
          if (is404(e)) {
            this.setSingleState('hasValueSchema', false)
          } else {
            throw e
          }
        }),
      Services.getGroupsService()
        .getArtifactMetaData(groupId, artifactId, this.versionParam())
        .catch((e) => {
          if (is404(e)) {
            this.setSingleState('hasKeySchema', false)
          } else {
            throw e
          }
        }),
    ]
  }
}
