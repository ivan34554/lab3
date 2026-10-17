package com.example.lab3.dto;

import java.time.Instant;
import com.example.lab3.model.PublicationSlot;
import jakarta.validation.constraints.NotNull;

public final class PublicationSlotDto {
    private PublicationSlotDto() {}

    public record CreateRequest(
            @NotNull Long podcastId,
            @NotNull Long episodeId,
            @NotNull Instant scheduledAt) {}

    public record UpdateRequest(
            Instant scheduledAt) {}

    public record Response(Long id, Long podcastId, Long episodeId, Instant scheduledAt,
                           PublicationSlot.Status status, Instant publishedAt, Instant createdAt) {}
}
