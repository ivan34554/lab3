package com.example.lab3.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.lab3.model.Episode;
import jakarta.persistence.LockModeType;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {

    @Query("select e.podcast.id from Episode e where e.id = :id")
    Optional<Long> findPodcastId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Episode e where e.id = :id")
    Optional<Episode> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Episode e where e.podcast.id = :podcastId order by e.id")
    List<Episode> lockAllByPodcastId(@Param("podcastId") Long podcastId);
}
