package is.flibusta.client.network;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import is.flibusta.client.data.Book;
import is.flibusta.client.data.DatabaseHelper;

import java.io.File;

public class BookDownloader {

    public static void downloadBook(Context context, Book book, String format) {
        try {
            String cleanFormat = (format != null && !format.isEmpty()) ? format.toLowerCase() : "fb2";
            String downloadUrl = FlibustaApi.BASE_URL + "/b/" + book.getId() + "/" + cleanFormat;

            String safeTitle = book.getTitle().replaceAll("[\\\\/*?:\"<>|]", "_").trim();
            if (safeTitle.length() > 60) {
                safeTitle = safeTitle.substring(0, 60);
            }
            String fileName = safeTitle + "." + cleanFormat;

            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));

            request.setTitle(book.getTitle());
            request.setDescription("Скачивание с Флибусты (" + cleanFormat.toUpperCase() + ")");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Flibusta/" + fileName);

            if (downloadManager != null) {
                downloadManager.enqueue(request);

                File localFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Flibusta/" + fileName);
                book.setLocalPath(localFile.getAbsolutePath());
                book.setFormat(cleanFormat);
                book.setStatus("Скачано");

                DatabaseHelper db = new DatabaseHelper(context);
                db.addBook(book);

                Toast.makeText(context, "Скачивание начато: " + book.getTitle(), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Ошибка скачивания: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void openBook(Context context, Book book) {
        try {
            if (book.getLocalPath() == null || book.getLocalPath().isEmpty()) {
                Toast.makeText(context, "Файл ещё не скачан", Toast.LENGTH_SHORT).show();
                return;
            }

            File file = new File(book.getLocalPath());
            if (!file.exists()) {
                Toast.makeText(context, "Файл не найден по пути: " + book.getLocalPath(), Toast.LENGTH_LONG).show();
                return;
            }

            Uri uri;
            try {
                uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
            } catch (Exception e) {
                uri = Uri.fromFile(file);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            String mimeType = "application/x-fb2";
            if (book.getLocalPath().endsWith(".epub")) {
                mimeType = "application/epub+zip";
            } else if (book.getLocalPath().endsWith(".mobi")) {
                mimeType = "application/x-mobipocket-ebook";
            }

            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Intent chooser = Intent.createChooser(intent, "Открыть книгу в читалке");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(context, "Не найдено приложение для чтения (установите ReadEra / Moon+ / FBReader)", Toast.LENGTH_LONG).show();
        }
    }
}
