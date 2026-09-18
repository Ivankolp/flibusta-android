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
                String targetUrl = (url != null && !url.isEmpty()) ? url : BASE_URL + "/opds/genres";
                String xml = fetchString(targetUrl);
                List<GenreItem> list = parseGenreItemsFromOpds(xml);
                mainHandler.post(() -> callback.onSuccess(list));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void fetchBooksFromUrl(String url, Callback<List<Book>> callback) {
        executor.execute(() -> {
            try {
                String xml = fetchString(url);
                List<Book> list = parseBooksFromOpds(xml);
                mainHandler.post(() -> callback.onSuccess(list));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void searchBooksByAuthor(String authorName, Callback<List<Book>> callback) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(authorName, "UTF-8");

                // 1. Try author search via OPDS
                try {
                    String searchXml = fetchString(BASE_URL + "/opds/search?searchType=authors&searchTerm=" + encoded);
                    Matcher m = Pattern.compile("/opds/author/(\\d+)").matcher(searchXml);
                    if (m.find()) {
                        String authorId = m.group(1);
                        String booksXml = fetchString(BASE_URL + "/opds/author/" + authorId + "/alphabet");
                        List<Book> opdsBooks = parseBooksFromOpds(booksXml);
                        if (!opdsBooks.isEmpty()) {
                            mainHandler.post(() -> callback.onSuccess(opdsBooks));
                            return;
                        }
                    }
                } catch (Exception ignored) {}

                // 2. Fallback: search books by author name via HTML
                String html = fetchString(BASE_URL + "/booksearch?ask=" + encoded);
                List<Book> htmlBooks = parseBooksFromHtml(html);
                mainHandler.post(() -> callback.onSuccess(htmlBooks));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void searchBooks(String query, Callback<List<Book>> callback) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String opdsUrl = BASE_URL + "/opds/search?searchType=books&searchTerm=" + encoded;
                String xml = fetchString(opdsUrl);

                List<Book> books = parseBooksFromOpds(xml);

                // Fallback to HTML if OPDS returns empty
                if (books.isEmpty()) {
                    String html = fetchString(BASE_URL + "/booksearch?ask=" + encoded);
                    books = parseBooksFromHtml(html);
                }

                List<Book> finalBooks = books;
                mainHandler.post(() -> callback.onSuccess(finalBooks));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void searchSeries(String query, Callback<List<Series>> callback) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String urlStr = BASE_URL + "/booksearch?ask=" + encoded + "&chs=on";
                String html = fetchString(urlStr);

                List<Series> seriesList = parseSeriesFromHtml(html);
                mainHandler.post(() -> callback.onSuccess(seriesList));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void loadSeriesBooks(String seriesId, String defaultAuthor, Callback<List<Book>> callback) {
        executor.execute(() -> {
            try {
                // Try OPDS sequencebooks first
                String opdsUrl = BASE_URL + "/opds/sequencebooks/" + seriesId;
                try {
                    String xml = fetchString(opdsUrl);
                    List<Book> books = parseBooksFromOpds(xml);
                    if (!books.isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(books));
                        return;
                    }
                } catch (Exception ignored) {}

                // Fallback to HTML sequence page
                String urlStr = BASE_URL + "/sequence/" + seriesId;
                String html = fetchString(urlStr);
                List<Book> books = parseSeriesBooksFromHtml(html, defaultAuthor != null ? defaultAuthor : "Серия");
                mainHandler.post(() -> callback.onSuccess(books));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void loadSeriesDetails(Series series, Callback<Series> callback) {
        loadSeriesBooks(series.getId(), series.getAuthor(), new Callback<List<Book>>() {
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
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(15000);
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
        List<GenreItem> list = new ArrayList<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xml));

            int eventType = parser.getEventType();
            boolean insideEntry = false;
            String currentTag = "";
            String title = "";
            String content = "";
            String href = "";

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        currentTag = name != null ? name.toLowerCase() : "";
                        if ("entry".equals(currentTag)) {
                            insideEntry = true;
                            title = "";
                            content = "";
                            href = "";
                        } else if (insideEntry && "link".equals(currentTag)) {
                            String h = parser.getAttributeValue(null, "href");
                            String rel = parser.getAttributeValue(null, "rel");
                            if (h != null && (rel == null || !rel.contains("search"))) {
                                if (href.isEmpty() || h.contains("/opds/genres/")) {
                                    href = h;
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
                                } else if ("content".equals(currentTag) && content.isEmpty()) {
                                    content = text;
                                }
                            }
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("entry".equals(name != null ? name.toLowerCase() : "")) {
                            insideEntry = false;
                            if (!title.isEmpty() && !href.isEmpty()) {
                                String fullUrl = href.startsWith("http") ? href : BASE_URL + href;
                                boolean isLeaf = href.matches(".*/\\d+$");
                                list.add(new GenreItem(title, fullUrl, content, isLeaf));
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

        Pattern pattern = Pattern.compile("<a\\s+href=\"/(?:b/)?(\\d+)\"[^>]*>([^<]+)</a>(?:\\s*-\\s*<a\\s+href=\"/a/\\d+\"[^>]*>([^<]+)</a>)?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String bId = matcher.group(1);
            String title = matcher.group(2).trim();
            String author = matcher.group(3) != null ? matcher.group(3).trim() : "Не указан";

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

        Pattern pattern = Pattern.compile("<a\\s+href=\"/(?:sequence|s)/(\\d+)\"[^>]*>([^<]+)</a>(?:\\s*\\((\\d+)\\))?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String sId = matcher.group(1);
            String title = matcher.group(2).trim();
            String countStr = matcher.group(3);
            int count = countStr != null ? Integer.parseInt(countStr) : 0;

            if (sId != null && !seenIds.contains(sId) && !title.isEmpty()) {
                seenIds.add(sId);
                list.add(new Series(sId, title, "Цикл / Серия", count));
            }
            if (list.size() >= 40) break;
        }

        return list;
    }

    private static List<Book> parseSeriesBooksFromHtml(String html, String defaultAuthor) {
        List<Book> books = new ArrayList<>();
        Pattern pattern = Pattern.compile("<input[^>]*name=\"bchk(\\d+)\"[^>]*>.*?<a\\s+href=\"/b/\\1\"[^>]*>([^<]+)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);

        int idx = 1;
        while (matcher.find()) {
            String bId = matcher.group(1);
            String title = matcher.group(2).trim();
            String dlUrl = BASE_URL + "/b/" + bId + "/fb2";

            books.add(new Book(bId, idx + ". " + title, defaultAuthor, "В серии", "FB2", "fb2", dlUrl));
            idx++;
        }
        return books;
    }
}
