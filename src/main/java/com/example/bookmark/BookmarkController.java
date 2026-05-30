package com.example.bookmark;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BookmarkController {
    private static final int TITLE_MAX_LENGTH = 100;
    private static final int DESCRIPTION_MAX_LENGTH = 300;

    private final BookmarkRepository bookmarkRepository;
    private final BookmarkMetadataFetcher metadataFetcher;

    public BookmarkController(BookmarkRepository bookmarkRepository, BookmarkMetadataFetcher metadataFetcher) {
        this.bookmarkRepository = bookmarkRepository;
        this.metadataFetcher = metadataFetcher;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("bookmarks", bookmarkRepository.findAll());
        return "index";
    }

    @PostMapping("/bookmarks")
    public String add(@RequestParam String url, RedirectAttributes redirectAttributes) {
        BookmarkMetadata metadata;
        try {
            metadata = metadataFetcher.fetch(url);
        } catch (IllegalArgumentException | IOException ex) {
            redirectAttributes.addFlashAttribute("error", "URLを確認してください。");
            redirectAttributes.addFlashAttribute("url", url);
            return "redirect:/";
        }

        try {
            bookmarkRepository.add(
                    metadata.title(),
                    metadata.url(),
                    null,
                    null,
                    metadata.ogpImageUrl()
            );
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "ブックマークを保存できませんでした。");
            redirectAttributes.addFlashAttribute("url", url);
            return "redirect:/";
        }
        return "redirect:/";
    }

    @GetMapping("/bookmarks/{id}/edit")
    public String edit(@PathVariable long id, Model model, RedirectAttributes redirectAttributes) {
        return bookmarkRepository.findById(id)
                .map(bookmark -> {
                    if (!model.containsAttribute("title")) {
                        model.addAttribute("title", bookmark.title());
                    }
                    if (!model.containsAttribute("description")) {
                        model.addAttribute("description", bookmark.description());
                    }
                    if (!model.containsAttribute("tags")) {
                        model.addAttribute("tags", bookmark.tags());
                    }
                    model.addAttribute("bookmark", bookmark);
                    return "edit";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "ブックマークが見つかりません。");
                    return "redirect:/";
                });
    }

    @PostMapping("/bookmarks/{id}")
    public String update(
            @PathVariable long id,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String tags,
            RedirectAttributes redirectAttributes
    ) {
        if (bookmarkRepository.findById(id).isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "ブックマークが見つかりません。");
            return "redirect:/";
        }

        BookmarkForm form = normalize(title, description, tags);
        String validationError = validate(form);
        if (validationError != null) {
            redirectAttributes.addFlashAttribute("error", validationError);
            redirectAttributes.addFlashAttribute("title", title);
            redirectAttributes.addFlashAttribute("description", description);
            redirectAttributes.addFlashAttribute("tags", tags);
            return "redirect:/bookmarks/" + id + "/edit";
        }

        bookmarkRepository.update(id, form.title(), form.description(), form.tags());
        return "redirect:/";
    }

    @PostMapping("/bookmarks/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirectAttributes) {
        if (!bookmarkRepository.delete(id)) {
            redirectAttributes.addFlashAttribute("error", "ブックマークが見つかりません。");
        }
        return "redirect:/";
    }

    private static BookmarkForm normalize(String title, String description, String tags) {
        return new BookmarkForm(
                trimToNull(title),
                trimToNull(description),
                normalizeTags(tags)
        );
    }

    private static String validate(BookmarkForm form) {
        if (form.title() == null) {
            return "タイトルを入力してください。";
        }
        if (codePointLength(form.title()) > TITLE_MAX_LENGTH) {
            return "タイトルは100文字以下で入力してください。";
        }
        if (form.description() != null && codePointLength(form.description()) > DESCRIPTION_MAX_LENGTH) {
            return "メモは300文字以下で入力してください。";
        }
        return null;
    }

    private static String normalizeTags(String tags) {
        if (tags == null) {
            return null;
        }
        String normalized = Arrays.stream(tags.split(","))
                .map(BookmarkController::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(","));
        return normalized.isEmpty() ? null : normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private record BookmarkForm(String title, String description, String tags) {
    }
}
