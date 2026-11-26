import { FunctionComponent, useEffect, useState } from "react";
import "./PeersPage.css";
import {
    Alert,
    Button,
    PageSection,
    PageSectionVariants,
    Pagination,
    Toolbar,
    ToolbarContent,
    ToolbarItem
} from "@patternfly/react-core";
import {
    PageDataLoader,
    PageError,
    PageErrorHandler,
    PageProperties,
    PEERS_PAGE_IDX,
    toPageError
} from "@app/pages";
import { PeerList, PeerModal, PeersEmptyState } from "@app/pages/peers/components";
import { peerErrorMessage } from "@app/pages/peers/peerValidation.ts";
import { RootPageHeader } from "@app/components";
import { AdminService, useAdminService } from "@services/useAdminService.ts";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { isErrorStatus } from "@utils/rest.utils.ts";
import { Paging } from "@models/Paging.ts";
import { NewPeer, Peer, UpdatePeer } from "@sdk/lib/generated-client/models";

const DEFAULT_PAGING: Paging = {
    page: 1,
    pageSize: 10
};

/**
 * The remote registries (peers) page: lists the registries this registry federates agent search with,
 * and lets an administrator add, edit and remove them.
 */
export const PeersPage: FunctionComponent<PageProperties> = () => {
    const [pageError, setPageError] = useState<PageError>();
    const [loaders, setLoaders] = useState<Promise<any> | Promise<any>[] | undefined>();
    const [paging, setPaging] = useState<Paging>(DEFAULT_PAGING);
    const [peers, setPeers] = useState<Peer[]>([]);
    const [totalCount, setTotalCount] = useState(0);
    const [isFederationDisabled, setIsFederationDisabled] = useState(false);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [selectedPeer, setSelectedPeer] = useState<Peer>();
    const [actionError, setActionError] = useState<string>();

    const admin: AdminService = useAdminService();
    const config: ConfigService = useConfigService();
    const isReadOnly: boolean = config.featureReadOnly();

    const loadPeers = (requested: Paging): Promise<void> => {
        return admin.getPeers(requested).then(results => {
            const count: number = results.count || 0;
            const pagePeers: Peer[] = results.peers || [];
            if (pagePeers.length === 0 && requested.page > 1 && count > 0) {
                // The page is past the end, for example after its last peer was removed: show the last page.
                return loadPeers({ page: Math.ceil(count / requested.pageSize), pageSize: requested.pageSize });
            }
            setPaging(requested);
            setPeers(pagePeers);
            setTotalCount(count);
            setIsFederationDisabled(false);
        }).catch(error => {
            if (isErrorStatus(error, 409)) {
                // Listing only fails with a conflict when federation is switched off on the server.
                setPeers([]);
                setTotalCount(0);
                setIsFederationDisabled(true);
            } else {
                setPageError(toPageError(error, "Error loading remote registries."));
            }
        });
    };

    const onSetPage = (_event: any, newPage: number, perPage?: number): void => {
        loadPeers({ page: newPage, pageSize: perPage ? perPage : paging.pageSize });
    };

    const onPerPageSelect = (_event: any, newPerPage: number, newPage: number): void => {
        loadPeers({ page: newPage, pageSize: newPerPage });
    };

    const onAddPeer = (): void => {
        setSelectedPeer(undefined);
        setIsModalOpen(true);
    };

    const onEditPeer = (peer: Peer): void => {
        setSelectedPeer(peer);
        setIsModalOpen(true);
    };

    const closeModal = (): void => {
        setIsModalOpen(false);
        setSelectedPeer(undefined);
    };

    const savePeer = (peer: NewPeer, isEdit: boolean): Promise<void> => {
        setActionError(undefined);
        let request: Promise<void>;
        if (isEdit) {
            // The peer ID is the identity of the peer, so an update sends everything but the ID.
            const update: UpdatePeer = {
                url: peer.url,
                name: peer.name,
                description: peer.description,
                credentialSecretRef: peer.credentialSecretRef,
                enabled: peer.enabled
            };
            request = admin.updatePeer(peer.peerId!, update);
        } else {
            request = admin.createPeer(peer);
        }
        return request.then(() => {
            closeModal();
            return loadPeers(paging);
        });
    };

    const removePeer = (peer: Peer): void => {
        setActionError(undefined);
        admin.deletePeer(peer.peerId!).then(() => {
            return loadPeers(paging);
        }).catch(error => {
            setActionError(peerErrorMessage(error, "The remote registry could not be removed."));
        });
    };

    useEffect(() => {
        setLoaders(loadPeers(DEFAULT_PAGING));
    }, []);

    return (
        <PageErrorHandler error={pageError}>
            <PageDataLoader loaders={loaders}>
                <PageSection hasBodyWrapper={false} className="ps_peers-header" padding={{ default: "noPadding" }}>
                    <RootPageHeader tabKey={PEERS_PAGE_IDX} />
                </PageSection>
                <PageSection hasBodyWrapper={false} variant={PageSectionVariants.default} isFilled={true} className="ps_peers-section">
                    { actionError ?
                        <Alert variant="danger" isInline title={actionError} data-testid="peers-action-error"
                            className="ps_peers-alert" actionClose={
                                <Button variant="plain" aria-label="Dismiss" onClick={() => setActionError(undefined)}>×</Button>
                            } /> : null }
                    { isFederationDisabled ?
                        <PeersEmptyState isFederationDisabled={true} /> : null }
                    { !isFederationDisabled && peers.length === 0 ?
                        <PeersEmptyState isReadOnly={isReadOnly} onAddPeer={onAddPeer} /> : null }
                    { !isFederationDisabled && peers.length > 0 ?
                        <>
                            <Toolbar>
                                <ToolbarContent>
                                    { isReadOnly ? null :
                                        <ToolbarItem>
                                            <Button variant="primary" data-testid="btn-add-peer" onClick={onAddPeer}>
                                                Add remote registry
                                            </Button>
                                        </ToolbarItem> }
                                    <ToolbarItem align={{ default: "alignEnd" }}>
                                        <Pagination
                                            variant="top"
                                            dropDirection="down"
                                            itemCount={totalCount}
                                            perPage={paging.pageSize}
                                            page={paging.page}
                                            onSetPage={onSetPage}
                                            onPerPageSelect={onPerPageSelect}
                                            widgetId="peer-list-pagination"
                                            className="peer-list-pagination"
                                        />
                                    </ToolbarItem>
                                </ToolbarContent>
                            </Toolbar>
                            <PeerList peers={peers} isReadOnly={isReadOnly} onEdit={onEditPeer} onRemove={removePeer} />
                        </> : null }
                </PageSection>
            </PageDataLoader>
            <PeerModal isOpen={isModalOpen} peer={selectedPeer} onClose={closeModal} onSave={savePeer} />
        </PageErrorHandler>
    );

};
