package com.example.bookmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BookmarkControllerTest {
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        try {
            Path tempDbFile = Files.createTempFile("bookmark-controller-test", ".db");
            tempDbFile.toFile().deleteOnExit();
            registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDbFile.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary database file", e);
        }
    }
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM bookmarks");
    }

    @Test
    void editPageShowsExistingBookmark() throws Exception {
        long id = insertBookmark("Original title", "https://example.com", "memo", "Java,Spring");

        mockMvc.perform(get("/bookmarks/{id}/edit", id))
                .andExpect(status().isOk())
                .andExpect(view().name("edit"))
                .andExpect(model().attributeExists("bookmark"))
                .andExpect(model().attribute("title", "Original title"))
                .andExpect(model().attribute("description", "memo"))
                .andExpect(model().attribute("tags", "Java,Spring"));
    }

    @Test
    void indexShowsDescriptionAndSplitTags() throws Exception {
        insertBookmark("Original title", "https://example.com", "memo", "Java,Spring");

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("memo")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Java")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Spring")));
    }

    @Test
    void updateBookmarkNormalizesInputAndRedirectsToIndex() throws Exception {
        long id = insertBookmark("Original title", "https://example.com", null, null);

        mockMvc.perform(post("/bookmarks/{id}", id)
                        .param("title", "  Updated title  ")
                        .param("description", "  Updated memo  ")
                        .param("tags", " Java, , Spring Boot,SQLite "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        Bookmark bookmark = findBookmark(id);
        assertThat(bookmark.title()).isEqualTo("Updated title");
        assertThat(bookmark.description()).isEqualTo("Updated memo");
        assertThat(bookmark.tags()).isEqualTo("Java,Spring Boot,SQLite");
    }

    @Test
    void updateBookmarkRejectsBlankTitle() throws Exception {
        long id = insertBookmark("Original title", "https://example.com", "memo", "Java");

        mockMvc.perform(post("/bookmarks/{id}", id)
                        .param("title", "   ")
                        .param("description", "Changed")
                        .param("tags", "Changed"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bookmarks/" + id + "/edit"));

        Bookmark bookmark = findBookmark(id);
        assertThat(bookmark.title()).isEqualTo("Original title");
        assertThat(bookmark.description()).isEqualTo("memo");
        assertThat(bookmark.tags()).isEqualTo("Java");
    }

    @Test
    void updateBookmarkRejectsTooLongDescription() throws Exception {
        long id = insertBookmark("Original title", "https://example.com", null, null);

        mockMvc.perform(post("/bookmarks/{id}", id)
                        .param("title", "Valid title")
                        .param("description", "あ".repeat(301))
                        .param("tags", "Java"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bookmarks/" + id + "/edit"));

        Bookmark bookmark = findBookmark(id);
        assertThat(bookmark.title()).isEqualTo("Original title");
        assertThat(bookmark.description()).isNull();
        assertThat(bookmark.tags()).isNull();
    }

    @Test
    void deleteBookmarkRemovesBookmarkAndRedirectsToIndex() throws Exception {
        long id = insertBookmark("Original title", "https://example.com", null, null);

        mockMvc.perform(post("/bookmarks/{id}/delete", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookmarks WHERE id = ?",
                Integer.class,
                id
        );
        assertThat(count).isZero();
    }

    private long insertBookmark(String title, String url, String description, String tags) {
        jdbcTemplate.update("""
                INSERT INTO bookmarks (title, url, description, tags, ogp_image_url)
                VALUES (?, ?, ?, ?, ?)
                """, title, url, description, tags, null);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM bookmarks WHERE url = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                url
        );
    }

    private Bookmark findBookmark(long id) {
        return jdbcTemplate.queryForObject("""
                SELECT id, title, url, description, tags, ogp_image_url, created_at, updated_at
                FROM bookmarks
                WHERE id = ?
                """, (rs, rowNum) -> new Bookmark(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("url"),
                rs.getString("description"),
                rs.getString("tags"),
                rs.getString("ogp_image_url"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        ), id);
    }
}
