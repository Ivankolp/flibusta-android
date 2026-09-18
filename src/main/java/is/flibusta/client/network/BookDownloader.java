package is.flibusta.client.network;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import is.flibusta.client.data.Book;
import is.flibusta.client.data.DatabaseHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BookDownloader {

    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void downloadBook(Context context, Book book, String format) {
        downloadBook(context, book, format, null);
    }

    public static void downloadBook(Context context, Book book, String format, Runnable onComplete) {
        if (book == null) return;

        String cleanFormat = (format != null && !format.isEmpty()) ? format.toLowerCase() : "fb2";
        String downloadUrl = FlibustaApi.BASE_URL + "/b/" + book.getId() + "/" + cleanFormat;

        Toast.makeText(context, "Скачивание книги: " + book.getTitle(), Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            File targetFile = null;
            try {
                String safeTitle = book.getTitle().replaceAll("[\\\\/*?:\"<>|]", "_").trim();
                if (safeTitle.length() > 60) {
                    safeTitle = safeTitle.substring(0, 60);
                }
                String fileName = safeTitle + "." + cleanFormat;

                File booksDir = getBooksDirectory(context);
                targetFile = new File(booksDir, fileName);

                downloadToFileWithRedirects(downloadUrl, targetFile, 0);

                if (isZipFile(targetFile) && ("fb2".equalsIgnoreCase(cleanFormat) || "txt".equalsIgnoreCase(cleanFormat))) {
                    targetFile = unpackZipInPlace(targetFile);
                }

                File finalFile = targetFile;
                mainHandler.post(() -> {
                    book.setLocalPath(finalFile.getAbsolutePath());
                    book.setFormat(cleanFormat);
                    book.setStatus("Скачано");

                    DatabaseHelper db = new DatabaseHelper(context);
                    db.addBook(book);

                    String sizeStr = "";
                    long bytes = finalFile.length();
                    if (bytes > 1024 * 1024) {
                        sizeStr = String.format(Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0));
                    } else if (bytes > 0) {
                        sizeStr = (bytes / 1024) + " КБ";
                    }

                    Toast.makeText(context, "Книга скачана " + (sizeStr.isEmpty() ? "" : "(" + sizeStr + "): ") + book.getTitle(), Toast.LENGTH_SHORT).show();

                    if (onComplete != null) {
                        onComplete.run();
                    }
                });

            } catch (Exception e) {
                if (targetFile != null && targetFile.exists() && targetFile.length() == 0) {
                    targetFile.delete();
                }
                mainHandler.post(() -> {
                    Toast.makeText(context, "Ошибка скачивания: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    public static void downloadAndOpenBook(Context context, Book book, String format) {
        downloadBook(context, book, format, () -> openBook(context, book));
    }

    public static void openBook(Context context, Book book) {
        try {
            if (book == null) return;

            if (book.getLocalPath() == null || book.getLocalPath().isEmpty()) {
                Toast.makeText(context, "Скачиваем книгу...", Toast.LENGTH_SHORT).show();
                downloadAndOpenBook(context, book, book.getFormat() != null ? book.getFormat() : "fb2");
                return;
            }

            File file = new File(book.getLocalPath());
            if (!file.exists() || file.length() == 0) {
                Toast.makeText(context, "Файл не найден или пуст, скачиваем заново...", Toast.LENGTH_SHORT).show();
                downloadAndOpenBook(context, book, book.getFormat() != null ? book.getFormat() : "fb2");
                return;
            }

            // Auto-unpack if it's a zip archive disguised as .fb2 or .txt
            if (isZipFile(file) && (file.getName().toLowerCase().endsWith(".fb2") || file.getName().toLowerCase().endsWith(".txt"))) {
                File unzipped = unpackZipInPlace(file);
                if (unzipped != null && unzipped.exists() && unzipped.length() > 0) {
                    file = unzipped;
                    book.setLocalPath(file.getAbsolutePath());
                    new DatabaseHelper(context).addBook(book);
                }
            }

            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);

            String mimeType = "application/x-fb2";
            String lowerName = file.getName().toLowerCase();
            if (lowerName.endsWith(".epub")) {
                mimeType = "application/epub+zip";
            } else if (lowerName.endsWith(".mobi")) {
                mimeType = "application/x-mobipocket-ebook";
            } else if (lowerName.endsWith(".pdf")) {
                mimeType = "application/pdf";
            } else if (lowerName.endsWith(".txt")) {
                mimeType = "text/plain";
            } else if (lowerName.endsWith(".zip")) {
                mimeType = "application/x-zip-compressed";
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.setClipData(ClipData.newRawUri(book.getTitle(), uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> resInfoList = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            if (resInfoList.isEmpty()) {
                // Fallback to general type if no app explicitly registered for application/x-fb2
                intent.setDataAndType(uri, "*/*");
                List<ResolveInfo> fallbackList = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : fallbackList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            }

            Intent chooser = Intent.createChooser(intent, "Открыть: " + book.getTitle());
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(chooser);

        } catch (Exception e) {
            Toast.makeText(context, "Не удалось открыть читалку: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static File getBooksDirectory(Context context) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = new File(context.getFilesDir(), "Flibusta");
        } else {
            dir = new File(dir, "Flibusta");
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static void downloadToFileWithRedirects(String urlString, File destFile, int redirectCount) throws IOException {
        if (redirectCount > 6) throw new IOException("Слишком много перенаправлений сервера");
        if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
            urlString = FlibustaApi.BASE_URL + (urlString.startsWith("/") ? "" : "/") + urlString;
        }

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(25000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) FlibustaReader/1.0");

        int code = conn.getResponseCode();
        if (code >= 300 && code < 400) {
            String location = conn.getHeaderField("Location");
            conn.disconnect();
            if (location != null) {
                downloadToFileWithRedirects(location, destFile, redirectCount + 1);
                return;
            }
        }

        if (code != 200) {
            conn.disconnect();
            throw new IOException("Сервер вернул ошибку HTTP " + code);
        }

        File tempFile = new File(destFile.getParentFile(), destFile.getName() + ".part");
        try (InputStream is = conn.getInputStream();
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fos.flush();
        } finally {
            conn.disconnect();
        }

        if (destFile.exists()) destFile.delete();
        if (!tempFile.renameTo(destFile)) {
            throw new IOException("Не удалось сохранить файл книги");
        }
    }

    public static boolean isZipFile(File file) {
        if (file == null || !file.exists() || file.length() < 4) return false;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[4];
            int n = fis.read(header);
            return n == 4 && header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04;
        } catch (Exception e) {
            return false;
        }
    }

    public static File unpackZipInPlace(File file) {
        File tempFile = new File(file.getParentFile(), file.getName() + ".zip.tmp");
        if (tempFile.exists()) tempFile.delete();
        if (!file.renameTo(tempFile)) {
            return file;
        }

        boolean success = false;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tempFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, count);
                        }
                        success = true;
                    }
                    break;
                }
            }
        } catch (Exception e) {
            success = false;
        }

        if (success && file.exists() && file.length() > 0) {
            tempFile.delete();
            return file;
        } else {
            if (file.exists()) file.delete();
            tempFile.renameTo(file);
            return file;
        }
    }
}
