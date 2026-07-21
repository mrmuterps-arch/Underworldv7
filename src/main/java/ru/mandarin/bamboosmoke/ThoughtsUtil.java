package ru.mandarin.bamboosmoke;

import java.util.List;
import java.util.Random;

/**
 * Набор дурацких "мыслей", которые всплывают над баром опыта (через BossBar)
 * в зависимости от уровня обкуренности. Без явного мата — просто чёрный юмор
 * и абсурд, чтобы не вылетало модерацией на серверах с фильтром чата.
 */
public final class ThoughtsUtil {

    private static final Random RANDOM = new Random();

    private static final List<String> LOW = List.of(
            "мысль: а панды вообще норм живут...",
            "мысль: бамбук на вкус как трава. буквально.",
            "мысль: кажется, я слышу цвета",
            "мысль: интересно, а крипер тоже курит?",
            "мысль: жизнь — это майнкрафт без креатива"
    );

    private static final List<String> MID = List.of(
            "мысль: а что если этот мир — чей-то сервер...",
            "мысль: я стал панда на 12%",
            "мысль: моё тело — храм. храм из бамбука",
            "мысль: если упаду в лаву — это судьба или лаг?",
            "мысль: сколько будет 2+2? а зачем считать вообще",
            "мысль: мой инвентарь тоже смотрит на меня"
    );

    private static final List<String> HIGH = List.of(
            "мысль: ЗЕМЛЯ КРУГЛАЯ ИЛИ ЭТО ЧАНК ТАК ЗАГРУЗИЛСЯ",
            "мысль: панда внутри меня хочет на свободу",
            "мысль: я ЕСТЬ бамбук. бамбук ЕСТЬ я",
            "мысль: где я. кто я. что я держу в руке",
            "мысль: сервер шатается или я шатаюсь",
            "мысль: чат это просто шум ветра теперь"
    );

    private ThoughtsUtil() {
    }

    /** Возвращает случайную мысль в зависимости от уровня обкуренности (0-10). */
    public static String randomThought(int level) {
        if (level <= 0) {
            return null;
        }
        List<String> pool;
        if (level <= 3) {
            pool = LOW;
        } else if (level <= 6) {
            pool = MID;
        } else {
            pool = HIGH;
        }
        return pool.get(RANDOM.nextInt(pool.size()));
    }
}
