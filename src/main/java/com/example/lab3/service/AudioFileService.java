package com.example.lab3.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.lab3.exception.BadRequestException;
import com.example.lab3.exception.ConflictException;
import com.example.lab3.exception.NotFoundException;
import com.example.lab3.model.AudioFile;
import com.example.lab3.model.Episode;
import com.example.lab3.repository.AudioFileRepository;

@Service
@Transactional
public class AudioFileService {

    private final AudioFileRepository audioFiles;
    private final EntityLocks locks;
    private final Clock clock;

    public AudioFileService(AudioFileRepository audioFiles, EntityLocks locks, Clock clock) {
        this.audioFiles = audioFiles;
        this.locks = locks;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AudioFile> findAll() {
        return audioFiles.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public AudioFile findById(Long id) {
        return audioFiles.findById(id)
                .orElseThrow(() -> new NotFoundException("Audio file " + id + " not found"));
    }

    public AudioFile create(Long episodeId, String path, AudioFile.Format format,
                            long sizeBytes, boolean main) {
        Episode episode = locks.lockEpisode(episodeId)
                .orElseThrow(() -> new BadRequestException("Episode " + episodeId + " does not exist"));
        requireNotPublished(episode);
        if (audioFiles.existsByEpisodeIdAndPath(episodeId, path)) {
            throw new ConflictException("Episode " + episodeId + " already has a file with this path");
        }
        if (main) {
            demoteMainFiles(episodeId);
        }
        AudioFile file = new AudioFile();
        file.setEpisode(episode);
        file.setPath(path);
        file.setFormat(format);
        file.setSizeBytes(sizeBytes);
        file.setMain(main);
        file.setCreatedAt(Instant.now(clock));
        return audioFiles.save(file);
    }

    public AudioFile update(Long id, String path, AudioFile.Format format, Long sizeBytes) {
        Episode episode = lockEpisodeOf(id);
        AudioFile file = findById(id);
        requireNotPublished(episode);
        if (path != null) {
            if (audioFiles.existsByEpisodeIdAndPathAndIdNot(episode.getId(), path, id)) {
                throw new ConflictException("Episode " + episode.getId() + " already has a file with this path");
            }
            file.setPath(path);
        }
        if (format != null) file.setFormat(format);
        if (sizeBytes != null) file.setSizeBytes(sizeBytes);
        return file;
    }

    public void delete(Long id) {
        Episode episode = lockEpisodeOf(id);
        AudioFile file = findById(id);
        if (file.isMain() && episode.getState() != Episode.State.DRAFT) {
            throw new ConflictException("Main audio file of a " + episode.getState()
                    + " episode cannot be deleted");
        }
        audioFiles.delete(file);
    }

    public AudioFile makeMain(Long id) {
        Episode episode = lockEpisodeOf(id);
        AudioFile file = findById(id);
        requireNotPublished(episode);
        if (file.isMain()) {
            throw new ConflictException("Audio file " + id + " is already the main file");
        }
        demoteMainFiles(episode.getId());
        file.setMain(true);
        return file;
    }

    private Episode lockEpisodeOf(Long fileId) {
        Long episodeId = audioFiles.findEpisodeId(fileId)
                .orElseThrow(() -> new NotFoundException("Audio file " + fileId + " not found"));
        return locks.lockEpisode(episodeId)
                .orElseThrow(() -> new NotFoundException("Audio file " + fileId + " not found"));
    }

    private void requireNotPublished(Episode episode) {
        if (episode.getState() == Episode.State.PUBLISHED) {
            throw new ConflictException("Episode " + episode.getId() + " is published, its files are frozen");
        }
    }

    private void demoteMainFiles(Long episodeId) {
        List<AudioFile> current = audioFiles.findByEpisodeIdAndMainTrue(episodeId);
        current.forEach(f -> f.setMain(false));
        audioFiles.flush();
    }
}
