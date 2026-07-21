package ru.mandarin.bamboosmoke;

import java.util.Random;

/**
 * Портит текст сообщения тем сильнее, чем выше уровень обкуренности игрока.
 * Уровень 0 — текст не трогаем. Уровень 10 — почти нечитаемая белиберда.
 */
public final class ChatGarbler {

    private static final Random RANDOM = new Random();
    private static final char[] FILLER_CHARS = {'ы', 'а', 'э', 'у', 'ъ'};
    private static final String[] FILLER_WORDS = {"бааамбук", "пандаааа", "шшш", "хех", "ёпта", "круть"};

    private ChatGarbler() {
    }

    public static String garble(String input, int level) {
        if (level <= 0 || input == null || input.isEmpty()) {
            return input;
        }

        // Вероятность "поломки" каждого символа растёт с уровнем (макс уровень 10 -> до ~70%).
        int corruptChancePercent = Math.min(70, level * 7);

        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isWhitespace(c)) {
                result.append(c);
                continue;
            }

            int roll = RANDOM.nextInt(100);
            if (roll < corruptChancePercent) {
                int effect = RANDOM.nextInt(4);
                switch (effect) {
                    case 0 -> { /* пропустить символ (заикание/проглотил букву) */ }
                    case 1 -> { // задвоить символ
                        result.append(c).append(c);
                    }
                    case 2 -> // заменить на случайную "пьяную" букву
                            result.append(FILLER_CHARS[RANDOM.nextInt(FILLER_CHARS.length)]);
                    default -> { // оставить букву, но capslock-рандом на высоких уровнях
                        if (level >= 6 && RANDOM.nextBoolean()) {
                            result.append(Character.toUpperCase(c));
                        } else {
                            result.append(c);
                        }
                    }
                }
            } else {
                result.append(c);
            }
        }

        // На высоких уровнях иногда вставляем случайное слово-паразит.
        if (level >= 5 && RANDOM.nextInt(100) < (level * 5)) {
            result.append(" ").append(FILLER_WORDS[RANDOM.nextInt(FILLER_WORDS.length)]);
        }

        // На максимуме — шанс полностью потерять мысль.
        if (level >= 9 && RANDOM.nextInt(100) < 25) {
            return "ыыыы... " + FILLER_WORDS[RANDOM.nextInt(FILLER_WORDS.length)] + "... чё я хотел сказать...";
        }

        return result.toString();
    }
}
