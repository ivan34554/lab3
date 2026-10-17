package com.example.lab3.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "audio_files")
public class AudioFile extends BaseEntity {
    public enum Format { MP3, WAV, OGG, FLAC, AAC }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id", nullable = false, updatable = false)
    private Episode episode;

    @Column(nullable = false, length = 255)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Format format;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "is_main", nullable = false)
    private boolean main;
}
