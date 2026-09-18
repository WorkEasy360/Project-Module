package com.projectmodule.integration.adapter;

import com.projectmodule.integration.port.DocumentPort;
import com.projectmodule.integration.port.DocumentReference;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Honest placeholder for {@link DocumentPort}: the Document module does not exist yet, so there
 * is nothing to look up. Logs the call and returns empty — it never invents a document.
 */
@Component
public class NoopDocumentAdapter implements DocumentPort {

    private static final Logger log = LoggerFactory.getLogger(NoopDocumentAdapter.class);

    @Override
    public Optional<DocumentReference> findById(String externalDocumentId) {
        log.debug("DocumentPort.findById({}): no Document module integration is configured; "
                + "returning empty rather than a fabricated document", externalDocumentId);
        return Optional.empty();
    }
}
