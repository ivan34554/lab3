package com.example.lab3.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.example.lab3.dto.PublicationSlotDto;
import com.example.lab3.model.PublicationSlot;
import com.example.lab3.service.PublicationSlotService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/publication-slots")
public class PublicationSlotController {

    private final PublicationSlotService service;

    public PublicationSlotController(PublicationSlotService service) {
        this.service = service;
    }

    @GetMapping
    public List<PublicationSlotDto.Response> getAll() {
        return service.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public PublicationSlotDto.Response getById(@PathVariable Long id) {
        return toResponse(service.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublicationSlotDto.Response create(@Valid @RequestBody PublicationSlotDto.CreateRequest request) {
        return toResponse(service.create(request.podcastId(), request.episodeId(), request.scheduledAt()));
    }

    @PatchMapping("/{id}")
    public PublicationSlotDto.Response update(@PathVariable Long id,
                                              @Valid @RequestBody PublicationSlotDto.UpdateRequest request) {
        return toResponse(service.update(id, request.scheduledAt()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/publish")
    public PublicationSlotDto.Response publish(@PathVariable Long id) {
        return toResponse(service.publish(id));
    }

    @PostMapping("/{id}/cancel")
    public PublicationSlotDto.Response cancel(@PathVariable Long id) {
        return toResponse(service.cancel(id));
    }

    private PublicationSlotDto.Response toResponse(PublicationSlot s) {
        return new PublicationSlotDto.Response(s.getId(), s.getPodcast().getId(), s.getEpisode().getId(),
                s.getScheduledAt(), s.getStatus(), s.getPublishedAt(), s.getCreatedAt());
    }
}
