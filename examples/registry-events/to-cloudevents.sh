#!/usr/bin/env bash
# Converts registry events (one JSON payload per line, on stdin) to CloudEvents 1.0 in structured
# mode (one JSON envelope per line, on stdout), with the type names the registry reserves for them
# (CloudEventConverter). The registry does not publish CloudEvents itself yet; a small bridge like
# this one can, until it does. See GUIDE.md step 7.
#
#   ... | ./to-cloudevents.sh
set -euo pipefail
jq -c --arg source "${CE_SOURCE:-/apicurio-registry}" '
  def ce_type:
    {
      ARTIFACT_CREATED: "artifact.created", ARTIFACT_DELETED: "artifact.deleted",
      ARTIFACT_METADATA_UPDATED: "artifact.metadata.updated", ARTIFACT_RULE_CONFIGURED: "artifact.rule.configured",
      ARTIFACT_VERSION_CREATED: "artifact.version.created", ARTIFACT_VERSION_DELETED: "artifact.version.deleted",
      ARTIFACT_VERSION_METADATA_UPDATED: "artifact.version.metadata.updated",
      ARTIFACT_VERSION_STATE_CHANGED: "artifact.version.state.changed",
      GROUP_CREATED: "group.created", GROUP_DELETED: "group.deleted",
      GROUP_METADATA_UPDATED: "group.metadata.updated", GROUP_RULE_CONFIGURED: "group.rule.configured",
      GLOBAL_RULE_CONFIGURED: "global.rule.configured",
      CONTRACT_RULESET_CONFIGURED: "contract.ruleset.configured",
      CONTRACT_METADATA_UPDATED: "contract.metadata.updated", CONTRACT_STATUS_CHANGED: "contract.status.changed"
    }[.eventType] | "io.apicurio.registry." + .;
  # subject: what changed; omitted when there is nothing to name (it must not be empty)
  ([.groupId, .artifactId, .version] | map(select(. != null)) | join("/")) as $coordinates
  | (if $coordinates != "" then $coordinates else .ruleType end) as $subject
  | {
      specversion: "1.0",
      id: .id,
      source: $source,
      type: ce_type,
      datacontenttype: "application/json",
      data: .
    }
  + (if $subject != null and $subject != "" then {subject: $subject} else {} end)'
