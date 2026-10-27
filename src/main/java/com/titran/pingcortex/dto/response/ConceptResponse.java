package com.titran.pingcortex.dto.response;

import com.titran.pingcortex.model.ConceptStatus;

import java.util.UUID;

public record ConceptResponse(
        UUID id,
        String name,
        String description,
        int position,
        ConceptStatus status,
        boolean suggestedCoverage,
        Integer masteryLevel
) {
}
