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
import com.example.lab3.repository.AudioFileRepository;
import com.example.lab3.repository.PublicationSlotRepository;

@Service
@Transactional
public class PublicationSlotService {

    private record Locked(Episode episode, PublicationSlot slot) {}

    private final PublicationSlotRepository slots;
    private final AudioFileRepository audioFiles;
    private final EntityLocks locks;
    private final Clock clock;

    public PublicationSlotService(PublicationSlotRepository slots, AudioFileRepository audioFiles,
                                  EntityLocks locks, Clock clock) {
        this.slots = slots;
        this.audioFiles = audioFiles;
        this.locks = locks;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PublicationSlot> findAll() {
        return slots.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public PublicationSlot findById(Long id) {
        return slots.findById(id)
                .orElseThrow(() -> new NotFoundException("Publication slot " + id + " not found"));
    }

    public PublicationSlot create(Long podcastId, Long episodeId, Instant scheduledAt) {
        Podcast podcast = locks.lockPodcastShared(podcastId)
                .orElseThrow(() -> new BadRequestException("Podcast " + podcastId + " does not exist"));
        Episode episode = locks.lockEpisode(episodeId)
                .orElseThrow(() -> new BadRequestException("Episode " + episodeId + " does not exist"));
        if (!episode.getPodcast().getId().equals(podcastId)) {
            throw new BadRequestException("Episode " + episodeId + " does not belong to podcast " + podcastId);
        }
        requireFuture(scheduledAt);
        if (podcast.getStatus() != Podcast.Status.ACTIVE) {
            throw new ConflictException("Podcast " + podcastId + " is archived");
        }
        if (episode.getState() != Episode.State.DRAFT) {
            throw new ConflictException("Episode " + episodeId + " is " + episode.getState()
                    + ", only a DRAFT episode can be scheduled");
        }
        PublicationSlot slot = new PublicationSlot();
        slot.setPodcast(podcast);
        slot.setEpisode(episode);
        slot.setScheduledAt(scheduledAt);
        slot.setStatus(PublicationSlot.Status.PLANNED);
        slot.setCreatedAt(Instant.now(clock));
        slot = slots.save(slot);
        episode.setState(Episode.State.SCHEDULED);
        return slot;
    }

    public PublicationSlot update(Long id, Instant scheduledAt) {
        PublicationSlot slot = lock(id).slot();
        if (slot.getStatus() != PublicationSlot.Status.PLANNED) {
            throw new ConflictException("Only a PLANNED slot can be changed, slot " + id + " is " + slot.getStatus());
        }
        if (scheduledAt != null) {
            requireFuture(scheduledAt);
            slot.setScheduledAt(scheduledAt);
        }
        return slot;
    }

    public void delete(Long id) {
        Locked locked = lock(id);
        if (locked.slot().getStatus() == PublicationSlot.Status.PUBLISHED) {
            throw new ConflictException("Published slot cannot be deleted");
        }
        if (locked.slot().getStatus() == PublicationSlot.Status.PLANNED) {
            locked.episode().setState(Episode.State.DRAFT);
        }
        slots.delete(locked.slot());
    }

    public PublicationSlot publish(Long id) {
        Locked locked = lock(id);
        PublicationSlot slot = locked.slot();
        Episode episode = locked.episode();
        if (slot.getStatus() != PublicationSlot.Status.PLANNED) {
            throw new ConflictException("Only a PLANNED slot can be published, slot " + id + " is " + slot.getStatus());
        }
        if (episode.getPodcast().getStatus() != Podcast.Status.ACTIVE) {
            throw new ConflictException("Podcast of slot " + id + " is archived");
        }
        Instant now = Instant.now(clock);
        if (slot.getScheduledAt().isAfter(now)) {
            throw new ConflictException("Slot time has not come yet");
        }
        if (!audioFiles.existsByEpisodeIdAndMainTrue(episode.getId())) {
            throw new ConflictException("Episode " + episode.getId() + " has no main audio file");
        }
        slot.setStatus(PublicationSlot.Status.PUBLISHED);
        slot.setPublishedAt(now);
        episode.setState(Episode.State.PUBLISHED);
        return slot;
    }

    public PublicationSlot cancel(Long id) {
        Locked locked = lock(id);
        PublicationSlot slot = locked.slot();
        if (slot.getStatus() != PublicationSlot.Status.PLANNED) {
            throw new ConflictException("Only a PLANNED slot can be cancelled, slot " + id + " is " + slot.getStatus());
        }
        slot.setStatus(PublicationSlot.Status.CANCELLED);
        locked.episode().setState(Episode.State.DRAFT);
        return slot;
    }

    private Locked lock(Long slotId) {
        Long episodeId = slots.findEpisodeId(slotId)
                .orElseThrow(() -> new NotFoundException("Publication slot " + slotId + " not found"));
        Episode episode = locks.lockEpisode(episodeId)
                .orElseThrow(() -> new NotFoundException("Publication slot " + slotId + " not found"));
        PublicationSlot slot = slots.findById(slotId)
                .orElseThrow(() -> new NotFoundException("Publication slot " + slotId + " not found"));
        return new Locked(episode, slot);
    }

    private void requireFuture(Instant scheduledAt) {
        if (!scheduledAt.isAfter(Instant.now(clock))) {
            throw new BadRequestException("scheduledAt must be in the future");
        }
    }
}
