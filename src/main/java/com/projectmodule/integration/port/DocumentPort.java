package com.projectmodule.integration.port;

import java.util.Optional;

/**
 * Outbound port to the external Document module — the "Document Service" integration point
 * named in {@code docs/project/02-ARCHITECTURE.md}.
 *
 * <p>This module owns no document storage, no upload path and no {@code ProjectComment}
 * attachment model yet (those are later roadmap items). The only operation justified today is
 * resolving a document reference this module might one day hold as an opaque id, mirroring
 * {@link UserDirectoryPort}.
 */
public interface DocumentPort {

    /** Looks up a document by its external id. Empty if not found, including when no real
     *  integration is configured. */
    Optional<DocumentReference> findById(String externalDocumentId);
}
