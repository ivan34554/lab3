package com.example.lab3.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.lab3.model.AudioFile;

public interface AudioFileRepository extends JpaRepository<AudioFile, Long> {

    @Query("select a.episode.id from AudioFile a where a.id = :id")
    Optional<Long> findEpisodeId(@Param("id") Long id);

    List<AudioFile> findByEpisodeIdAndMainTrue(Long episodeId);

    boolean existsByEpisodeIdAndMainTrue(Long episodeId);

    boolean existsByEpisodeIdAndPath(Long episodeId, String path);

    boolean existsByEpisodeIdAndPathAndIdNot(Long episodeId, String path, Long id);
}
