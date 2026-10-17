package com.example.lab3;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(BusinessOperationsTests.TestClockConfig.class)
class BusinessOperationsTests {

    static final Instant START = Instant.parse("2030-01-01T00:00:00Z");

    static class MutableClock extends Clock {
        private Instant now = START;

        void set(Instant instant) { this.now = instant; }
        void advance(Duration duration) { this.now = now.plus(duration); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;
    @PersistenceContext EntityManager em;

    private int seq;

    @BeforeEach
    void resetClock() {
        clock.set(START);
    }

    private long create(String url, String body) throws Exception {
        String response = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void postExpect(String url, int expectedStatus) throws Exception {
        mvc.perform(post(url)).andExpect(status().is(expectedStatus));
    }

    private long user() throws Exception {
        int n = ++seq;
        long unique = System.nanoTime();
        return create("/api/users", "{\"username\":\"u" + unique + "_" + n + "\",\"email\":\"u" + unique + "_" + n + "@example.com\"}");
    }

    private long podcast() throws Exception {
        return create("/api/podcasts", "{\"title\":\"Podcast\",\"authorId\":" + user() + "}");
    }

    private long episode(long podcastId) throws Exception {
        return create("/api/episodes", "{\"podcastId\":" + podcastId + ",\"title\":\"Episode\",\"durationSeconds\":600}");
    }

    private long audio(long episodeId, String path, boolean main) throws Exception {
        return create("/api/audio-files", "{\"episodeId\":" + episodeId + ",\"path\":\"" + path
                + "\",\"format\":\"MP3\",\"sizeBytes\":1024,\"main\":" + main + "}");
    }

    private long slot(long podcastId, long episodeId, Duration fromNow) throws Exception {
        return create("/api/publication-slots", "{\"podcastId\":" + podcastId + ",\"episodeId\":" + episodeId
                + ",\"scheduledAt\":\"" + clock.instant().plus(fromNow) + "\"}");
    }

    private void expectEpisodeState(long episodeId, String state) throws Exception {
        mvc.perform(get("/api/episodes/" + episodeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(state));
    }

    @Test
    void scheduleAndPublishSlotDependsOnTimeAndMainAudio() throws Exception {
        long podcast = podcast();
        long episode = episode(podcast);
        long slot = slot(podcast, episode, Duration.ofHours(1));
        expectEpisodeState(episode, "SCHEDULED");

        postExpect("/api/publication-slots/" + slot + "/publish", 409);

        clock.advance(Duration.ofHours(2));
        postExpect("/api/publication-slots/" + slot + "/publish", 409);

        long file = audio(episode, "audio/ep.mp3", false);
        postExpect("/api/publication-slots/" + slot + "/publish", 409);
        postExpect("/api/audio-files/" + file + "/make-main", 200);

        mvc.perform(post("/api/publication-slots/" + slot + "/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").exists());
        expectEpisodeState(episode, "PUBLISHED");

        postExpect("/api/publication-slots/" + slot + "/publish", 409);
        postExpect("/api/publication-slots/" + slot + "/cancel", 409);
    }

    @Test
    void cancelSlotReturnsEpisodeToDraftAndAllowsRescheduling() throws Exception {
        long podcast = podcast();
        long episode = episode(podcast);
        long slot = slot(podcast, episode, Duration.ofHours(1));

        mvc.perform(post("/api/publication-slots").contentType(MediaType.APPLICATION_JSON)
                .content("{\"podcastId\":" + podcast + ",\"episodeId\":" + episode
                        + ",\"scheduledAt\":\"" + clock.instant().plus(Duration.ofHours(5)) + "\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/publication-slots/" + slot + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        expectEpisodeState(episode, "DRAFT");

        postExpect("/api/publication-slots/" + slot + "/cancel", 409);
        slot(podcast, episode, Duration.ofHours(3));
        expectEpisodeState(episode, "SCHEDULED");
    }

    @Test
    void publishNowRequiresMainAudioAndWorksOnlyOnce() throws Exception {
        long podcast = podcast();
        long episode = episode(podcast);
        postExpect("/api/episodes/" + episode + "/publish-now", 409);

        long file = audio(episode, "audio/now.mp3", true);
        mvc.perform(post("/api/episodes/" + episode + "/publish-now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episode.state").value("PUBLISHED"))
                .andExpect(jsonPath("$.slot.status").value("PUBLISHED"));

        postExpect("/api/episodes/" + episode + "/publish-now", 409);
        postExpect("/api/audio-files/" + file + "/make-main", 409);
        mvc.perform(delete("/api/audio-files/" + file)).andExpect(status().isConflict());
    }

    @Test
    void publishNowPublishesPlannedSlotEarly() throws Exception {
        long podcast = podcast();
        long episode = episode(podcast);
        audio(episode, "audio/early.mp3", true);
        long slot = slot(podcast, episode, Duration.ofDays(7));

        mvc.perform(post("/api/episodes/" + episode + "/publish-now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slot.id").value(slot))
                .andExpect(jsonPath("$.slot.status").value("PUBLISHED"));
        expectEpisodeState(episode, "PUBLISHED");
    }

    @Test
    void makeMainSwitchesPreviousMainFile() throws Exception {
        long podcast = podcast();
        long episode = episode(podcast);
        long first = audio(episode, "audio/a.mp3", true);
        long second = audio(episode, "audio/b.mp3", false);

        postExpect("/api/audio-files/" + first + "/make-main", 409);
        postExpect("/api/audio-files/" + second + "/make-main", 200);

        mvc.perform(get("/api/audio-files/" + first)).andExpect(jsonPath("$.main").value(false));
        mvc.perform(get("/api/audio-files/" + second)).andExpect(jsonPath("$.main").value(true));

        long third = audio(episode, "audio/c.mp3", true);
        mvc.perform(get("/api/audio-files/" + second)).andExpect(jsonPath("$.main").value(false));
        mvc.perform(get("/api/audio-files/" + third)).andExpect(jsonPath("$.main").value(true));
    }

    @Test
    void mainFileOfScheduledEpisodeCannotBeDeleted() throws Exception {
        long podcast = podcast();
        long episode = episode(podcast);
        long file = audio(episode, "audio/keep.mp3", true);
        slot(podcast, episode, Duration.ofHours(1));

        mvc.perform(delete("/api/audio-files/" + file)).andExpect(status().isConflict());
    }

    @Test
    void archivePodcastCancelsPlannedSlotsAndRestoreReopensIt() throws Exception {
        long podcast = podcast();
        long scheduled = episode(podcast);
        long draft = episode(podcast);
        long slot = slot(podcast, scheduled, Duration.ofDays(1));

        mvc.perform(post("/api/podcasts/" + podcast + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podcast.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.cancelledSlots").value(1))
                .andExpect(jsonPath("$.episodesReturnedToDraft").value(1));

        mvc.perform(get("/api/publication-slots/" + slot)).andExpect(jsonPath("$.status").value("CANCELLED"));
        expectEpisodeState(scheduled, "DRAFT");
        expectEpisodeState(draft, "DRAFT");

        mvc.perform(post("/api/episodes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"podcastId\":" + podcast + ",\"title\":\"New\",\"durationSeconds\":60}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/publication-slots").contentType(MediaType.APPLICATION_JSON)
                .content("{\"podcastId\":" + podcast + ",\"episodeId\":" + draft
                        + ",\"scheduledAt\":\"" + clock.instant().plus(Duration.ofDays(2)) + "\"}"))
                .andExpect(status().isConflict());
        postExpect("/api/podcasts/" + podcast + "/archive", 409);

        mvc.perform(post("/api/podcasts/" + podcast + "/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        postExpect("/api/podcasts/" + podcast + "/restore", 409);
        slot(podcast, draft, Duration.ofDays(2));
    }

    @Test
    void slotInThePastIsRejected() throws Exception {
        long podcast = podcast();
        long episode = episode(podcast);
        mvc.perform(post("/api/publication-slots").contentType(MediaType.APPLICATION_JSON)
                .content("{\"podcastId\":" + podcast + ",\"episodeId\":" + episode
                        + ",\"scheduledAt\":\"" + clock.instant().minus(Duration.ofMinutes(1)) + "\"}"))
                .andExpect(status().isBadRequest());
        expectEpisodeState(episode, "DRAFT");
    }

    @Test
    void episodeMustBelongToSlotPodcast() throws Exception {
        long podcastA = podcast();
        long podcastB = podcast();
        long episodeOfB = episode(podcastB);
        mvc.perform(post("/api/publication-slots").contentType(MediaType.APPLICATION_JSON)
                .content("{\"podcastId\":" + podcastA + ",\"episodeId\":" + episodeOfB
                        + ",\"scheduledAt\":\"" + clock.instant().plus(Duration.ofHours(1)) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void usersAreUniqueAndValidated() throws Exception {
        long unique = System.nanoTime();
        String body = "{\"username\":\"dup" + unique + "\",\"email\":\"dup" + unique + "@example.com\"}";
        long id = create("/api/users", body);

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/users/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"renamed" + unique + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("renamed" + unique));
        mvc.perform(get("/api/users/999999999")).andExpect(status().isNotFound());
    }

    @Test
    void audioPathTraversalIsRejected() throws Exception {
        long episode = episode(podcast());
        mvc.perform(post("/api/audio-files").contentType(MediaType.APPLICATION_JSON)
                .content("{\"episodeId\":" + episode + ",\"path\":\"../etc/passwd\",\"format\":\"MP3\",\"sizeBytes\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingUserCascadesToEverythingTheyOwn() throws Exception {
        long userId = user();
        long podcast = create("/api/podcasts", "{\"title\":\"Owned\",\"authorId\":" + userId + "}");
        long episode = episode(podcast);
        long file = audio(episode, "audio/owned.mp3", true);
        long slot = slot(podcast, episode, Duration.ofHours(1));

        em.flush();
        em.clear();

        mvc.perform(delete("/api/users/" + userId)).andExpect(status().isNoContent());
        em.flush();
        em.clear();

        mvc.perform(get("/api/podcasts/" + podcast)).andExpect(status().isNotFound());
        mvc.perform(get("/api/episodes/" + episode)).andExpect(status().isNotFound());
        mvc.perform(get("/api/audio-files/" + file)).andExpect(status().isNotFound());
        mvc.perform(get("/api/publication-slots/" + slot)).andExpect(status().isNotFound());
    }

    @Test
    void publishedSlotCannotBeChangedOrDeleted() throws Exception {
        long podcast = podcast();
        long episode = episode(podcast);
        audio(episode, "audio/p.mp3", true);
        long slot = slot(podcast, episode, Duration.ofMinutes(10));
        clock.advance(Duration.ofMinutes(20));
        postExpect("/api/publication-slots/" + slot + "/publish", 200);

        mvc.perform(patch("/api/publication-slots/" + slot).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scheduledAt\":\"" + clock.instant().plus(Duration.ofDays(1)) + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/publication-slots/" + slot)).andExpect(status().isConflict());
    }
}
