package com.example.lab3.service;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.example.lab3.model.Episode;
import com.example.lab3.model.Podcast;
import com.example.lab3.repository.EpisodeRepository;
import com.example.lab3.repository.PodcastRepository;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class EntityLocks {

    private final PodcastRepository podcasts;
    private final EpisodeRepository episodes;

    public EntityLocks(PodcastRepository podcasts, EpisodeRepository episodes) {
        this.podcasts = podcasts;
        this.episodes = episodes;
    }

    public Optional<Podcast> lockPodcastShared(Long podcastId) {
        return podcasts.lockShared(podcastId);
    }

    public Optional<Podcast> lockPodcastExclusive(Long podcastId) {
        return podcasts.lockExclusive(podcastId);
    }

    public Optional<Episode> lockEpisode(Long episodeId) {
        Optional<Long> podcastId = episodes.findPodcastId(episodeId);
        if (podcastId.isEmpty()) {
            return Optional.empty();
        }
        podcasts.lockShared(podcastId.get());
        return episodes.lockById(episodeId);
    }

    public List<Episode> lockEpisodesOf(Long podcastId) {
        return episodes.lockAllByPodcastId(podcastId);
    }
}
