package is.flibusta.client.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import is.flibusta.client.data.Book;
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
    public static final String DEFAULT_MIRROR = "http://flibusta.is";
    private static String currentMirror = DEFAULT_MIRROR;

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

    public static String getMirror() {
        return currentMirror;
    }

    public static void setMirror(String mirror) {
        if (mirror != null && !mirror.isEmpty()) {
            currentMirror = mirror.replaceAll("/+$", "");
        }
    }

    public static void fetchLiveCatalog(Callback<CatalogFeed> callback) {
        executor.execute(() -> {
            try {
                CatalogFeed feed = new CatalogFeed();

                // 1. Fetch live books from OPDS
                String booksXml = fetchString(currentMirror + "/opds/new/0/new");
                List<Book> liveBooks = parseBooksFromOpds(booksXml);

                if (!liveBooks.isEmpty()) {
                    feed.featuredBook = liveBooks.get(0);
                    if (liveBooks.size() > 1) {
                        feed.recommendedBooks.addAll(liveBooks.subList(1, liveBooks.size()));
                    }
                }

                // 2. Fetch live series from OPDS
                try {
                    String seriesXml = fetchString(currentMirror + "/opds/newsequences");
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

    public static void searchBooks(String query, Callback<List<Book>> callback) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String urlStr = currentMirror + "/booksearch?ask=" + encoded;
                String html = fetchString(urlStr);

                List<Book> books = parseBooksFromHtml(html);
                mainHandler.post(() -> callback.onSuccess(books));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void searchSeries(String query, Callback<List<Series>> callback) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String urlStr = currentMirror + "/booksearch?ask=" + encoded + "&chs=on";
                String html = fetchString(urlStr);

                List<Series> seriesList = parseSeriesFromHtml(html);
                mainHandler.post(() -> callback.onSuccess(seriesList));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static void loadSeriesDetails(Series series, Callback<Series> callback) {
        executor.execute(() -> {
            try {
                // Try OPDS sequencebooks first
                String opdsUrl = currentMirror + "/opds/sequencebooks/" + series.getId();
                try {
                    String xml = fetchString(opdsUrl);
                    List<Book> books = parseBooksFromOpds(xml);
                    if (!books.isEmpty()) {
                        series.setBooks(books);
                        mainHandler.post(() -> callback.onSuccess(series));
                        return;
                    }
                } catch (Exception ignored) {
                }

                // Fallback to HTML sequence page
                String urlStr = currentMirror + "/sequence/" + series.getId();
                String html = fetchString(urlStr);
                List<Book> books = parseSeriesBooksFromHtml(html, series.getAuthor());
                series.setBooks(books);
                mainHandler.post(() -> callback.onSuccess(series));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
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
                return fetchString(redirectUrl.startsWith("http") ? redirectUrl : currentMirror + redirectUrl);
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

    private static List<Book> parseBooksFromOpds(String xml) {
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
            String dlUrl = "";

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
                            dlUrl = "";
                        } else if (insideEntry) {
                            if ("link".equals(currentTag)) {
                                String href = parser.getAttributeValue(null, "href");
                                if (href != null && href.contains("/b/")) {
                                    Matcher m = Pattern.compile("/b/(\\d+)").matcher(href);
                                    if (m.find() && bookId.isEmpty()) {
                                        bookId = m.group(1);
                                        dlUrl = currentMirror + "/b/" + bookId + "/fb2";
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
                                }
                            }
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("entry".equals(name != null ? name.toLowerCase() : "")) {
                            insideEntry = false;
                            if (!title.isEmpty() && !bookId.isEmpty()) {
                                Book b = new Book(bookId, title, author.isEmpty() ? "Не указан" : author,
                                        genre.isEmpty() ? "Художественная литература" : genre, "FB2", "fb2", dlUrl);
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
                String dlUrl = currentMirror + "/b/" + bId + "/fb2";
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
            String dlUrl = currentMirror + "/b/" + bId + "/fb2";

            books.add(new Book(bId, idx + ". " + title, defaultAuthor, "В серии", "FB2", "fb2", dlUrl));
            idx++;
        }
        return books;
    }
}
