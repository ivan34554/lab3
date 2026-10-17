package com.example.lab3.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.example.lab3.dto.UserDto;
import com.example.lab3.model.User;
import com.example.lab3.service.UserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserDto.Response> getAll() {
        return service.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public UserDto.Response getById(@PathVariable Long id) {
        return toResponse(service.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto.Response create(@Valid @RequestBody UserDto.CreateRequest request) {
        return toResponse(service.create(request.username(), request.email()));
    }

    @PatchMapping("/{id}")
    public UserDto.Response update(@PathVariable Long id, @Valid @RequestBody UserDto.UpdateRequest request) {
        return toResponse(service.update(id, request.username(), request.email()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    private UserDto.Response toResponse(User u) {
        return new UserDto.Response(u.getId(), u.getUsername(), u.getEmail(), u.getCreatedAt());
    }
}
