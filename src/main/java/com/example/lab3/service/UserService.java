package com.example.lab3.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.lab3.exception.ConflictException;
import com.example.lab3.exception.NotFoundException;
import com.example.lab3.model.User;
import com.example.lab3.repository.UserRepository;

@Service
@Transactional
public class UserService {

    private final UserRepository users;
    private final Clock clock;

    public UserService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return users.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }

    public User create(String username, String email) {
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Username '" + username + "' is already taken");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("E-mail '" + email + "' is already taken");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setCreatedAt(Instant.now(clock));
        return users.save(user);
    }

    public User update(Long id, String username, String email) {
        User user = findById(id);
        if (username != null) {
            if (users.existsByUsernameIgnoreCaseAndIdNot(username, id)) {
                throw new ConflictException("Username '" + username + "' is already taken");
            }
            user.setUsername(username);
        }
        if (email != null) {
            if (users.existsByEmailIgnoreCaseAndIdNot(email, id)) {
                throw new ConflictException("E-mail '" + email + "' is already taken");
            }
            user.setEmail(email);
        }
        return user;
    }

    public void delete(Long id) {
        users.delete(findById(id));
    }
}
