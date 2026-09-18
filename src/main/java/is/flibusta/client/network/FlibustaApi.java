package is.flibusta.client.network;

import android.os.Handler;
import android.os.Looper;

import is.flibusta.client.data.Book;
import is.flibusta.client.data.Series;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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

    public static void searchBooks(String query, Callback<List<Book>> callback) {
        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String urlStr = currentMirror + "/booksearch?ask=" + encoded;
                String html = fetchHtml(urlStr);

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
                String html = fetchHtml(urlStr);

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
                String urlStr = currentMirror + "/sequence/" + series.getId();
                String html = fetchHtml(urlStr);

                List<Book> books = parseSeriesBooksFromHtml(html, series.getAuthor());
                series.setBooks(books);
                mainHandler.post(() -> callback.onSuccess(series));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private static String fetchHtml(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(12000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) FlibustaReader/1.0");
        conn.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8");

        int code = conn.getResponseCode();
        if (code >= 300 && code < 400) {
            String redirectUrl = conn.getHeaderField("Location");
            if (redirectUrl != null) {
                return fetchHtml(redirectUrl.startsWith("http") ? redirectUrl : currentMirror + redirectUrl);
            }
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    private static List<Book> parseBooksFromHtml(String html) {
        List<Book> list = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        // Regex for matching book links: <a href="/b/(\d+)">Title</a>
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

        // Regex for sequence links: <a href="/sequence/(\d+)">Title</a>
        Pattern pattern = Pattern.compile("<a\\s+href=\"/(?:sequence|s)/(\\d+)\"[^>]*>([^<]+)</a>(?:\\s*\\((\\d+)\\))?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String sId = matcher.group(1);
            String title = matcher.group(2).trim();
            String countStr = matcher.group(3);
            int count = countStr != null ? Integer.parseInt(countStr) : 0;

            if (sId != null && !seenIds.contains(sId) && !title.isEmpty()) {
                seenIds.add(sId);
                list.add(new Series(sId, title, "Цикл / Серия книг", count));
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
