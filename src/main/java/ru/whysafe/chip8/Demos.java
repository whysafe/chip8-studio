package ru.whysafe.chip8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Demo programs bundled as assembler sources, so every byte of them is readable and editable. */
public final class Demos {

    public static final class Demo {
        public final String key;
        public final String title;
        public final String hint;

        Demo(String key, String title, String hint) {
            this.key = key;
            this.title = title;
            this.hint = hint;
        }

        public String fileName() {
            return key + ".c8s";
        }

        public String source() {
            return readResource("/demos/" + fileName());
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private static final List<Demo> ALL = Collections.unmodifiableList(Arrays.asList(
        new Demo("squash", "Сквош", "A / D — двигать платформу"),
        new Demo("maze", "Лабиринт", "любая клавиша — новый лабиринт"),
        new Demo("bounce", "Отскок", "анимация на таймере задержки"),
        new Demo("keys", "Клавиши", "нажмите клавишу — увидите её код")));

    private Demos() {
    }

    public static List<Demo> all() {
        return ALL;
    }

    public static Demo find(String key) {
        for (Demo demo : ALL) {
            if (demo.key.equalsIgnoreCase(key)) {
                return demo;
            }
        }
        throw new IllegalArgumentException("Нет такого демо: " + key + ". Есть: squash, maze, bounce, keys");
    }

    private static String readResource(String path) {
        try (InputStream in = Demos.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Ресурс не найден: " + path);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать " + path, e);
        }
    }
}
