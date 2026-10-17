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
import com.example.lab3.model.Episode;
import com.example.lab3.model.Podcast;
import com.example.lab3.model.PublicationSlot;
import com.example.lab3.model.User;
import com.example.lab3.repository.PodcastRepository;
import com.example.lab3.repository.PublicationSlotRepository;
import com.example.lab3.repository.UserRepository;

@Service
@Transactional
public class PodcastService {

    public record ArchiveResult(Podcast podcast, int cancelledSlots, int episodesReturnedToDraft) {}

    private final PodcastRepository podcasts;
    private final UserRepository users;
    private final PublicationSlotRepository slots;
    private final EntityLocks locks;
    private final Clock clock;

    public PodcastService(PodcastRepository podcasts, UserRepository users,
                          PublicationSlotRepository slots, EntityLocks locks, Clock clock) {
        this.podcasts = podcasts;
        this.users = users;
        this.slots = slots;
        this.locks = locks;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Podcast> findAll() {
        return podcasts.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Podcast findById(Long id) {
        return podcasts.findById(id)
                .orElseThrow(() -> new NotFoundException("Podcast " + id + " not found"));
    }

    public Podcast create(String title, Long authorId) {
        Podcast podcast = new Podcast();
        podcast.setTitle(title);
        podcast.setAuthor(requireAuthor(authorId));
        podcast.setStatus(Podcast.Status.ACTIVE);
        podcast.setCreatedAt(Instant.now(clock));
        return podcasts.save(podcast);
    }

    public Podcast update(Long id, String title, Long authorId) {
        Podcast podcast = findById(id);
        if (title != null) podcast.setTitle(title);
        if (authorId != null) podcast.setAuthor(requireAuthor(authorId));
        return podcast;
    }

    public void delete(Long id) {
        Podcast podcast = locks.lockPodcastExclusive(id)
                .orElseThrow(() -> new NotFoundException("Podcast " + id + " not found"));
        podcasts.delete(podcast);
    }

    public ArchiveResult archive(Long id) {
        Podcast podcast = locks.lockPodcastExclusive(id)
                .orElseThrow(() -> new NotFoundException("Podcast " + id + " not found"));
        if (podcast.getStatus() != Podcast.Status.ACTIVE) {
            throw new ConflictException("Podcast " + id + " is already archived");
        }
        List<Episode> episodes = locks.lockEpisodesOf(id);
        int cancelled = 0;
        for (PublicationSlot slot : slots.findByPodcastIdAndStatus(id, PublicationSlot.Status.PLANNED)) {
            slot.setStatus(PublicationSlot.Status.CANCELLED);
            cancelled++;
        }
        int reverted = 0;
        for (Episode episode : episodes) {
            if (episode.getState() == Episode.State.SCHEDULED) {
                episode.setState(Episode.State.DRAFT);
                reverted++;
            }
        }
        podcast.setStatus(Podcast.Status.ARCHIVED);
        return new ArchiveResult(podcast, cancelled, reverted);
    }

    public Podcast restore(Long id) {
        Podcast podcast = locks.lockPodcastExclusive(id)
                .orElseThrow(() -> new NotFoundException("Podcast " + id + " not found"));
        if (podcast.getStatus() != Podcast.Status.ARCHIVED) {
            throw new ConflictException("Podcast " + id + " is not archived");
        }
        podcast.setStatus(Podcast.Status.ACTIVE);
        return podcast;
    }

    private User requireAuthor(Long authorId) {
        return users.findById(authorId)
                .orElseThrow(() -> new BadRequestException("Author " + authorId + " does not exist"));
    }
}
