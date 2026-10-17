package com.example.lab3.dto;

import java.time.Instant;
import com.example.lab3.model.AudioFile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class AudioFileDto {
    private AudioFileDto() {}

    private static final String SAFE_PATH = "^(?!.*\\.\\.)(?!/)[\\w./-]+$";

    public record CreateRequest(
        @NotNull Long episodeId,
        @NotBlank @Size(max = 255) @Pattern(regexp = SAFE_PATH, message = "unsafe path") String path,
        @NotNull AudioFile.Format format,
        @NotNull @Positive Long sizeBytes,
        Boolean main) {}

    public record UpdateRequest(
            @Size(min = 1, max = 255) @Pattern(regexp = SAFE_PATH, message = "unsafe path") String path,
            AudioFile.Format format,
            @Positive Long sizeBytes) {}

    public record Response(Long id, Long episodeId, String path, AudioFile.Format format,
                           long sizeBytes, boolean main, Instant createdAt) {}
}
