package is.flibusta.client.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "flibusta_library.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_LIBRARY = "library_books";
    private static final String COL_ID = "id";
    private static final String COL_TITLE = "title";
    private static final String COL_AUTHOR = "author";
    private static final String COL_GENRE = "genre";
    private static final String COL_SIZE = "size";
    private static final String COL_FORMAT = "format";
    private static final String COL_DOWNLOAD_URL = "download_url";
    private static final String COL_STATUS = "status";
    private static final String COL_LOCAL_PATH = "local_path";
    private static final String COL_DATE_ADDED = "date_added";
    private static final String COL_SERIES_NAME = "series_name";
    private static final String COL_SERIES_ID = "series_id";
    private static final String COL_SERIES_NUMBER = "series_number";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_COVER_URL = "cover_url";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_LIBRARY + " ("
                + COL_ID + " TEXT PRIMARY KEY, "
                + COL_TITLE + " TEXT, "
                + COL_AUTHOR + " TEXT, "
                + COL_GENRE + " TEXT, "
                + COL_SIZE + " TEXT, "
                + COL_FORMAT + " TEXT, "
                + COL_DOWNLOAD_URL + " TEXT, "
                + COL_STATUS + " TEXT, "
                + COL_LOCAL_PATH + " TEXT, "
                + COL_DATE_ADDED + " TEXT, "
                + COL_SERIES_NAME + " TEXT, "
                + COL_SERIES_ID + " TEXT, "
                + COL_SERIES_NUMBER + " INTEGER DEFAULT 0, "
                + COL_DESCRIPTION + " TEXT, "
                + COL_COVER_URL + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_LIBRARY + " ADD COLUMN " + COL_SERIES_NAME + " TEXT");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + TABLE_LIBRARY + " ADD COLUMN " + COL_SERIES_ID + " TEXT");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + TABLE_LIBRARY + " ADD COLUMN " + COL_SERIES_NUMBER + " INTEGER DEFAULT 0");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + TABLE_LIBRARY + " ADD COLUMN " + COL_DESCRIPTION + " TEXT");
            } catch (Exception ignored) {}
            try {
                db.execSQL("ALTER TABLE " + TABLE_LIBRARY + " ADD COLUMN " + COL_COVER_URL + " TEXT");
            } catch (Exception ignored) {}
        }
    }

    public synchronized boolean addBook(Book book) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ID, book.getId());
        cv.put(COL_TITLE, book.getTitle());
        cv.put(COL_AUTHOR, book.getAuthor());
        cv.put(COL_GENRE, book.getGenre());
        cv.put(COL_SIZE, book.getSize());
        cv.put(COL_FORMAT, book.getFormat());
        cv.put(COL_DOWNLOAD_URL, book.getDownloadUrl());
        cv.put(COL_STATUS, book.getStatus() != null ? book.getStatus() : "В планах");
        cv.put(COL_LOCAL_PATH, book.getLocalPath());

        String dateStr = book.getDateAdded();
        if (dateStr == null || dateStr.trim().isEmpty()) {
            dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        }
        cv.put(COL_DATE_ADDED, dateStr);
        book.setDateAdded(dateStr);

        cv.put(COL_SERIES_NAME, book.getSeriesName());
        cv.put(COL_SERIES_ID, book.getSeriesId());
        cv.put(COL_SERIES_NUMBER, book.getSeriesNumber());
        cv.put(COL_DESCRIPTION, book.getDescription());
        cv.put(COL_COVER_URL, book.getCoverUrl());

        long result = db.insertWithOnConflict(TABLE_LIBRARY, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return result != -1;
    }

    public synchronized void updateBookStatus(String bookId, String newStatus) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_STATUS, newStatus);
        db.update(TABLE_LIBRARY, cv, COL_ID + " = ?", new String[]{bookId});
    }

    public synchronized void updateBookLocalPath(String bookId, String path) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_LOCAL_PATH, path);
        cv.put(COL_STATUS, "Скачано");
        db.update(TABLE_LIBRARY, cv, COL_ID + " = ?", new String[]{bookId});
    }

    public synchronized void removeBook(String bookId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_LIBRARY, COL_ID + " = ?", new String[]{bookId});
    }

    public synchronized boolean isBookInLibrary(String bookId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT 1 FROM " + TABLE_LIBRARY + " WHERE " + COL_ID + " = ?", new String[]{bookId});
        boolean exists = (cursor.getCount() > 0);
        cursor.close();
        return exists;
    }

    private Book readBookFromCursor(Cursor cursor) {
        Book b = new Book();
        b.setId(cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)));
        b.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)));
        b.setAuthor(cursor.getString(cursor.getColumnIndexOrThrow(COL_AUTHOR)));
        b.setGenre(cursor.getString(cursor.getColumnIndexOrThrow(COL_GENRE)));
        b.setSize(cursor.getString(cursor.getColumnIndexOrThrow(COL_SIZE)));
        b.setFormat(cursor.getString(cursor.getColumnIndexOrThrow(COL_FORMAT)));
        b.setDownloadUrl(cursor.getString(cursor.getColumnIndexOrThrow(COL_DOWNLOAD_URL)));
        b.setStatus(cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS)));
        b.setLocalPath(cursor.getString(cursor.getColumnIndexOrThrow(COL_LOCAL_PATH)));
        b.setDateAdded(cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE_ADDED)));

        int sNameIdx = cursor.getColumnIndex(COL_SERIES_NAME);
        if (sNameIdx != -1) b.setSeriesName(cursor.getString(sNameIdx));

        int sIdIdx = cursor.getColumnIndex(COL_SERIES_ID);
        if (sIdIdx != -1) b.setSeriesId(cursor.getString(sIdIdx));

        int sNumIdx = cursor.getColumnIndex(COL_SERIES_NUMBER);
        if (sNumIdx != -1) b.setSeriesNumber(cursor.getInt(sNumIdx));

        int descIdx = cursor.getColumnIndex(COL_DESCRIPTION);
        if (descIdx != -1) b.setDescription(cursor.getString(descIdx));

        int coverIdx = cursor.getColumnIndex(COL_COVER_URL);
        if (coverIdx != -1) b.setCoverUrl(cursor.getString(coverIdx));

        return b;
    }

    public synchronized List<Book> getBooks(String filterStatus) {
        List<Book> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor;

        if (filterStatus == null || filterStatus.equals("Все") || filterStatus.isEmpty()) {
            cursor = db.rawQuery("SELECT * FROM " + TABLE_LIBRARY + " ORDER BY rowid DESC", null);
        } else {
            cursor = db.rawQuery("SELECT * FROM " + TABLE_LIBRARY + " WHERE " + COL_STATUS + " = ? ORDER BY rowid DESC", new String[]{filterStatus});
        }

        if (cursor.moveToFirst()) {
            do {
                list.add(readBookFromCursor(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public synchronized List<Book> getDownloadedBooks() {
        List<Book> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_LIBRARY + " WHERE " + COL_LOCAL_PATH + " IS NOT NULL AND TRIM(" + COL_LOCAL_PATH + ") != '' ORDER BY rowid DESC", null);
        if (cursor.moveToFirst()) {
            do {
                list.add(readBookFromCursor(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public synchronized List<Series> getDownloadedSeries() {
        List<Series> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COL_SERIES_NAME + ", " + COL_SERIES_ID + ", " + COL_AUTHOR + ", COUNT(*) as book_count FROM " + TABLE_LIBRARY
                        + " WHERE " + COL_SERIES_NAME + " IS NOT NULL AND TRIM(" + COL_SERIES_NAME + ") != '' GROUP BY " + COL_SERIES_NAME + " ORDER BY " + COL_SERIES_NAME + " ASC",
                null);
        if (cursor.moveToFirst()) {
            do {
                String sName = cursor.getString(0);
                String sId = cursor.getString(1);
                String sAuth = cursor.getString(2);
                int count = cursor.getInt(3);
                Series s = new Series(sId != null ? sId : "", sName, sAuth != null ? sAuth : "Разные авторы", count);
                list.add(s);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public synchronized List<Book> getBooksBySeries(String seriesName) {
        List<Book> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_LIBRARY + " WHERE " + COL_SERIES_NAME + " = ? ORDER BY " + COL_SERIES_NUMBER + " ASC, " + COL_TITLE + " ASC",
                new String[]{seriesName});
        if (cursor.moveToFirst()) {
            do {
                list.add(readBookFromCursor(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public synchronized List<Author> getDownloadedAuthors() {
        List<Author> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COL_AUTHOR + ", COUNT(*) as book_count FROM " + TABLE_LIBRARY
                        + " WHERE " + COL_AUTHOR + " IS NOT NULL AND TRIM(" + COL_AUTHOR + ") != '' AND " + COL_AUTHOR + " != 'Не указан' AND " + COL_AUTHOR + " != 'Неизвестный автор' GROUP BY " + COL_AUTHOR + " ORDER BY " + COL_AUTHOR + " ASC",
                null);
        if (cursor.moveToFirst()) {
            do {
                String aName = cursor.getString(0);
                int count = cursor.getInt(1);
                list.add(new Author("", aName, count));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public synchronized List<Book> getBooksByAuthor(String authorName) {
        List<Book> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_LIBRARY + " WHERE " + COL_AUTHOR + " = ? ORDER BY " + COL_TITLE + " ASC",
                new String[]{authorName});
        if (cursor.moveToFirst()) {
            do {
                list.add(readBookFromCursor(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public synchronized long getTotalDownloadedBytes() {
        long total = 0;
        List<Book> books = getDownloadedBooks();
        for (Book b : books) {
            String path = b.getLocalPath();
            if (path != null && !path.trim().isEmpty()) {
                File f = new File(path);
                if (f.exists() && f.isFile()) {
                    total += f.length();
                }
            }
        }
        return total;
    }

    public synchronized boolean deleteBookFile(String bookId) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_LOCAL_PATH + " FROM " + TABLE_LIBRARY + " WHERE " + COL_ID + " = ?", new String[]{bookId});
        String path = null;
        if (cursor.moveToFirst()) {
            path = cursor.getString(0);
        }
        cursor.close();

        if (path != null && !path.trim().isEmpty()) {
            try {
                File f = new File(path);
                if (f.exists()) {
                    f.delete();
                }
            } catch (Exception ignored) {}
        }

        ContentValues cv = new ContentValues();
        cv.putNull(COL_LOCAL_PATH);
        cv.put(COL_STATUS, "В планах");
        int rows = db.update(TABLE_LIBRARY, cv, COL_ID + " = ?", new String[]{bookId});
        return rows > 0;
    }

    public synchronized String exportLibraryToJson() {
        try {
            List<Book> allBooks = getBooks("Все");
            JSONArray array = new JSONArray();
            for (Book b : allBooks) {
                JSONObject obj = new JSONObject();
                obj.put("id", b.getId());
                obj.put("title", b.getTitle());
                obj.put("author", b.getAuthor());
                obj.put("genre", b.getGenre());
                obj.put("size", b.getSize());
                obj.put("format", b.getFormat());
                obj.put("download_url", b.getDownloadUrl());
                obj.put("status", b.getStatus());
                obj.put("local_path", b.getLocalPath());
                obj.put("date_added", b.getDateAdded());
                obj.put("series_name", b.getSeriesName());
                obj.put("series_id", b.getSeriesId());
                obj.put("series_number", b.getSeriesNumber());
                obj.put("description", b.getDescription());
                obj.put("cover_url", b.getCoverUrl());
                array.put(obj);
            }
            return array.toString(2);
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized int importLibraryFromJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return 0;
        int importedCount = 0;
        try {
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Book b = new Book();
                b.setId(obj.optString("id"));
                b.setTitle(obj.optString("title"));
                b.setAuthor(obj.optString("author"));
                b.setGenre(obj.optString("genre"));
                b.setSize(obj.optString("size"));
                b.setFormat(obj.optString("format"));
                b.setDownloadUrl(obj.optString("download_url"));
                b.setStatus(obj.optString("status", "В планах"));
                b.setLocalPath(obj.optString("local_path", null));
                b.setDateAdded(obj.optString("date_added"));
                b.setSeriesName(obj.optString("series_name", null));
                b.setSeriesId(obj.optString("series_id", null));
                b.setSeriesNumber(obj.optInt("series_number", 0));
                b.setDescription(obj.optString("description", null));
                b.setCoverUrl(obj.optString("cover_url", null));

                if (b.getId() != null && !b.getId().isEmpty()) {
                    addBook(b);
                    importedCount++;
                }
            }
        } catch (Exception ignored) {}
        return importedCount;
    }
}
