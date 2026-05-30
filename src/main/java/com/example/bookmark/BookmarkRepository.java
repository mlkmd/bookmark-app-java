package com.example.bookmark;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookmarkRepository {
    private final JdbcTemplate jdbcTemplate;
    private static final org.springframework.jdbc.core.RowMapper<Bookmark> BOOKMARK_ROW_MAPPER = (rs, rowNum) -> new Bookmark(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("url"),
            rs.getString("description"),
            rs.getString("tags"),
            rs.getString("ogp_image_url"),
            rs.getString("created_at"),
            rs.getString("updated_at")
    );

    public BookmarkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Bookmark> findAll() {
        return jdbcTemplate.query("""
                SELECT id, title, url, description, tags, ogp_image_url, created_at, updated_at
                FROM bookmarks
                ORDER BY id DESC
                """, BOOKMARK_ROW_MAPPER);
    }

    public Optional<Bookmark> findById(long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT id, title, url, description, tags, ogp_image_url, created_at, updated_at
                    FROM bookmarks
                    WHERE id = ?
                    """, BOOKMARK_ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public void add(String title, String url, String description, String tags, String ogpImageUrl) {
        jdbcTemplate.update("""
                INSERT INTO bookmarks (title, url, description, tags, ogp_image_url)
                VALUES (?, ?, ?, ?, ?)
                """, title, url, description, tags, ogpImageUrl);
    }

    public boolean update(long id, String title, String description, String tags) {
        int updatedRows = jdbcTemplate.update("""
                UPDATE bookmarks
                SET title = ?, description = ?, tags = ?, updated_at = datetime('now')
                WHERE id = ?
                """, title, description, tags, id);
        return updatedRows > 0;
    }

    public boolean delete(long id) {
        int deletedRows = jdbcTemplate.update("""
                DELETE FROM bookmarks
                WHERE id = ?
                """, id);
        return deletedRows > 0;
    }
}
