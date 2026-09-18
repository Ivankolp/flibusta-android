package is.flibusta.client.data;

import java.util.ArrayList;
import java.util.List;

public class CatalogData {

    public static List<Series> getPopularSeries() {
        List<Series> list = new ArrayList<>();

        // 1. Ведьмак
        Series witcher = new Series("383", "Ведьмак", "Анджей Сапковский", 8);
        witcher.addBook(new Book("82622", "1. Последнее желание", "Анджей Сапковский", "Темное фэнтези", "750 KB", "fb2", "http://flibusta.is/b/82622/fb2"));
        witcher.addBook(new Book("82623", "2. Меч Предназначения", "Анджей Сапковский", "Темное фэнтези", "810 KB", "fb2", "http://flibusta.is/b/82623/fb2"));
        witcher.addBook(new Book("82624", "3. Кровь эльфов", "Анджей Сапковский", "Темное фэнтези", "780 KB", "fb2", "http://flibusta.is/b/82624/fb2"));
        witcher.addBook(new Book("82625", "4. Час Презрения", "Анджей Сапковский", "Темное фэнтези", "850 KB", "fb2", "http://flibusta.is/b/82625/fb2"));
        witcher.addBook(new Book("82626", "5. Крещение огнём", "Анджей Сапковский", "Темное фэнтези", "820 KB", "fb2", "http://flibusta.is/b/82626/fb2"));
        witcher.addBook(new Book("82627", "6. Башня Ласточки", "Анджей Сапковский", "Темное фэнтези", "940 KB", "fb2", "http://flibusta.is/b/82627/fb2"));
        witcher.addBook(new Book("82628", "7. Владычица Озера", "Анджей Сапковский", "Темное фэнтези", "1.1 MB", "fb2", "http://flibusta.is/b/82628/fb2"));
        witcher.addBook(new Book("370258", "8. Сезон гроз", "Анджей Сапковский", "Темное фэнтези", "690 KB", "fb2", "http://flibusta.is/b/370258/fb2"));
        list.add(witcher);

        // 2. Метро 2033
        Series metro = new Series("1091", "Метро", "Дмитрий Глуховский", 3);
        metro.addBook(new Book("42898", "1. Метро 2033", "Дмитрий Глуховский", "Постапокалипсис", "1.3 MB", "fb2", "http://flibusta.is/b/42898/fb2"));
        metro.addBook(new Book("156557", "2. Метро 2034", "Дмитрий Глуховский", "Постапокалипсис", "980 KB", "fb2", "http://flibusta.is/b/156557/fb2"));
        metro.addBook(new Book("409951", "3. Метро 2035", "Дмитрий Глуховский", "Постапокалипсис", "1.1 MB", "fb2", "http://flibusta.is/b/409951/fb2"));
        list.add(metro);

        // 3. Песнь Льда и Пламени
        Series asoiaf = new Series("374", "Песнь Льда и Пламени", "Джордж Мартин", 5);
        asoiaf.addBook(new Book("69315", "1. Игра престолов", "Джордж Мартин", "Эпическое фэнтези", "1.8 MB", "fb2", "http://flibusta.is/b/69315/fb2"));
        asoiaf.addBook(new Book("69316", "2. Битва королей", "Джордж Мартин", "Эпическое фэнтези", "2.1 MB", "fb2", "http://flibusta.is/b/69316/fb2"));
        asoiaf.addBook(new Book("69317", "3. Буря мечей", "Джордж Мартин", "Эпическое фэнтези", "2.4 MB", "fb2", "http://flibusta.is/b/69317/fb2"));
        asoiaf.addBook(new Book("69318", "4. Пир стервятников", "Джордж Мартин", "Эпическое фэнтези", "1.9 MB", "fb2", "http://flibusta.is/b/69318/fb2"));
        asoiaf.addBook(new Book("260381", "5. Танец с драконами", "Джордж Мартин", "Эпическое фэнтези", "2.7 MB", "fb2", "http://flibusta.is/b/260381/fb2"));
        list.add(asoiaf);

        // 4. Дюна
        Series dune = new Series("188", "Хроники Дюны", "Фрэнк Герберт", 6);
        dune.addBook(new Book("12695", "1. Дюна", "Фрэнк Герберт", "Научная фантастика", "1.5 MB", "fb2", "http://flibusta.is/b/12695/fb2"));
        dune.addBook(new Book("12696", "2. Мессия Дюны", "Фрэнк Герберт", "Научная фантастика", "850 KB", "fb2", "http://flibusta.is/b/12696/fb2"));
        dune.addBook(new Book("12697", "3. Дети Дюны", "Фрэнк Герберт", "Научная фантастика", "1.2 MB", "fb2", "http://flibusta.is/b/12697/fb2"));
        dune.addBook(new Book("12698", "4. Бог-император Дюны", "Фрэнк Герберт", "Научная фантастика", "1.3 MB", "fb2", "http://flibusta.is/b/12698/fb2"));
        list.add(dune);

        // 5. Искажающие Реальность (ЛитРПГ)
        Series litrpg = new Series("34190", "Искажающие Реальность", "Михаил Атаманов", 8);
        litrpg.addBook(new Book("485290", "1. Искажающие Реальность", "Михаил Атаманов", "ЛитРПГ", "920 KB", "fb2", "http://flibusta.is/b/485290/fb2"));
        litrpg.addBook(new Book("507640", "2. Искажающие Реальность 2", "Михаил Атаманов", "ЛитРПГ", "950 KB", "fb2", "http://flibusta.is/b/507640/fb2"));
        litrpg.addBook(new Book("525890", "3. Искажающие Реальность 3", "Михаил Атаманов", "ЛитРПГ", "1.0 MB", "fb2", "http://flibusta.is/b/525890/fb2"));
        list.add(litrpg);

        return list;
    }

    public static List<Book> getRecommendedBooks() {
        List<Book> list = new ArrayList<>();

        list.add(new Book("221528", "1984", "Джордж Оруэлл", "Антиутопия", "820 KB", "fb2", "http://flibusta.is/b/221528/fb2"));
        list.add(new Book("380894", "Марсианин", "Энди Вейер", "Твердая научная фантастика", "1.1 MB", "fb2", "http://flibusta.is/b/380894/fb2"));
        list.add(new Book("112450", "Цветы для Элджернона", "Дэниел Киз", "Психологическая фантастика", "680 KB", "fb2", "http://flibusta.is/b/112450/fb2"));
        list.add(new Book("460112", "Задача трёх тел", "Лю Цысинь", "Научная фантастика", "1.4 MB", "fb2", "http://flibusta.is/b/460112/fb2"));
        list.add(new Book("156550", "Автостопом по галактике", "Дуглас Адамс", "Юмористическая фантастика", "590 KB", "fb2", "http://flibusta.is/b/156550/fb2"));
        list.add(new Book("209840", "Шантарам", "Грегори Дэвид Робертс", "Приключения / Роман", "2.8 MB", "fb2", "http://flibusta.is/b/209840/fb2"));
        list.add(new Book("180512", "Мастер и Маргарита", "Михаил Булгаков", "Классика / Мистика", "1.2 MB", "fb2", "http://flibusta.is/b/180512/fb2"));
        list.add(new Book("312450", "S.T.A.L.K.E.R. Зона Поражения", "Василий Орехов", "Боевая фантастика", "890 KB", "fb2", "http://flibusta.is/b/312450/fb2"));

        return list;
    }
}
