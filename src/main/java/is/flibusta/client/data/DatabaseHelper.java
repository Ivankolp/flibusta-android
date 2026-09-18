package is.flibusta.client.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "flibusta_library.db";
    private static final int DATABASE_VERSION = 1;

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
                + COL_DATE_ADDED + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LIBRARY);
        onCreate(db);
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

        String dateStr = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date());
        cv.put(COL_DATE_ADDED, dateStr);

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
                list.add(b);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }
}
