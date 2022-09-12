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
import {
    Card,
    CardBody,
    CardTitle,
    EmptyState,
    EmptyStateBody,
    EmptyStateIcon,
    EmptyStateVariant,
    Title,
} from "@patternfly/react-core";
import LockIcon from "@patternfly/react-icons/dist/js/icons/lock-icon";

export const PermissionDenied: React.FC = () => {
  return (
    <Card>
      <CardTitle>Topic schemas</CardTitle>
      <CardBody>
        <EmptyState variant={EmptyStateVariant.large}>
          <EmptyStateIcon icon={LockIcon} />
          <Title headingLevel="h4" size="lg">
            Access permissions needed
          </Title>
          <EmptyStateBody>
            You don’t have access to the selected Service Registry instance. To
            get access, contact your organization administrators.
          </EmptyStateBody>
        </EmptyState>
      </CardBody>
    </Card>
  );
};
