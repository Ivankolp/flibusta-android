package is.flibusta.client.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import is.flibusta.client.data.Book;
import is.flibusta.client.data.BookPage;
import is.flibusta.client.data.GenreItem;
import is.flibusta.client.data.Series;
import is.flibusta.client.data.SeriesPage;

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

    // ==========================================
    // CATALOG (MAIN TAB) - ROBUST MULTI-FALLBACK
    // ==========================================
    public static void fetchLiveCatalog(Callback<CatalogFeed> callback) {
        executor.execute(() -> {
            try {
                CatalogFeed feed = new CatalogFeed();

                // 1. Fetch live books from OPDS or HTML with cascading fallbacks
                List<Book> liveBooks = new ArrayList<>();
                try {
                    String xml = fetchString(BASE_URL + "/opds/new/0/new");
                    liveBooks = parseBooksFromOpds(xml);
                } catch (Exception ignored) {}

                if (liveBooks.isEmpty()) {
                    try {
                        String xml = fetchString(BASE_URL + "/opds/new");
                        liveBooks = parseBooksFromOpds(xml);
                    } catch (Exception ignored) {}
                }

                if (liveBooks.isEmpty()) {
                    try {
                        String xml = fetchString(BASE_URL + "/opds");
                        liveBooks = parseBooksFromOpds(xml);
                    } catch (Exception ignored) {}
                }

                if (liveBooks.isEmpty()) {
                    try {
                        String html = fetchString(BASE_URL + "/");
                        liveBooks = parseBooksFromHtml(html);
                    } catch (Exception ignored) {}
                }

                if (!liveBooks.isEmpty()) {
                    feed.featuredBook = liveBooks.get(0);
                    if (liveBooks.size() > 1) {
                        feed.recommendedBooks.addAll(liveBooks.subList(1, Math.min(liveBooks.size(), 20)));
                    }
                }

                // 2. Fetch live series from OPDS or search
                try {
                    String seriesXml = fetchString(BASE_URL + "/opds/newsequences");
                    feed.popularSeries = parseSeriesFromOpds(seriesXml);
                } catch (Exception ignored) {
                    try {
                        String seriesHtml = fetchString(BASE_URL + "/booksearch?ask=%D0%94%D0%BE%D0%B7%D0%BE%D1%80&chs=on");
                        feed.popularSeries = parseSeriesFromHtml(seriesHtml);
                    } catch (Exception ignored2) {
                        feed.popularSeries = new ArrayList<>();
                    }
                }

                if (feed.featuredBook == null && feed.recommendedBooks.isEmpty() && feed.popularSeries.isEmpty()) {
                    throw new Exception("Не удалось загрузить каталог Флибусты");
                }

                mainHandler.post(() -> callback.onSuccess(feed));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    // ==========================================
    // GENRES
    // ==========================================
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

    // ==========================================
    // UNIVERSAL ACCURATE PAGINATION FOR BOOKS
    // ==========================================

    public static void fetchBooksPage(String url, Callback<BookPage> callback) {
        executor.execute(() -> {
            try {
                String content = fetchString(url);
                BookPage page;
                if (content.trim().startsWith("<") && (content.contains("<feed") || content.contains("<?xml"))) {
                    page = parseBookPageFromOpds(content);
                } else {
                    page = parseBookPageFromHtml(content);
                }
                mainHandler.post(() -> callback.onSuccess(page));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void searchBooksPage(String query, String pageUrl, Callback<BookPage> callback) {
        if (pageUrl != null && !pageUrl.isEmpty()) {
            fetchBooksPage(pageUrl, callback);
            return;
        }

        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String opdsUrl = BASE_URL + "/opds/search?searchType=books&searchTerm=" + encoded;
                try {
                    String xml = fetchString(opdsUrl);
                    BookPage page = parseBookPageFromOpds(xml);
                    if (!page.getBooks().isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(page));
                        return;
                    }
                } catch (Exception ignored) {}

                // Fallback to HTML
                String htmlUrl = BASE_URL + "/booksearch?ask=" + encoded + "&chb=on";
                try {
                    String html = fetchString(htmlUrl);
                    BookPage htmlPage = parseBookPageFromHtml(html);
                    if (!htmlPage.getBooks().isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(htmlPage));
                        return;
                    }
                } catch (Exception ignored) {}

                // General fallback
                String generalUrl = BASE_URL + "/booksearch?ask=" + encoded;
                String generalHtml = fetchString(generalUrl);
                BookPage generalPage = parseBookPageFromHtml(generalHtml);
                mainHandler.post(() -> callback.onSuccess(generalPage));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void searchBooksByAuthorPage(String authorName, String pageUrl, Callback<BookPage> callback) {
        if (pageUrl != null && !pageUrl.isEmpty()) {
            fetchBooksPage(pageUrl, new Callback<BookPage>() {
                @Override
                public void onSuccess(BookPage page) {
                    for (Book b : page.getBooks()) {
                        if (b.getAuthor() == null || b.getAuthor().isEmpty() ||
                                b.getAuthor().equalsIgnoreCase("Не указан") ||
                                b.getAuthor().equalsIgnoreCase("Неизвестный автор")) {
                            b.setAuthor(authorName);
                        }
                    }
                    callback.onSuccess(page);
                }

                @Override
                public void onError(Exception e) {
                    callback.onError(e);
                }
            });
            return;
        }

        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(authorName, "UTF-8");

                // 1. Direct OPDS book search
                try {
                    String opdsUrl = BASE_URL + "/opds/search?searchType=books&searchTerm=" + encoded;
                    String xml = fetchString(opdsUrl);
                    BookPage page = parseBookPageFromOpds(xml);
                    if (!page.getBooks().isEmpty()) {
                        for (Book b : page.getBooks()) {
                            if (b.getAuthor() == null || b.getAuthor().isEmpty() ||
                                    b.getAuthor().equalsIgnoreCase("Не указан") ||
                                    b.getAuthor().equalsIgnoreCase("Неизвестный автор")) {
                                b.setAuthor(authorName);
                            }
                        }
                        mainHandler.post(() -> callback.onSuccess(page));
                        return;
                    }
                } catch (Exception ignored) {}

                // 2. Direct HTML booksearch with chb=on
                try {
                    String htmlUrl = BASE_URL + "/booksearch?ask=" + encoded + "&chb=on";
                    String html = fetchString(htmlUrl);
                    BookPage htmlPage = parseBookPageFromHtml(html);
                    if (!htmlPage.getBooks().isEmpty()) {
                        for (Book b : htmlPage.getBooks()) {
                            if (b.getAuthor() == null || b.getAuthor().isEmpty() ||
                                    b.getAuthor().equalsIgnoreCase("Не указан") ||
                                    b.getAuthor().equalsIgnoreCase("Неизвестный автор")) {
                                b.setAuthor(authorName);
                            }
                        }
                        mainHandler.post(() -> callback.onSuccess(htmlPage));
                        return;
                    }
                } catch (Exception ignored) {}

                mainHandler.post(() -> callback.onSuccess(new BookPage(new ArrayList<>(), null)));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void loadSeriesBooksPage(String seriesId, String pageUrl, String defaultAuthor, Callback<BookPage> callback) {
        if (pageUrl != null && !pageUrl.isEmpty()) {
            fetchBooksPage(pageUrl, callback);
            return;
        }

        executor.execute(() -> {
            try {
                // Try OPDS sequencebooks first
                String opdsUrl = BASE_URL + "/opds/sequencebooks/" + seriesId;
                try {
                    String xml = fetchString(opdsUrl);
                    BookPage page = parseBookPageFromOpds(xml);
                    if (!page.getBooks().isEmpty()) {
                        if (defaultAuthor != null && !defaultAuthor.isEmpty()) {
                            for (Book b : page.getBooks()) {
                                if (b.getAuthor() == null || b.getAuthor().isEmpty() ||
                                        b.getAuthor().equalsIgnoreCase("Не указан") ||
                                        b.getAuthor().equalsIgnoreCase("Неизвестный автор")) {
                                    b.setAuthor(defaultAuthor);
                                }
                            }
                        }
                        mainHandler.post(() -> callback.onSuccess(page));
                        return;
                    }
                } catch (Exception ignored) {}

                // Fallback to HTML sequence page
                String urlStr = BASE_URL + "/sequence/" + seriesId;
                String html = fetchString(urlStr);
                List<Book> books = parseSeriesBooksFromHtml(html, defaultAuthor != null ? defaultAuthor : "Серия");
                mainHandler.post(() -> callback.onSuccess(new BookPage(books, null)));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    // ==========================================
    // SERIES SEARCH PAGINATION
    // ==========================================
    public static void searchSeriesPage(String query, String pageUrl, Callback<SeriesPage> callback) {
        executor.execute(() -> {
            try {
                if (pageUrl != null && !pageUrl.isEmpty()) {
                    String content = fetchString(pageUrl);
                    SeriesPage page;
                    if (content.trim().startsWith("<") && (content.contains("<feed") || content.contains("<?xml"))) {
                        page = parseSeriesPageFromOpds(content);
                    } else {
                        page = parseSeriesPageFromHtml(content);
                    }
                    mainHandler.post(() -> callback.onSuccess(page));
                    return;
                }

                String encoded = URLEncoder.encode(query, "UTF-8");

                // 1. Try OPDS sequences search
                try {
                    String opdsUrl = BASE_URL + "/opds/search?searchType=sequences&searchTerm=" + encoded;
                    String xml = fetchString(opdsUrl);
                    SeriesPage page = parseSeriesPageFromOpds(xml);
                    if (!page.getSeriesList().isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(page));
                        return;
                    }
                } catch (Exception ignored) {}

                // 2. Try HTML booksearch with chs=on
                try {
                    String urlStr = BASE_URL + "/booksearch?ask=" + encoded + "&chs=on";
                    String html = fetchString(urlStr);
                    SeriesPage page = parseSeriesPageFromHtml(html);
                    if (!page.getSeriesList().isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(page));
                        return;
                    }
                } catch (Exception ignored) {}

                // 3. Fallback: general booksearch
                try {
                    String generalUrl = BASE_URL + "/booksearch?ask=" + encoded;
                    String generalHtml = fetchString(generalUrl);
                    SeriesPage page = parseSeriesPageFromHtml(generalHtml);
                    if (!page.getSeriesList().isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(page));
                        return;
                    }
                } catch (Exception ignored) {}

                mainHandler.post(() -> callback.onSuccess(new SeriesPage(new ArrayList<>(), null)));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    // ==========================================
    // BACKWARD-COMPATIBILITY OVERLOADS
    // ==========================================
    public static void fetchBooksFromUrl(String url, Callback<List<Book>> callback) {
        fetchBooksPage(url, new Callback<BookPage>() {
            @Override
            public void onSuccess(BookPage result) {
                callback.onSuccess(result.getBooks());
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public static void searchBooks(String query, Callback<List<Book>> callback) {
        searchBooksPage(query, null, new Callback<BookPage>() {
            @Override
            public void onSuccess(BookPage result) {
                callback.onSuccess(result.getBooks());
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public static void searchBooksByAuthor(String authorName, Callback<List<Book>> callback) {
        searchBooksByAuthorPage(authorName, null, new Callback<BookPage>() {
            @Override
            public void onSuccess(BookPage result) {
                callback.onSuccess(result.getBooks());
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public static void searchSeries(String query, Callback<List<Series>> callback) {
        searchSeriesPage(query, null, new Callback<SeriesPage>() {
            @Override
            public void onSuccess(SeriesPage result) {
                callback.onSuccess(result.getSeriesList());
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public static void loadSeriesBooks(String seriesId, String defaultAuthor, Callback<List<Book>> callback) {
        loadSeriesBooksPage(seriesId, null, defaultAuthor, new Callback<BookPage>() {
            @Override
            public void onSuccess(BookPage result) {
                callback.onSuccess(result.getBooks());
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public static void loadSeriesDetails(Series series, Callback<Series> callback) {
        loadSeriesBooksPage(series.getId(), null, series.getAuthor(), new Callback<BookPage>() {
            @Override
            public void onSuccess(BookPage result) {
                series.setBooks(result.getBooks());
                callback.onSuccess(series);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    // ==========================================
    // HTTP UTILITY
    // ==========================================
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

    // ==========================================
    // PARSING LOGIC
    // ==========================================
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
                boolean isLeaf = !isRoot;
                list.add(new GenreItem(title, fullUrl, content, isLeaf));
            }
        }
        return list;
    }

    public static BookPage parseBookPageFromOpds(String xml) {
        List<Book> list = new ArrayList<>();
        String nextPageUrl = null;
        if (xml == null || xml.isEmpty()) {
            return new BookPage(list, null);
        }

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
                        } else {
                            // Link on <feed> level: check for rel="next"
                            if ("link".equals(currentTag)) {
                                String rel = parser.getAttributeValue(null, "rel");
                                String href = parser.getAttributeValue(null, "href");
                                if (rel != null && rel.contains("next") && href != null && !href.isEmpty()) {
                                    nextPageUrl = href.startsWith("http") ? href : BASE_URL + href;
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

        return new BookPage(list, nextPageUrl);
    }

    public static List<Book> parseBooksFromOpds(String xml) {
        return parseBookPageFromOpds(xml).getBooks();
    }

    public static BookPage parseBookPageFromHtml(String html) {
        List<Book> list = new ArrayList<>();
        String nextPageUrl = null;
        if (html == null || html.isEmpty()) return new BookPage(list, null);

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

        // Check for next page link in HTML pager
        Matcher mNext = Pattern.compile("<li\\s+class=[\"']pager-next[\"'][^>]*><a\\s+href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html);
        if (mNext.find()) {
            String href = mNext.group(1).replace("&amp;", "&");
            nextPageUrl = href.startsWith("http") ? href : BASE_URL + href;
        } else {
            Matcher mNext2 = Pattern.compile("<a\\s+href=[\"']([^\"']*[?&]page=\\d+[^\"']*)[\"'][^>]*>(?:следующая|›|&gt;)", Pattern.CASE_INSENSITIVE).matcher(html);
            if (mNext2.find()) {
                String href = mNext2.group(1).replace("&amp;", "&");
                nextPageUrl = href.startsWith("http") ? href : BASE_URL + href;
            }
        }

        return new BookPage(list, nextPageUrl);
    }

    public static List<Book> parseBooksFromHtml(String html) {
        return parseBookPageFromHtml(html).getBooks();
    }

    public static SeriesPage parseSeriesPageFromOpds(String xml) {
        List<Series> list = new ArrayList<>();
        String nextPageUrl = null;
        if (xml == null || xml.isEmpty()) return new SeriesPage(list, null);

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xml));

            int eventType = parser.getEventType();
            boolean insideEntry = false;
            String currentTag = "";
            String title = "";
            String idStr = "";
            String content = "";
            String entryHref = "";

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
                            entryHref = "";
                        } else if (insideEntry) {
                            if ("link".equals(currentTag)) {
                                String href = parser.getAttributeValue(null, "href");
                                if (href != null && href.contains("/sequence")) {
                                    entryHref = href;
                                }
                            }
                        } else {
                            if ("link".equals(currentTag)) {
                                String rel = parser.getAttributeValue(null, "rel");
                                String href = parser.getAttributeValue(null, "href");
                                if (rel != null && rel.contains("next") && href != null && !href.isEmpty()) {
                                    nextPageUrl = href.startsWith("http") ? href : BASE_URL + href;
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
                            if (!entryHref.isEmpty()) {
                                Matcher m = Pattern.compile("/sequence(?:books)?/(\\d+)").matcher(entryHref);
                                if (m.find()) seqId = m.group(1);
                            }
                            if (seqId.isEmpty() && !idStr.isEmpty()) {
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

        return new SeriesPage(list, nextPageUrl);
    }

    public static List<Series> parseSeriesFromOpds(String xml) {
        return parseSeriesPageFromOpds(xml).getSeriesList();
    }

    public static SeriesPage parseSeriesPageFromHtml(String html) {
        List<Series> list = new ArrayList<>();
        String nextPageUrl = null;
        if (html == null || html.isEmpty()) return new SeriesPage(list, null);

        Set<String> seenIds = new HashSet<>();
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

        // Check for next page in pager
        Matcher mNext = Pattern.compile("<li\\s+class=[\"']pager-next[\"'][^>]*><a\\s+href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html);
        if (mNext.find()) {
            String href = mNext.group(1).replace("&amp;", "&");
            nextPageUrl = href.startsWith("http") ? href : BASE_URL + href;
        }

        return new SeriesPage(list, nextPageUrl);
    }

    public static List<Series> parseSeriesFromHtml(String html) {
        return parseSeriesPageFromHtml(html).getSeriesList();
    }

    public static List<Book> parseSeriesBooksFromHtml(String html, String defaultAuthor) {
        List<Book> books = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        Pattern pattern = Pattern.compile("<a\\s+href=[\"']/(?:b/)?(\\d+)[\"'][^>]*>(.*?)</a>(?:\\s*-\\s*<a\\s+href=[\"']/a/\\d+[\"'][^>]*>(.*?)</a>)?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);

        int idx = 1;
        while (matcher.find()) {
            String bId = matcher.group(1);
            String title = matcher.group(2).replaceAll("<[^>]+>", "").trim();
            String author = matcher.group(3) != null ? matcher.group(3).replaceAll("<[^>]+>", "").trim() : defaultAuthor;

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
