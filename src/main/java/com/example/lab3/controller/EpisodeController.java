package com.example.lab3.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.example.lab3.dto.EpisodeDto;
import com.example.lab3.dto.PublicationSlotDto;
import com.example.lab3.model.Episode;
import com.example.lab3.model.PublicationSlot;
import com.example.lab3.service.EpisodeService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/episodes")
public class EpisodeController {

    private final EpisodeService service;

    public EpisodeController(EpisodeService service) {
        this.service = service;
    }

    @GetMapping
    public List<EpisodeDto.Response> getAll() {
        return service.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public EpisodeDto.Response getById(@PathVariable Long id) {
        return toResponse(service.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EpisodeDto.Response create(@Valid @RequestBody EpisodeDto.CreateRequest request) {
        return toResponse(service.create(request.podcastId(), request.title(), request.durationSeconds()));
    }

    @PatchMapping("/{id}")
    public EpisodeDto.Response update(@PathVariable Long id, @Valid @RequestBody EpisodeDto.UpdateRequest request) {
        return toResponse(service.update(id, request.title(), request.durationSeconds()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/publish-now")
    public EpisodeDto.PublishResponse publishNow(@PathVariable Long id) {
        EpisodeService.PublishResult result = service.publishNow(id);
        return new EpisodeDto.PublishResponse(toResponse(result.episode()), toResponse(result.slot()));
    }

    private EpisodeDto.Response toResponse(Episode e) {
        return new EpisodeDto.Response(e.getId(), e.getPodcast().getId(), e.getTitle(),
                e.getDurationSeconds(), e.getState(), e.getCreatedAt());
    }

    private PublicationSlotDto.Response toResponse(PublicationSlot s) {
        return new PublicationSlotDto.Response(s.getId(), s.getPodcast().getId(), s.getEpisode().getId(),
                s.getScheduledAt(), s.getStatus(), s.getPublishedAt(), s.getCreatedAt());
    }
}
