import { RefObject, useEffect, useState } from "react";
import { useAuth } from "@apicurio/common-ui-components";
import { useReauthenticationService } from "@services/useReauthenticationService.ts";
import { isErrorStatus } from "@utils/rest.utils.ts";
import { SnapshotPersistenceResult } from "./useEditorDraftRecovery.ts";

export type UseEditorReauthenticationProps = {
    groupId: string;
    draftId: string;
    version: string;
    isAuthRedirectInProgressRef: RefObject<boolean>;
    persistDraftRecoverySnapshot: () => SnapshotPersistenceResult;
};

export type UseEditorReauthentication = {
    isReauthenticateModalOpen: boolean;
    isReauthenticateRedirecting: boolean;
    confirmWithoutSnapshot: boolean;
    requestReauthenticationIfUnauthorized: (error: unknown) => Promise<boolean>;
    handleRequestError: (error: unknown, onUnhandled: () => void) => Promise<void>;
    onReauthenticate: () => Promise<void>;
    onCloseReauthenticateModal: () => void;
};

export const useEditorReauthentication = ({
    groupId,
    draftId,
    version,
    isAuthRedirectInProgressRef,
    persistDraftRecoverySnapshot
}: UseEditorReauthenticationProps): UseEditorReauthentication => {
    const auth = useAuth();
    const reauthentication = useReauthenticationService();

    const [isReauthenticateModalOpen, setReauthenticateModalOpen] = useState(false);
    const [isReauthenticateRedirecting, setReauthenticateRedirecting] = useState(false);
    const [confirmWithoutSnapshot, setConfirmWithoutSnapshot] = useState(false);

    const openReauthenticateModal = (): void => {
        isAuthRedirectInProgressRef.current = false;
        setReauthenticateRedirecting(false);
        setConfirmWithoutSnapshot(false);
        setReauthenticateModalOpen(true);
    };

    useEffect(() => {
        setReauthenticateModalOpen(false);
        setReauthenticateRedirecting(false);
        setConfirmWithoutSnapshot(false);
        isAuthRedirectInProgressRef.current = false;
    }, [draftId, groupId, version, isAuthRedirectInProgressRef]);

    useEffect(() => {
        return reauthentication.registerReauthenticationInterceptor(async () => {
            // The editor intercepts the global re-auth flow so it can save a local recovery snapshot first.
            openReauthenticateModal();
            return true;
        });
    }, [reauthentication]);

    const requestReauthenticationIfUnauthorized = async (error: unknown): Promise<boolean> => {
        if (!isErrorStatus(error, 401)) {
            return false;
        }

        return reauthentication.requestReauthentication(auth);
    };

    const handleRequestError = async (
        error: unknown,
        onUnhandled: () => void
    ): Promise<void> => {
        if (!await requestReauthenticationIfUnauthorized(error)) {
            onUnhandled();
        }
    };

    const prepareSnapshotForReauthentication = (): boolean => {
        if (confirmWithoutSnapshot) {
            return true;
        }

        const persistenceResult = persistDraftRecoverySnapshot();
        if (persistenceResult !== "quota_exceeded") {
            return true;
        }

        setConfirmWithoutSnapshot(true);
        setReauthenticateRedirecting(false);
        isAuthRedirectInProgressRef.current = false;
        return false;
    };

    const onReauthenticate = async (): Promise<void> => {
        try {
            setReauthenticateRedirecting(true);
            // Suppress the regular beforeunload prompt only for this explicit auth redirect.
            isAuthRedirectInProgressRef.current = true;
            if (prepareSnapshotForReauthentication()) {
                await reauthentication.startReauthenticationRedirect(auth);
            }
        } catch (error) {
            console.error("[EditorPage] Failed to initiate re-authentication redirect", error);
            setReauthenticateRedirecting(false);
            isAuthRedirectInProgressRef.current = false;
        }
    };

    const onCloseReauthenticateModal = (): void => {
        if (isReauthenticateRedirecting) {
            return;
        }

        isAuthRedirectInProgressRef.current = false;
        setReauthenticateRedirecting(false);
        setConfirmWithoutSnapshot(false);
        reauthentication.cancelReauthentication();
        setReauthenticateModalOpen(false);
    };

    return {
        isReauthenticateModalOpen,
        isReauthenticateRedirecting,
        confirmWithoutSnapshot,
        requestReauthenticationIfUnauthorized,
        handleRequestError,
        onReauthenticate,
        onCloseReauthenticateModal
    };
};
