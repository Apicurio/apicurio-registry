package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.PeerValidator;
import io.apicurio.registry.storage.dto.PeerDto;
import io.apicurio.registry.storage.dto.PeerSearchResultsDto;
import io.apicurio.registry.storage.error.PeerAlreadyExistsException;
import io.apicurio.registry.storage.error.PeerNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.mappers.PeerDtoMapper;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Repository handling peer operations in the SQL storage layer.
 */
public class SqlPeerRepository {

    private final Logger log;

    private final SqlStatements sqlStatements;

    private final HandleFactory handles;

    public SqlPeerRepository(HandleFactory handles, SqlStatements sqlStatements, Logger log) {
        this.handles = handles;
        this.sqlStatements = sqlStatements;
        this.log = log;
    }

    /**
     * Create a new peer.
     */
    public void createPeer(PeerDto peer) throws RegistryStorageException {
        PeerValidator.validate(peer);
        log.debug("Inserting a peer row for: {}", peer.getPeerId());
        try {
            handles.withHandle(handle -> {
                handle.createUpdate(sqlStatements.insertPeer())
                        .bind(0, peer.getPeerId())
                        .bind(1, peer.getUrl())
                        .bind(2, peer.getName())
                        .bind(3, peer.getDescription())
                        .bind(4, peer.isEnabled())
                        .bind(5, peer.getCredentialSecretRef())
                        .execute();
                return null;
            });
        } catch (Exception ex) {
            if (sqlStatements.isPrimaryKeyViolation(ex)) {
                throw new PeerAlreadyExistsException(peer.getPeerId());
            }
            throw ex;
        }
    }

    /**
     * Delete a peer.
     */
    public void deletePeer(String peerId) throws RegistryStorageException {
        log.debug("Deleting a peer row for: {}", peerId);
        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.deletePeer())
                    .bind(0, peerId)
                    .execute();
            if (rowCount == 0) {
                throw new PeerNotFoundException(peerId);
            }
            return null;
        });
    }

    /**
     * Get a peer by ID.
     */
    public PeerDto getPeer(String peerId) throws RegistryStorageException {
        log.debug("Selecting a single peer for: {}", peerId);
        return handles.withHandle(handle -> {
            Optional<PeerDto> res = handle.createQuery(sqlStatements.selectPeerById())
                    .bind(0, peerId)
                    .map(PeerDtoMapper.instance)
                    .findOne();
            return res.orElseThrow(() -> new PeerNotFoundException(peerId));
        });
    }

    /**
     * Get all peers.
     */
    public List<PeerDto> getPeers() throws RegistryStorageException {
        log.debug("Getting a list of all peers.");
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectPeers())
                    .map(PeerDtoMapper.instance)
                    .list();
        });
    }

    /**
     * Search peers with pagination.
     */
    public PeerSearchResultsDto searchPeers(int offset, int limit) throws RegistryStorageException {
        log.debug("Searching peers.");
        return handles.withHandleNoException(handle -> {
            String query = sqlStatements.selectPeers();
            if ("mssql".equals(sqlStatements.dbType())) {
                query += " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            } else {
                query += " LIMIT ? OFFSET ?";
            }
            String countQuery = sqlStatements.countPeers();

            var peersQuery = handle.createQuery(query);
            if ("mssql".equals(sqlStatements.dbType())) {
                peersQuery.bind(0, offset).bind(1, limit);
            } else {
                peersQuery.bind(0, limit).bind(1, offset);
            }
            List<PeerDto> peers = peersQuery.map(PeerDtoMapper.instance).list();

            Integer count = handle.createQuery(countQuery)
                    .mapTo(Integer.class)
                    .one();
            return PeerSearchResultsDto.builder()
                    .count(count)
                    .peers(peers)
                    .build();
        });
    }

    /**
     * Update a peer.
     */
    public void updatePeer(PeerDto peer) throws RegistryStorageException {
        PeerValidator.validate(peer);
        log.debug("Updating a peer: {}", peer.getPeerId());
        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.updatePeer())
                    .bind(0, peer.getUrl())
                    .bind(1, peer.getName())
                    .bind(2, peer.getDescription())
                    .bind(3, peer.isEnabled())
                    .bind(4, peer.getCredentialSecretRef())
                    .bind(5, peer.getPeerId())
                    .execute();
            if (rowCount == 0) {
                throw new PeerNotFoundException(peer.getPeerId());
            }
            return null;
        });
    }

    /**
     * Check if a peer exists.
     */
    public boolean isPeerExists(String peerId) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectPeerCountById())
                    .bind(0, peerId)
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }

}
