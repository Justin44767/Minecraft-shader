package verity.book;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.WrittenBookContent;

public final class VerityWarningBookManager {
    private static final List<Filterable<Component>> PAGES = load();
    private static List<Filterable<Component>> load() {
        try (InputStream stream = VerityWarningBookManager.class.getResourceAsStream("/assets/verity/book/de_de.txt")) {
            if (stream == null) throw new IOException("Missing warning guide");
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            List<String> pages = Arrays.stream(text.split("\n---PAGE---\n")).map(String::strip).toList();
            if (pages.size() != 10 || pages.stream().anyMatch(p -> p.isBlank() || p.length() > 1024))
                throw new IOException("Warning guide must contain 10 non-empty pages, each <= 1024 characters");
            return pages.stream().map(s -> Filterable.<Component>passThrough(Component.literal(s))).toList();
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    public static ItemStack create() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("VERITY – WARNING GUIDE"), "UNKNOWN", 0, PAGES, true));
        book.set(DataComponents.CUSTOM_NAME, Component.literal("VERITY – WARNING GUIDE"));
        return book;
    }
}
