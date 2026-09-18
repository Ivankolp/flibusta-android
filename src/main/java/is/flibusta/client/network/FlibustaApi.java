package is.flibusta.client.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import is.flibusta.client.data.Book;
import is.flibusta.client.data.GenreItem;
import is.flibusta.client.data.Series;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlibustaApi {
    public static final String BASE_URL = "http://flibusta.is";

    public static String getMirror() {
        return BASE_URL;
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static class CatalogFeed {
        public Book featuredBook;
        public List<Series> popularSeries = new ArrayList<>();
        public List<Book> recommendedBooks = new ArrayList<>();
    }

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    public static void fetchLiveCatalog(Callback<CatalogFeed> callback) {
        executor.execute(() -> {
            try {
                CatalogFeed feed = new CatalogFeed();

                // 1. Fetch live books from OPDS
                String booksXml = fetchString(BASE_URL + "/opds/new/0/new");
                List<Book> liveBooks = parseBooksFromOpds(booksXml);

                if (!liveBooks.isEmpty()) {
                    feed.featuredBook = liveBooks.get(0);
                    if (liveBooks.size() > 1) {
                        feed.recommendedBooks.addAll(liveBooks.subList(1, liveBooks.size()));
                    }
                }

                // 2. Fetch live series from OPDS
                try {
                    String seriesXml = fetchString(BASE_URL + "/opds/newsequences");
                    feed.popularSeries = parseSeriesFromOpds(seriesXml);
                } catch (Exception ignored) {
                    feed.popularSeries = new ArrayList<>();
                }

                mainHandler.post(() -> callback.onSuccess(feed));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void fetchGenres(String url, Callback<List<GenreItem>> callback) {
        executor.execute(() -> {
            try {
                boolean isRoot = (url == null || url.isEmpty() || url.endsWith("/opds/genres"));
                String targetUrl = isRoot ? BASE_URL + "/opds/genres" : url;
                String xml = fetchString(targetUrl);
                List<GenreItem> list = parseGenreItemsFromOpds(xml, isRoot);
                mainHandler.post(() -> callback.onSuccess(list));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void fetchBooksFromUrl(String url, Callback<List<Book>> callback) {
        fetchBooksFromUrl(url, 0, callback);
    }

    public static void fetchBooksFromUrl(String url, int page, Callback<List<Book>> callback) {
        executor.execute(() -> {
            try {
                String targetUrl = url;
                if (page > 0) {
                    if (targetUrl.contains("pageNumber=")) {
                        targetUrl = targetUrl.replaceAll("pageNumber=\\d+", "pageNumber=" + page);
                    } else if (targetUrl.matches(".*/genres/\\d+$")) {
                        targetUrl = targetUrl + "/" + page;
                    } else if (targetUrl.contains("?")) {
                        targetUrl = targetUrl + "&pageNumber=" + page;
                    } else {
                        targetUrl = targetUrl + "/" + page;
                    }
                }
                String xml = fetchString(targetUrl);
                List<Book> list = parseBooksFromOpds(xml);
                mainHandler.post(() -> callback.onSuccess(list));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void searchBooksByAuthor(String authorName, Callback<List<Book>> callback) {
        searchBooksByAuthor(authorName, 0, callback);
    }

    public static void searchBooksByAuthor(String authorName, int page, Callback<List<Book>> callback) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(authorName, "UTF-8");

                // 1. Fast direct OPDS book search with pagination
                try {
                    String opdsUrl = BASE_URL + "/opds/search?searchType=books&searchTerm=" + encoded + "&pageNumber=" + page;
                    String xml = fetchString(opdsUrl);
                    List<Book> opdsBooks = parseBooksFromOpds(xml);
                    if (!opdsBooks.isEmpty()) {
                        for (Book b : opdsBooks) {
                            if (b.getAuthor() == null || b.getAuthor().isEmpty() ||
                                    b.getAuthor().equalsIgnoreCase("Не указан") ||
                                    b.getAuthor().equalsIgnoreCase("Неизвестный автор")) {
                                b.setAuthor(authorName);
                            }
                        }
                        mainHandler.post(() -> callback.onSuccess(opdsBooks));
                        return;
                    }
                } catch (Exception ignored) {}

                // 2. Direct HTML booksearch with chb=on (search books)
                try {
                    String htmlUrl = BASE_URL + "/booksearch?ask=" + encoded + "&chb=on&page=" + page;
                    String html = fetchString(htmlUrl);
                    List<Book> htmlBooks = parseBooksFromHtml(html);
                    if (!htmlBooks.isEmpty()) {
                        for (Book b : htmlBooks) {
                            if (b.getAuthor() == null || b.getAuthor().isEmpty() ||
                                    b.getAuthor().equalsIgnoreCase("Не указан") ||
                                    b.getAuthor().equalsIgnoreCase("Неизвестный автор")) {
                                b.setAuthor(authorName);
                            }
                        }
                        mainHandler.post(() -> callback.onSuccess(htmlBooks));
                        return;
                    }
                } catch (Exception ignored) {}

                // Return empty list immediately without hanging
                mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void searchBooks(String query, Callback<List<Book>> callback) {
        searchBooks(query, 0, callback);
    }

    public static void searchBooks(String query, int page, Callback<List<Book>> callback) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String opdsUrl = BASE_URL + "/opds/search?searchType=books&searchTerm=" + encoded + "&pageNumber=" + page;
                String xml = null;
                try {
                    xml = fetchString(opdsUrl);
                } catch (Exception ignored) {}

                List<Book> books = new ArrayList<>();
                if (xml != null && !xml.isEmpty()) {
                    books = parseBooksFromOpds(xml);
                }

                // Fallback to HTML if OPDS returns empty
                if (books.isEmpty()) {
                    String htmlUrl = BASE_URL + "/booksearch?ask=" + encoded + "&chb=on&page=" + page;
                    try {
                        String html = fetchString(htmlUrl);
                        books = parseBooksFromHtml(html);
                    } catch (Exception ignored) {}
                }

                // Fuzzy fallback on first page if still empty
                if (books.isEmpty() && page == 0) {
                    try {
                        String generalUrl = BASE_URL + "/booksearch?ask=" + encoded;
                        String generalHtml = fetchString(generalUrl);
                        books = parseBooksFromHtml(generalHtml);
                    } catch (Exception ignored) {}
                }

                List<Book> finalBooks = books;
                mainHandler.post(() -> callback.onSuccess(finalBooks));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void searchSeries(String query, Callback<List<Series>> callback) {
        searchSeries(query, 0, callback);
    }

    public static void searchSeries(String query, int page, Callback<List<Series>> callback) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");

                // 1. Try OPDS sequences search
                try {
                    String opdsUrl = BASE_URL + "/opds/search?searchType=sequences&searchTerm=" + encoded + "&pageNumber=" + page;
                    String xml = fetchString(opdsUrl);
                    List<Series> list = parseSeriesFromOpds(xml);
                    if (!list.isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(list));
                        return;
                    }
                } catch (Exception ignored) {}

                // 2. Try HTML booksearch with chs=on
                try {
                    String urlStr = BASE_URL + "/booksearch?ask=" + encoded + "&chs=on&page=" + page;
                    String html = fetchString(urlStr);
                    List<Series> seriesList = parseSeriesFromHtml(html);
                    if (!seriesList.isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(seriesList));
                        return;
                    }
                } catch (Exception ignored) {}

                // 3. Fallback: general booksearch (without filters, finds fuzzy/similar series)
                try {
                    String generalUrl = BASE_URL + "/booksearch?ask=" + encoded + "&page=" + page;
                    String generalHtml = fetchString(generalUrl);
                    List<Series> generalSeries = parseSeriesFromHtml(generalHtml);
                    if (!generalSeries.isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(generalSeries));
                        return;
                    }
                } catch (Exception ignored) {}

                mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void loadSeriesBooks(String seriesId, String defaultAuthor, Callback<List<Book>> callback) {
        loadSeriesBooks(seriesId, 0, defaultAuthor, callback);
    }

    public static void loadSeriesBooks(String seriesId, int page, String defaultAuthor, Callback<List<Book>> callback) {
        executor.execute(() -> {
            try {
                // Try OPDS sequencebooks first
                String opdsUrl = BASE_URL + "/opds/sequencebooks/" + seriesId + (page > 0 ? "&pageNumber=" + page : "");
                try {
                    String xml = fetchString(opdsUrl);
                    List<Book> books = parseBooksFromOpds(xml);
                    if (!books.isEmpty()) {
                        if (defaultAuthor != null && !defaultAuthor.isEmpty()) {
                            for (Book b : books) {
                                if (b.getAuthor() == null || b.getAuthor().isEmpty() ||
                                        b.getAuthor().equalsIgnoreCase("Не указан") ||
                                        b.getAuthor().equalsIgnoreCase("Неизвестный автор")) {
                                    b.setAuthor(defaultAuthor);
                                }
                            }
                        }
                        mainHandler.post(() -> callback.onSuccess(books));
                        return;
                    }
                } catch (Exception ignored) {}

                // Fallback to HTML sequence page
                String urlStr = BASE_URL + "/sequence/" + seriesId + (page > 0 ? "?page=" + page : "");
                String html = fetchString(urlStr);
                List<Book> books = parseSeriesBooksFromHtml(html, defaultAuthor != null ? defaultAuthor : "Серия");
                mainHandler.post(() -> callback.onSuccess(books));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void loadSeriesDetails(Series series, Callback<Series> callback) {
        loadSeriesBooks(series.getId(), 0, series.getAuthor(), new Callback<List<Book>>() {
            @Override
            public void onSuccess(List<Book> result) {
                series.setBooks(result);
                callback.onSuccess(series);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    private static String fetchString(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) FlibustaReader/1.0");
        conn.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8");

        int code = conn.getResponseCode();
        if (code >= 300 && code < 400) {
            String redirectUrl = conn.getHeaderField("Location");
            if (redirectUrl != null) {
                return fetchString(redirectUrl.startsWith("http") ? redirectUrl : BASE_URL + redirectUrl);
            }
        }

        InputStream is = conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    public static List<GenreItem> parseGenreItemsFromOpds(String xml) {
        return parseGenreItemsFromOpds(xml, false);
    }

    public static List<GenreItem> parseGenreItemsFromOpds(String xml, boolean isRoot) {
        List<GenreItem> list = new ArrayList<>();
        if (xml == null || xml.isEmpty()) return list;

        Matcher entryMatcher = Pattern.compile("<entry>.*?</entry>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(xml);
        while (entryMatcher.find()) {
            String entry = entryMatcher.group();
            Matcher tMatch = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE).matcher(entry);
            Matcher cMatch = Pattern.compile("<content[^>]*>([^<]*)</content>", Pattern.CASE_INSENSITIVE).matcher(entry);
            Matcher lMatch = Pattern.compile("<link[^>]+href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(entry);

            if (tMatch.find() && lMatch.find()) {
                String title = tMatch.group(1).trim();
                String href = lMatch.group(1).trim();
                String content = cMatch.find() ? cMatch.group(1).trim() : "";
                String fullUrl = href.startsWith("http") ? href : BASE_URL + href;
                // In root genres feed, all entries are categories (not leaves).
                // Subgenres lead to books, so they are leaves.
                boolean isLeaf = !isRoot;
                list.add(new GenreItem(title, fullUrl, content, isLeaf));
            }
        }
        return list;
    }

    public static List<Book> parseAuthorPageBooks(String html, String authorName) {
        List<Book> list = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        Pattern pattern = Pattern.compile("<input[^>]*name=[\"']?bchk(\\d+)[\"']?[^>]*>.*?<a\\s+href=[\"']/b/\\1[\"'][^>]*>(.*?)</a>(?:.*?<span\\s+style=size>([^<]+)</span>)?", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String bId = matcher.group(1);
            String title = matcher.group(2).replaceAll("<[^>]+>", "").trim();
            String size = matcher.group(3) != null ? matcher.group(3).trim() : "FB2";

            if (bId != null && !seenIds.contains(bId) && !title.isEmpty()) {
                seenIds.add(bId);
                String dlUrl = BASE_URL + "/b/" + bId + "/fb2";
                Book b = new Book(bId, title, authorName, "Книга автора", size, "fb2", dlUrl);
                try {
                    b.setCoverUrl(BASE_URL + "/i/" + (Integer.parseInt(bId) % 100) + "/" + bId + "/cover.jpg");
                } catch (Exception ignored) {}
                list.add(b);
            }
            if (list.size() >= 100) break;
        }

        return list;
    }

    public static List<Book> parseBooksFromOpds(String xml) {
        List<Book> list = new ArrayList<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xml));

            int eventType = parser.getEventType();
            boolean insideEntry = false;

            String currentTag = "";
            String title = "";
            String author = "";
            String genre = "";
            String content = "";
            String bookId = "";
            String coverUrl = "";
            String size = "";

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        currentTag = name != null ? name.toLowerCase() : "";
                        if ("entry".equals(currentTag)) {
                            insideEntry = true;
                            title = "";
                            author = "";
                            genre = "";
                            content = "";
                            bookId = "";
                            coverUrl = "";
                            size = "";
                        } else if (insideEntry) {
                            if ("link".equals(currentTag)) {
                                String href = parser.getAttributeValue(null, "href");
                                String rel = parser.getAttributeValue(null, "rel");
                                if (href != null) {
                                    if (href.contains("/b/") && bookId.isEmpty()) {
                                        Matcher m = Pattern.compile("/b/(\\d+)").matcher(href);
                                        if (m.find()) {
                                            bookId = m.group(1);
                                        }
                                    }
                                    if (rel != null && (rel.contains("image") || rel.contains("thumbnail") || href.endsWith(".jpg") || href.endsWith(".png"))) {
                                        if (coverUrl.isEmpty()) {
                                            coverUrl = href.startsWith("http") ? href : BASE_URL + href;
                                        }
                                    }
                                }
                            } else if ("category".equals(currentTag)) {
                                String label = parser.getAttributeValue(null, "label");
                                String term = parser.getAttributeValue(null, "term");
                                if (label != null && !label.isEmpty()) {
                                    genre = label;
                                } else if (term != null && !term.isEmpty()) {
                                    genre = term;
                                }
                            }
                        }
                        break;

                    case XmlPullParser.TEXT:
                        if (insideEntry) {
                            String text = parser.getText();
                            if (text != null) {
                                text = text.trim();
                                if ("title".equals(currentTag) && title.isEmpty()) {
                                    title = text;
                                } else if ("name".equals(currentTag) && author.isEmpty()) {
                                    author = text;
                                } else if ("content".equals(currentTag) && content.isEmpty()) {
                                    content = text.replaceAll("<[^>]+>", " ").trim();
                                    Matcher mSize = Pattern.compile("Размер:\\s*([0-9]+\\s*(?:Kb|Mb|Кб|Мб))", Pattern.CASE_INSENSITIVE).matcher(text);
                                    if (mSize.find()) {
                                        size = mSize.group(1);
                                    }
                                }
                            }
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("entry".equals(name != null ? name.toLowerCase() : "")) {
                            insideEntry = false;
                            if (!title.isEmpty() && !bookId.isEmpty()) {
                                String dlUrl = BASE_URL + "/b/" + bookId + "/fb2";
                                Book b = new Book(bookId, title, author.isEmpty() ? "Не указан" : author,
                                        genre.isEmpty() ? "Художественная литература" : genre,
                                        size.isEmpty() ? "FB2" : size, "fb2", dlUrl);
                                b.setCoverUrl(coverUrl);
                                b.setDescription(content);
                                list.add(b);
                            }
                        }
                        currentTag = "";
                        break;
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private static List<Series> parseSeriesFromOpds(String xml) {
        List<Series> list = new ArrayList<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xml));

            int eventType = parser.getEventType();
            boolean insideEntry = false;
            String currentTag = "";
            String title = "";
            String idStr = "";
            String content = "";

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        currentTag = name != null ? name.toLowerCase() : "";
                        if ("entry".equals(currentTag)) {
                            insideEntry = true;
                            title = "";
                            idStr = "";
                            content = "";
                        }
                        break;

                    case XmlPullParser.TEXT:
                        if (insideEntry) {
                            String text = parser.getText();
                            if (text != null) {
                                text = text.trim();
                                if ("title".equals(currentTag) && title.isEmpty()) {
                                    title = text;
                                } else if ("id".equals(currentTag) && idStr.isEmpty()) {
                                    idStr = text;
                                } else if ("content".equals(currentTag) && content.isEmpty()) {
                                    content = text;
                                }
                            }
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("entry".equals(name != null ? name.toLowerCase() : "")) {
                            insideEntry = false;
                            String seqId = "";
                            if (!idStr.isEmpty()) {
                                String[] parts = idStr.split(":");
                                seqId = parts[parts.length - 1];
                            }
                            if (!title.isEmpty() && !seqId.isEmpty()) {
                                int count = 1;
                                Matcher m = Pattern.compile("(\\d+)").matcher(content);
                                if (m.find()) {
                                    try {
                                        count = Integer.parseInt(m.group(1));
                                    } catch (Exception ignored) {}
                                }
                                list.add(new Series(seqId, title, "Цикл на Флибусте", count));
                            }
                        }
                        currentTag = "";
                        break;
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private static List<Book> parseBooksFromHtml(String html) {
        List<Book> list = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        Pattern pattern = Pattern.compile("<a\\s+href=[\"']/(?:b/)?(\\d+)[\"'][^>]*>(.*?)</a>(?:\\s*-\\s*<a\\s+href=[\"']/a/\\d+[\"'][^>]*>(.*?)</a>)?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String bId = matcher.group(1);
            String title = matcher.group(2).replaceAll("<[^>]+>", "").trim();
            String author = matcher.group(3) != null ? matcher.group(3).replaceAll("<[^>]+>", "").trim() : "Не указан";

            if (bId != null && !seenIds.contains(bId) && !title.isEmpty()) {
                seenIds.add(bId);
                String dlUrl = BASE_URL + "/b/" + bId + "/fb2";
                list.add(new Book(bId, title, author, "Художественная литература", "FB2", "fb2", dlUrl));
            }
            if (list.size() >= 50) break;
        }

        return list;
    }

    private static List<Series> parseSeriesFromHtml(String html) {
        List<Series> list = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        // Match /sequence/123 or /s/123, with optional count (e.g. (16) or (16 книг)) and optional author (- <a href="/a/456">Author</a>)
        Pattern pattern = Pattern.compile("<a\\s+href=[\"']/(?:sequence|s)/(\\d+)[\"'][^>]*>(.*?)</a>(?:\\s*\\((\\d+)(?:\\s*книг[а-я]*)?\\))?(?:\\s*-\\s*<a\\s+href=[\"']/a/\\d+[\"'][^>]*>(.*?)</a>)?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String sId = matcher.group(1);
            String title = matcher.group(2).replaceAll("<[^>]+>", "").trim();
            String countStr = matcher.group(3);
            String author = matcher.group(4) != null ? matcher.group(4).replaceAll("<[^>]+>", "").trim() : null;
            int count = countStr != null ? Integer.parseInt(countStr) : 0;

            if (sId != null && !seenIds.contains(sId) && !title.isEmpty()) {
                seenIds.add(sId);
                String authorDisplay = (author != null && !author.isEmpty()) ? author : "Цикл / Серия";
                list.add(new Series(sId, title, authorDisplay, count));
            }
            if (list.size() >= 50) break;
        }

        return list;
    }

    private static List<Book> parseSeriesBooksFromHtml(String html, String defaultAuthor) {
        List<Book> books = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        // Match books on series page: <a href="/b/123">Title</a> with optional author link
        Pattern pattern = Pattern.compile("<a\\s+href=[\"']/(?:b/)?(\\d+)[\"'][^>]*>(.*?)</a>(?:\\s*-\\s*<a\\s+href=[\"']/a/\\d+[\"'][^>]*>(.*?)</a>)?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);

        int idx = 1;
        while (matcher.find()) {
            String bId = matcher.group(1);
            String title = matcher.group(2).replaceAll("<[^>]+>", "").trim();
            String author = matcher.group(3) != null ? matcher.group(3).replaceAll("<[^>]+>", "").trim() : defaultAuthor;

            // Ignore system navigation links
            if (title.equalsIgnoreCase("скачать") || title.equalsIgnoreCase("читать") ||
                    title.equalsIgnoreCase("fb2") || title.equalsIgnoreCase("epub") ||
                    title.equalsIgnoreCase("mobi") || title.equalsIgnoreCase("pdf") ||
                    title.equalsIgnoreCase("купить") || title.equalsIgnoreCase("редактировать")) {
                continue;
            }

            if (bId != null && !seenIds.contains(bId) && !title.isEmpty()) {
                seenIds.add(bId);
                String dlUrl = BASE_URL + "/b/" + bId + "/fb2";
                String finalAuthor = (author != null && !author.isEmpty()) ? author : defaultAuthor;
                Book b = new Book(bId, title, finalAuthor, "В серии", "FB2", "fb2", dlUrl);
                try {
                    b.setCoverUrl(BASE_URL + "/i/" + (Integer.parseInt(bId) % 100) + "/" + bId + "/cover.jpg");
                } catch (Exception ignored) {}
                books.add(b);
                idx++;
            }
        }
        return books;
    }
}
