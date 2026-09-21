package is.flibusta.client.util;

import is.flibusta.client.data.Book;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BookSorter {

    public enum SortMode {
        DATE_DESC("По дате (сначала новые)"),
        DATE_ASC("По дате (сначала старые)"),
        TITLE_ASC("По названию (А - Я)"),
        TITLE_DESC("По названию (Я - А)"),
        AUTHOR_ASC("По автору (А - Я)"),
        SERIES_NUM_ASC("По порядку в серии (1, 2, 3...)"),
        SIZE_DESC("По размеру (сначала большие)");

        private final String title;

        SortMode(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    public static void sort(List<Book> books, SortMode mode) {
        if (books == null || books.size() <= 1) return;

        switch (mode) {
            case DATE_DESC:
                Collections.sort(books, (b1, b2) -> {
                    // 1. Library date added (when added to user shelf)
                    long added1 = b1.getSortableAddedDate();
                    long added2 = b2.getSortableAddedDate();
                    if (added1 > 0 || added2 > 0) {
                        if (added1 != added2) {
                            return Long.compare(added2, added1); // newest added first
                        }
                    }

                    // 2. Publication year (for author bibliography, search, etc.)
                    int y1 = b1.getNumericYear();
                    int y2 = b2.getNumericYear();
                    if (y1 > 0 || y2 > 0) {
                        if (y1 != y2) {
                            return Integer.compare(y2, y1); // newest year first
                        }
                    }

                    // 3. OPDS updated date (ISO timestamp)
                    String u1 = b1.getUpdatedDate();
                    String u2 = b2.getUpdatedDate();
                    if (u1 != null && u2 != null && !u1.isEmpty() && !u2.isEmpty() && !u1.equals(u2)) {
                        return u2.compareTo(u1);
                    }

                    // 4. Flibusta numeric ID (higher ID = uploaded more recently)
                    long id1 = b1.getNumericId();
                    long id2 = b2.getNumericId();
                    if (id1 != id2) {
                        return Long.compare(id2, id1);
                    }

                    return b1.getTitle().compareToIgnoreCase(b2.getTitle());
                });
                break;

            case DATE_ASC:
                Collections.sort(books, (b1, b2) -> {
                    // 1. Library date added
                    long added1 = b1.getSortableAddedDate();
                    long added2 = b2.getSortableAddedDate();
                    if (added1 > 0 || added2 > 0) {
                        if (added1 != added2) {
                            return Long.compare(added1, added2); // oldest added first
                        }
                    }

                    // 2. Publication year
                    int y1 = b1.getNumericYear();
                    int y2 = b2.getNumericYear();
                    if (y1 > 0 || y2 > 0) {
                        if (y1 != y2) {
                            return Integer.compare(y1, y2); // oldest year first
                        }
                    }

                    // 3. OPDS updated date
                    String u1 = b1.getUpdatedDate();
                    String u2 = b2.getUpdatedDate();
                    if (u1 != null && u2 != null && !u1.isEmpty() && !u2.isEmpty() && !u1.equals(u2)) {
                        return u1.compareTo(u2);
                    }

                    // 4. Flibusta numeric ID
                    long id1 = b1.getNumericId();
                    long id2 = b2.getNumericId();
                    if (id1 != id2) {
                        return Long.compare(id1, id2);
                    }

                    return b1.getTitle().compareToIgnoreCase(b2.getTitle());
                });
                break;

            case TITLE_ASC:
                Collections.sort(books, (b1, b2) -> b1.getTitle().compareToIgnoreCase(b2.getTitle()));
                break;

            case TITLE_DESC:
                Collections.sort(books, (b1, b2) -> b2.getTitle().compareToIgnoreCase(b1.getTitle()));
                break;

            case AUTHOR_ASC:
                Collections.sort(books, (b1, b2) -> {
                    int c = b1.getAuthor().compareToIgnoreCase(b2.getAuthor());
                    if (c != 0) return c;
                    return b1.getTitle().compareToIgnoreCase(b2.getTitle());
                });
                break;

            case SERIES_NUM_ASC:
                Collections.sort(books, (b1, b2) -> {
                    int n1 = b1.getSeriesNumber();
                    int n2 = b2.getSeriesNumber();
                    if (n1 > 0 && n2 > 0 && n1 != n2) {
                        return Integer.compare(n1, n2);
                    }
                    return b1.getTitle().compareToIgnoreCase(b2.getTitle());
                });
                break;

            case SIZE_DESC:
                Collections.sort(books, (b1, b2) -> Long.compare(b2.getNumericSize(), b1.getNumericSize()));
                break;
        }
    }
}
