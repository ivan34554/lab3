package com.example.lab3.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.lab3.model.PublicationSlot;

public interface PublicationSlotRepository extends JpaRepository<PublicationSlot, Long> {

    @Query("select s.episode.id from PublicationSlot s where s.id = :id")
    Optional<Long> findEpisodeId(@Param("id") Long id);

    Optional<PublicationSlot> findByEpisodeIdAndStatus(Long episodeId, PublicationSlot.Status status);

    List<PublicationSlot> findByPodcastIdAndStatus(Long podcastId, PublicationSlot.Status status);
}
