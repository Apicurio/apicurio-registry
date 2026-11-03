import React, { FunctionComponent, useState } from "react";
import "./PeerList.css";
import { Button, Label } from "@patternfly/react-core";
import {
    Modal
} from "@patternfly/react-core/deprecated";
import { Table, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table";
import { Peer } from "@sdk/lib/generated-client/models";

/**
 * Properties
 */
export type PeerListProps = {
    peers: Peer[];
    // When true the rows have no edit or remove actions (read-only UI).
    isReadOnly: boolean;
    onEdit: (peer: Peer) => void;
    onRemove: (peer: Peer) => void;
};


/**
 * Models the table of configured remote registries.
 */
export const PeerList: FunctionComponent<PeerListProps> = (props: PeerListProps) => {
    const [isRemoveModalOpen, setIsRemoveModalOpen] = useState(false);
    const [currentPeer, setCurrentPeer] = useState<Peer>();

    const onRemoveClick = (peer: Peer): void => {
        setCurrentPeer(peer);
        setIsRemoveModalOpen(true);
    };

    const onRemoveModalClose = (): void => {
        setIsRemoveModalOpen(false);
    };

    const doRemove = (): void => {
        setIsRemoveModalOpen(false);
        if (currentPeer) {
            props.onRemove(currentPeer);
        }
    };

    const peerActions = (peer: Peer) => [
        {
            title: "Edit",
            onClick: () => { props.onEdit(peer); }
        },
        {
            title: "Remove",
            onClick: () => { onRemoveClick(peer); }
        }
    ];

    return (
        <React.Fragment>
            <Table className="peer-list" aria-label="Remote registries" data-testid="peer-list">
                <Thead>
                    <Tr>
                        <Th>Registry</Th>
                        <Th>URL</Th>
                        <Th>Status</Th>
                        <Th>Credential reference</Th>
                    </Tr>
                </Thead>
                <Tbody>
                    {props.peers.map((peer: Peer) =>
                        <Tr key={peer.peerId} data-testid={`peer-row-${peer.peerId}`}>
                            <Td dataLabel="Registry">
                                <div className="peer-name">{ peer.name || peer.peerId }</div>
                                <div className="peer-id">{ peer.name ? peer.peerId : "" }</div>
                                <div className="peer-description">{ peer.description }</div>
                            </Td>
                            <Td dataLabel="URL">{ peer.url }</Td>
                            <Td dataLabel="Status">
                                { peer.enabled === false ?
                                    <Label color="grey">Disabled</Label> :
                                    <Label color="green">Enabled</Label> }
                            </Td>
                            <Td dataLabel="Credential reference">{ peer.credentialSecretRef || "None" }</Td>
                            { props.isReadOnly ? null :
                                <Td className="peer-list-action-column"
                                    actions={{
                                        items: peerActions(peer)
                                    }}
                                />
                            }
                        </Tr>
                    )}
                </Tbody>
            </Table>
            <Modal
                title="Remove remote registry?"
                variant="small"
                isOpen={isRemoveModalOpen}
                onClose={onRemoveModalClose}
                className="remove-peer-modal pf-m-redhat-font"
                actions={[
                    <Button key="remove" variant="primary" data-testid="modal-btn-remove" onClick={doRemove}>Remove</Button>,
                    <Button key="cancel" variant="link" data-testid="modal-btn-cancel" onClick={onRemoveModalClose}>Cancel</Button>
                ]}
            >
                <p>
                    Federated agent search will no longer query {currentPeer?.name || currentPeer?.peerId}.
                    Its agents stay in that registry.
                </p>
            </Modal>
        </React.Fragment>
    );

};
