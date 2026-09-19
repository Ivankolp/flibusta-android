package is.flibusta.client.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import is.flibusta.client.BooksListActivity;
import is.flibusta.client.R;
import is.flibusta.client.data.Book;
import is.flibusta.client.data.DatabaseHelper;
import is.flibusta.client.network.BookDownloader;
import is.flibusta.client.network.FlibustaApi;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SeriesDownloaderService extends Service {

    public static final String CHANNEL_ID = "flibusta_series_downloads";
    public static final int NOTIFICATION_ID = 2001;

    public static final String ACTION_DOWNLOAD_SERIES = "is.flibusta.client.action.DOWNLOAD_SERIES";
    public static final String ACTION_SERIES_PROGRESS = "is.flibusta.client.action.SERIES_PROGRESS";
    public static final String ACTION_SERIES_COMPLETE = "is.flibusta.client.action.SERIES_COMPLETE";

    public static final String EXTRA_SERIES_ID = "series_id";
    public static final String EXTRA_SERIES_TITLE = "series_title";
    public static final String EXTRA_AUTHOR = "author";
    public static final String EXTRA_BOOKS = "books";

    // Static progress tracker accessible by any Activity/Fragment
    public static final Map<String, Integer> activeDownloadsCurrent = new ConcurrentHashMap<>();
    public static final Map<String, Integer> activeDownloadsTotal = new ConcurrentHashMap<>();
    public static final Map<String, String> activeDownloadsTitle = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private NotificationManager notificationManager;

    public static boolean isDownloading(String seriesId) {
        return seriesId != null && activeDownloadsTotal.containsKey(seriesId);
    }

    public static int getProgress(String seriesId) {
        if (seriesId == null) return 0;
        Integer curr = activeDownloadsCurrent.get(seriesId);
        return curr != null ? curr : 0;
    }

    public static int getTotal(String seriesId) {
        if (seriesId == null) return 0;
        Integer tot = activeDownloadsTotal.get(seriesId);
        return tot != null ? tot : 0;
    }

    public static void startDownload(Context context, String seriesId, String seriesTitle, String author, ArrayList<Book> books) {
        Intent intent = new Intent(context, SeriesDownloaderService.class);
        intent.setAction(ACTION_DOWNLOAD_SERIES);
        intent.putExtra(EXTRA_SERIES_ID, seriesId);
        intent.putExtra(EXTRA_SERIES_TITLE, seriesTitle);
        intent.putExtra(EXTRA_AUTHOR, author);
        if (books != null && !books.isEmpty()) {
            intent.putExtra(EXTRA_BOOKS, books);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Загрузка серий книг",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Фоновая загрузка целых циклов и серий с Флибусты");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DOWNLOAD_SERIES.equals(intent.getAction())) {
            String seriesId = intent.getStringExtra(EXTRA_SERIES_ID);
            String seriesTitle = intent.getStringExtra(EXTRA_SERIES_TITLE);
            String author = intent.getStringExtra(EXTRA_AUTHOR);
            @SuppressWarnings("unchecked")
            ArrayList<Book> books = (ArrayList<Book>) intent.getSerializableExtra(EXTRA_BOOKS);

            if (seriesId != null && !seriesId.isEmpty()) {
                startForeground(NOTIFICATION_ID, buildProgressNotification(seriesTitle, "Подготовка к загрузке...", 0, 0));
                executor.execute(() -> processSeriesDownload(seriesId, seriesTitle, author, books));
            }
        }
        return START_NOT_STICKY;
    }

    private void processSeriesDownload(String seriesId, String seriesTitle, String author, List<Book> initialBooks) {
        try {
            List<Book> booksToDownload = initialBooks;

            if (booksToDownload == null || booksToDownload.isEmpty()) {
                // Fetch full list of books from Flibusta synchronously inside worker thread
                booksToDownload = loadAllBooksSync(seriesId, author);
            }

            if (booksToDownload == null || booksToDownload.isEmpty()) {
                notifyCompletion(seriesTitle, "Не удалось найти книги серии для скачивания", 0);
                activeDownloadsTotal.remove(seriesId);
                activeDownloadsCurrent.remove(seriesId);
                activeDownloadsTitle.remove(seriesId);
                stopForeground(false);
                stopSelf();
                return;
            }

            int total = booksToDownload.size();
            activeDownloadsTotal.put(seriesId, total);
            activeDownloadsTitle.put(seriesId, seriesTitle != null ? seriesTitle : "Серия");

            File seriesDir = getSeriesDirectory(seriesTitle);
            DatabaseHelper db = new DatabaseHelper(getApplicationContext());

            for (int i = 0; i < total; i++) {
                int current = i + 1;
                activeDownloadsCurrent.put(seriesId, current);
                Book book = booksToDownload.get(i);

                String progressText = String.format(Locale.US, "Книга %d из %d (%d%%): %s", current, total, (current * 100) / total, book.getTitle());
                updateNotification(seriesTitle, progressText, total, current);
                broadcastProgress(seriesId, current, total, book.getTitle());

                // Download individual book
                downloadSingleBook(book, seriesId, seriesTitle, seriesDir, current, db);
            }

            notifyCompletion(seriesTitle, String.format(Locale.US, "Серия успешно скачана! Все %d книг сохранены в папку Flibusta.", total), total);
            broadcastCompletion(seriesId, total);

        } catch (Exception e) {
            notifyCompletion(seriesTitle, "Ошибка при скачивании: " + e.getMessage(), 0);
        } finally {
            activeDownloadsTotal.remove(seriesId);
            activeDownloadsCurrent.remove(seriesId);
            activeDownloadsTitle.remove(seriesId);
            stopForeground(false);
            stopSelf();
        }
    }

    private List<Book> loadAllBooksSync(String seriesId, String defaultAuthor) {
        final List<Book> result = new ArrayList<>();
        final Object lock = new Object();
        final boolean[] done = new boolean[]{false};

        FlibustaApi.fetchAllSeriesBooks(seriesId, defaultAuthor, new FlibustaApi.Callback<List<Book>>() {
            @Override
            public void onSuccess(List<Book> books) {
                synchronized (lock) {
                    if (books != null) result.addAll(books);
                    done[0] = true;
                    lock.notifyAll();
                }
            }

            @Override
            public void onError(Exception e) {
                synchronized (lock) {
                    done[0] = true;
                    lock.notifyAll();
                }
            }
        });

        synchronized (lock) {
            while (!done[0]) {
                try {
                    lock.wait(20000);
                    break;
                } catch (InterruptedException ignored) {}
            }
        }
        return result;
    }

    private void downloadSingleBook(Book book, String seriesId, String seriesTitle, File seriesDir, int index, DatabaseHelper db) {
        File targetFile = null;
        try {
            String safeTitle = book.getTitle().replaceAll("[\\\\/*?:\"<>|]", "_").trim();
            if (safeTitle.length() > 50) safeTitle = safeTitle.substring(0, 50);

            String fileName = String.format(Locale.US, "%02d_%s.fb2", index, safeTitle);
            targetFile = new File(seriesDir, fileName);

            if (!targetFile.exists() || targetFile.length() == 0) {
                String downloadUrl = FlibustaApi.BASE_URL + "/b/" + book.getId() + "/fb2";
                BookDownloader.downloadToFileWithRedirects(downloadUrl, targetFile, 0);

                if (BookDownloader.isZipFile(targetFile)) {
                    targetFile = BookDownloader.unpackZipInPlace(targetFile);
                }
            }

            book.setLocalPath(targetFile.getAbsolutePath());
            book.setFormat("fb2");
            book.setStatus("Скачано");
            if (seriesId != null && !seriesId.isEmpty()) book.setSeriesId(seriesId);
            if (seriesTitle != null && !seriesTitle.isEmpty()) book.setSeriesName(seriesTitle);
            if (book.getSeriesNumber() <= 0) book.setSeriesNumber(index);
            db.addBook(book);

        } catch (Exception e) {
            if (targetFile != null && targetFile.exists() && targetFile.length() == 0) {
                targetFile.delete();
            }
        }
    }

    private File getSeriesDirectory(String seriesTitle) {
        String safeDir = (seriesTitle != null && !seriesTitle.isEmpty())
                ? seriesTitle.replaceAll("[\\\\/*?:\"<>|]", "_").trim()
                : "Серия";
        if (safeDir.length() > 50) safeDir = safeDir.substring(0, 50);

        File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File flibustaDir = new File(baseDir, "Flibusta");
        File target = new File(flibustaDir, safeDir);
        if (!target.exists()) {
            target.mkdirs();
        }
        return target;
    }

    private Notification buildProgressNotification(String seriesTitle, String text, int total, int current) {
        Intent openIntent = new Intent(this, BooksListActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Скачивание серии: " + (seriesTitle != null ? seriesTitle : ""))
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (total > 0) {
            builder.setProgress(total, current, false);
        } else {
            builder.setProgress(0, 0, true);
        }

        return builder.build();
    }

    private void updateNotification(String seriesTitle, String text, int total, int current) {
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildProgressNotification(seriesTitle, text, total, current));
        }
    }

    private void notifyCompletion(String seriesTitle, String message, int count) {
        if (notificationManager == null) return;

        Intent openIntent = new Intent(this, BooksListActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(count > 0 ? "Серия скачана!" : "Загрузка завершена")
                .setContentText(message)
                .setContentIntent(pi)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        notificationManager.notify(NOTIFICATION_ID + 1, builder.build());
    }

    private void broadcastProgress(String seriesId, int current, int total, String bookTitle) {
        Intent intent = new Intent(ACTION_SERIES_PROGRESS);
        intent.putExtra(EXTRA_SERIES_ID, seriesId);
        intent.putExtra("current", current);
        intent.putExtra("total", total);
        intent.putExtra("book_title", bookTitle);
        sendBroadcast(intent);
    }

    private void broadcastCompletion(String seriesId, int total) {
        Intent intent = new Intent(ACTION_SERIES_COMPLETE);
        intent.putExtra(EXTRA_SERIES_ID, seriesId);
        intent.putExtra("total", total);
        sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
