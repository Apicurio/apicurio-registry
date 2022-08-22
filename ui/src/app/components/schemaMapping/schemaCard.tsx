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
import { Card, CardBody, CardTitle, DescriptionList, DescriptionListTerm, } from "@patternfly/react-core";
import { MatchingSchemaCard } from "./matchingSchemaCard";
import { NoMatchingSchema } from "./noMatchingSchema";

export type SchemaCardProps = {
  hasValueSchema: boolean | undefined;
  hasKeySchema: boolean | undefined;
  topicName: string;
  groupId: string;
  version: string;
  basename: string;
  registryId: string;
};

export const SchemaCard: React.FC<SchemaCardProps> = ({
  hasKeySchema,
  hasValueSchema,
  topicName,
  version,
  basename,
  registryId,
}) => {
  return (
    <Card>
      <CardTitle component="h2">Topic schemas</CardTitle>
      <CardBody>
        <DescriptionList
          isHorizontal
          isAutoColumnWidths
          columnModifier={{ lg: "2Col" }}
        >
          <DescriptionListTerm>Value schema artifact ID</DescriptionListTerm>
          {hasValueSchema ? (
            <MatchingSchemaCard
              topicName={topicName}
              keySchema={false}
              routePath={`${basename}/t/${registryId}/artifacts/default/${topicName}-value/versions/${version}`}
            />
          ) : (
            <NoMatchingSchema
              title="Value schema"
              artifactId={`${topicName}-value`}
              basename={basename}
              registryId={registryId}
            />
          )}
          <DescriptionListTerm>Key schema artifact ID</DescriptionListTerm>
          {hasKeySchema ? (
            <MatchingSchemaCard
              topicName={topicName}
              keySchema={true}
              routePath={`${basename}/t/${registryId}/artifacts/default/${topicName}-key/versions/${version}`}
            />
          ) : (
            <NoMatchingSchema
              title="Key schema"
              artifactId={`${topicName}-key`}
              basename={basename}
              registryId={registryId}
            />
          )}
        </DescriptionList>
      </CardBody>
    </Card>
  );
};
