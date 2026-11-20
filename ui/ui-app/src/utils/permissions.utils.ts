import { ResourcePermissions } from "@sdk/lib/generated-client/models";

/**
 * A permission reported by the server for a group, artifact or version, when per-resource
 * authorization is enabled.
 */
export type ResourcePermission = "read" | "write" | "admin";

/**
 * Whether the reported permissions allow the given permission. The permissions are absent when
 * per-resource authorization is disabled, or for results that do not report them: the UI then
 * behaves as before and lets the server decide. The flags are hints, so a request that they allow
 * can still be refused.
 */
export const isPermitted = (permissions: ResourcePermissions | null | undefined,
    permission: ResourcePermission = "write"): boolean => {
    return permissions?.[permission] !== false;
};
