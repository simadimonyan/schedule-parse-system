package app.repository.models.dto.api.version;

import java.time.Instant;

public record VersionResponse(
        Long id,
        String name,
        Boolean isActive,
        Boolean isDraft,
        Instant createdAt,
        Instant updatedAt
) {}
