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

import React from 'react';
import {
  Title,
  Button,
  EmptyState as PFEmptyState,
  EmptyStateIcon,
  EmptyStateBody,
  EmptyStateVariant,
  ClipboardCopy,
  EmptyStateSecondaryActions,
  Card,
  CardBody,
  CardTitle,
} from '@patternfly/react-core';
import { ArrowRightIcon, InfoCircleIcon } from '@patternfly/react-icons';

export type EmptyStateProps = {
  title?: string;
  bodyContent?: string;
  topicKey?: string;
  topicValue?: string;
  schemaHeader?: string;
};

export const SchemaEmptyState: React.FC<EmptyStateProps> = ({
  title,
  bodyContent,
  topicKey,
  topicValue,
  schemaHeader,
}: EmptyStateProps) => {
  const { ...restTitleProps } = {};
  const { ...restEmptyStateProps } = {};

  return (
    <>
      <Card>
        {schemaHeader && <CardTitle>{schemaHeader}</CardTitle>}
        <CardBody>
          <PFEmptyState variant={EmptyStateVariant.xl} {...restEmptyStateProps}>
            <EmptyStateIcon icon={InfoCircleIcon} color='#2B9AF3' />
            <Title headingLevel='h2' {...restTitleProps}>
              {title}
            </Title>
            <EmptyStateBody>{bodyContent}</EmptyStateBody>
            <EmptyStateSecondaryActions>
              <ClipboardCopy
                isReadOnly
                hoverTip='Copy'
                clickTip='Copied'
                className='pf-u-w-25'
              >
                {topicKey}
              </ClipboardCopy>
            </EmptyStateSecondaryActions>
            <EmptyStateSecondaryActions>
              <ClipboardCopy
                isReadOnly
                hoverTip='Copy'
                clickTip='Copied'
                className='pf-u-w-25'
              >
                {topicValue}
              </ClipboardCopy>
            </EmptyStateSecondaryActions>
            <EmptyStateSecondaryActions>
              <Button variant='link' >
                Go to Service Registry instance <ArrowRightIcon />
              </Button>
            </EmptyStateSecondaryActions>
          </PFEmptyState>
        </CardBody>
      </Card>
    </>
  );
};