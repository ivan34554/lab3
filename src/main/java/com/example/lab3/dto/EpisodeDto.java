package com.example.lab3.dto;

import java.time.Instant;
import com.example.lab3.model.Episode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class EpisodeDto {
    private EpisodeDto() {}

    public record CreateRequest(
            @NotNull Long podcastId,
            @NotBlank @Size(max = 200) String title,
            @NotNull @Positive Integer durationSeconds) {}

    public record UpdateRequest(
            @Size(min = 1, max = 200) String title,
            @Positive Integer durationSeconds) {}

    public record Response(Long id, Long podcastId, String title, int durationSeconds,
                           Episode.State state, Instant createdAt) {}

    public record PublishResponse(Response episode, PublicationSlotDto.Response slot) {}
}
