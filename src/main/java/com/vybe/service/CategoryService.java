package com.vybe.service;

import com.vybe.entity.Category;
import com.vybe.exception.BadRequestException;
import com.vybe.exception.NotFoundException;
import com.vybe.repository.CategoryRepository;
import com.vybe.repository.VideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class CategoryService {

    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern ICON = Pattern.compile("^[a-z0-9-]{1,40}$");
    private static final Map<Character, String> TRANSLIT = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"), Map.entry('д', "d"),
            Map.entry('е', "e"), Map.entry('ё', "e"), Map.entry('ж', "zh"), Map.entry('з', "z"), Map.entry('и', "i"),
            Map.entry('й', "y"), Map.entry('к', "k"), Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"),
            Map.entry('о', "o"), Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"),
            Map.entry('у', "u"), Map.entry('ф', "f"), Map.entry('х', "h"), Map.entry('ц', "c"), Map.entry('ч', "ch"),
            Map.entry('ш', "sh"), Map.entry('щ', "sch"), Map.entry('ъ', ""), Map.entry('ы', "y"), Map.entry('ь', ""),
            Map.entry('э', "e"), Map.entry('ю', "yu"), Map.entry('я', "ya"));

    private final CategoryRepository categories;
    private final VideoRepository videos;

    public CategoryService(CategoryRepository categories, VideoRepository videos) {
        this.categories = categories;
        this.videos = videos;
    }

    public List<Category> all() {
        return categories.findAllByOrderBySortOrderAscNameAsc();
    }

    public Category get(Long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Категория не найдена"));
    }

    public Category bySlug(String slug) {
        return categories.findBySlug(slug).orElseThrow(() -> new NotFoundException("Категория не найдена"));
    }

    @Transactional
    public Category save(Long id, String name, String icon, String color, int sortOrder) {
        if (name == null || name.isBlank() || name.trim().length() > 50) {
            throw new BadRequestException("Название категории: от 1 до 50 символов");
        }
        Category c = id == null ? new Category() : get(id);
        String trimmed = name.trim();
        if (id == null && categories.existsByNameIgnoreCase(trimmed)) {
            throw new BadRequestException("Такая категория уже существует");
        }
        c.setName(trimmed);
        if (c.getSlug() == null) {
            String base = slugify(trimmed);
            String slug = base;
            int n = 2;
            while (categories.existsBySlug(slug)) slug = base + "-" + n++;
            c.setSlug(slug);
        }
        c.setIcon(icon != null && ICON.matcher(icon.trim()).matches() ? icon.trim() : "folder");
        c.setColor(color != null && COLOR.matcher(color.trim()).matches() ? color.trim() : "#8b5cf6");
        c.setSortOrder(sortOrder);
        return categories.save(c);
    }

    @Transactional
    public void delete(Long id) {
        Category c = get(id);
        videos.clearCategory(c);
        categories.delete(c);
    }

    public static String slugify(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toLowerCase(Locale.ROOT).toCharArray()) {
            String t = TRANSLIT.get(ch);
            sb.append(t != null ? t : String.valueOf(ch));
        }
        String n = Normalizer.normalize(sb.toString(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return n.isEmpty() ? "category" : n;
    }
}
