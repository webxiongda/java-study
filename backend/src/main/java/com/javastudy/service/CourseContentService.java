package com.javastudy.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CourseContentService {
    private static final Map<String, String> LAYER_FILES = Map.of(
        "theory", "01-theory.md",
        "demo", "02-demo.md",
        "check", "03-check.md",
        "project", "04-project-task.md"
    );

    private final Path contentRoot;

    public CourseContentService(@Value("${app.content-root}") String contentRoot) {
        this.contentRoot = Path.of(contentRoot).toAbsolutePath().normalize();
    }

    public List<CourseChapter> listChapters() {
        try {
            var folders = Files.list(contentRoot.resolve("chapters"))
                .filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .sorted(Comparator.comparingInt(name -> Integer.parseInt(name.substring(0, 2))))
                .toList();
            var folderByNo = folders.stream().collect(
                java.util.stream.Collectors.toMap(name -> Integer.parseInt(name.substring(0, 2)), name -> name)
            );
            return Files.readString(contentRoot.resolve("README.md"), StandardCharsets.UTF_8)
                .lines()
                .filter(line -> line.matches("^\\|\\s*\\d{2}\\s*\\|.*"))
                .map(line -> parseChapterRow(line, folderByNo))
                .toList();
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read course markdown files from " + contentRoot, error);
        }
    }

    public CourseChapter getChapter(int chapterNo) {
        return listChapters().stream()
            .filter(chapter -> chapter.no() == chapterNo)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + chapterNo));
    }

    public Map<String, String> readChapterContent(int chapterNo) {
        var chapter = getChapter(chapterNo);
        var content = new LinkedHashMap<String, String>();
        LAYER_FILES.forEach((layer, fileName) -> content.put(layer, readFile(contentRoot.resolve("chapters").resolve(chapter.folder()).resolve(fileName))));
        return content;
    }

    public Path contentRoot() {
        return contentRoot;
    }

    private CourseChapter parseChapterRow(String line, Map<Integer, String> folderByNo) {
        var cells = splitRow(line);
        int no = Integer.parseInt(cells.get(1));
        return new CourseChapter(
            no,
            clean(cells.get(2)),
            clean(cells.get(3)),
            clean(cells.get(4)),
            clean(cells.get(5)),
            clean(cells.get(6)),
            folderByNo.getOrDefault(no, "")
        );
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            return "";
        }
    }

    public static List<String> splitRow(String line) {
        return java.util.Arrays.stream(line.split("\\|", -1)).map(String::trim).toList();
    }

    public static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    public record CourseChapter(int no, String title, String summary, String priority, String hours, String routeStatus, String folder) {
    }
}
