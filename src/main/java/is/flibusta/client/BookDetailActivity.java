package is.flibusta.client;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import is.flibusta.client.data.Book;
import is.flibusta.client.data.DatabaseHelper;
import is.flibusta.client.network.BookDownloader;
import is.flibusta.client.network.ImageLoader;

public class BookDetailActivity extends AppCompatActivity {

    private Book book;
    private DatabaseHelper db;

    private ImageView ivCover;
    private TextView tvTitle;
    private TextView tvAuthor;
    private TextView tvGenre;
    private TextView tvSize;
    private TextView tvDescription;

    private View layoutAuthor;
    private View layoutGenre;

    private TextView btnDownloadFb2;
    private TextView btnDownloadEpub;
    private TextView btnDownloadMobi;
    private TextView btnToLibrary;
    private TextView btnRead;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_detail);

        db = new DatabaseHelper(this);

        book = (Book) getIntent().getSerializableExtra("book");
        if (book == null) {
            Toast.makeText(this, "Книга не найдена", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        bindBookData();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btn_detail_back);
        btnBack.setOnClickListener(v -> finish());

        ivCover = findViewById(R.id.iv_detail_cover);
        tvTitle = findViewById(R.id.tv_detail_title);
        tvAuthor = findViewById(R.id.tv_detail_author);
        tvGenre = findViewById(R.id.tv_detail_genre);
        tvSize = findViewById(R.id.tv_detail_size);
        tvDescription = findViewById(R.id.tv_detail_description);

        layoutAuthor = findViewById(R.id.layout_detail_author);
        layoutGenre = findViewById(R.id.layout_detail_genre);

        btnDownloadFb2 = findViewById(R.id.btn_detail_download_fb2);
        btnDownloadEpub = findViewById(R.id.btn_detail_download_epub);
        btnDownloadMobi = findViewById(R.id.btn_detail_download_mobi);
        btnToLibrary = findViewById(R.id.btn_detail_to_library);
        btnRead = findViewById(R.id.btn_detail_read);
    }

    private void bindBookData() {
        tvTitle.setText(book.getTitle());
        tvAuthor.setText(book.getAuthor());
        tvGenre.setText(book.getGenre());

        String sizeText = book.getSize();
        if (sizeText != null && !sizeText.isEmpty()) {
            tvSize.setText("Размер: " + sizeText);
            tvSize.setVisibility(View.VISIBLE);
        } else {
            tvSize.setVisibility(View.GONE);
        }

        String desc = book.getDescription();
        if (desc != null && !desc.trim().isEmpty()) {
            tvDescription.setText(desc);
        } else {
            tvDescription.setText("Аннотация отсутствует для этого издания на сервере Флибусты.");
        }

        ImageLoader.loadCover(ivCover, book.getCoverUrl());

        // Accessibility descriptions
        layoutAuthor.setContentDescription("Автор: " + book.getAuthor() + ". Нажмите, чтобы открыть все книги автора");
        layoutGenre.setContentDescription("Жанр: " + book.getGenre() + ". Нажмите, чтобы найти книги этого жанра");

        // Author click -> open all books by this author
        layoutAuthor.setOnClickListener(v -> {
            String author = book.getAuthor();
            if (author != null && !author.equalsIgnoreCase("Не указан") && !author.equalsIgnoreCase("Неизвестный автор")) {
                Intent intent = new Intent(BookDetailActivity.this, BooksListActivity.class);
                intent.putExtra("type", "author");
                intent.putExtra("query", author);
                intent.putExtra("author", author);
                if (book.getAuthorId() != null && !book.getAuthorId().isEmpty()) {
                    intent.putExtra("author_id", book.getAuthorId());
                }
                intent.putExtra("title", "Автор: " + author);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Автор книги не указан", Toast.LENGTH_SHORT).show();
            }
        });

        // Genre click -> open all books in this genre
        layoutGenre.setOnClickListener(v -> {
            String genre = book.getGenre();
            if (genre != null && !genre.isEmpty()) {
                Intent intent = new Intent(BookDetailActivity.this, BooksListActivity.class);
                intent.putExtra("type", "genre_search");
                intent.putExtra("query", genre);
                intent.putExtra("title", "Жанр: " + genre);
                startActivity(intent);
            }
        });

        // Downloads
        btnDownloadFb2.setOnClickListener(v -> {
            BookDownloader.downloadBook(this, book, "fb2", this::updateLibraryButton);
        });

        btnDownloadEpub.setOnClickListener(v -> {
            BookDownloader.downloadBook(this, book, "epub", this::updateLibraryButton);
        });

        btnDownloadMobi.setOnClickListener(v -> {
            BookDownloader.downloadBook(this, book, "mobi", this::updateLibraryButton);
        });

        // Library toggle
        updateLibraryButton();
        btnToLibrary.setOnClickListener(v -> {
            boolean inLib = db.isBookInLibrary(book.getId());
            if (inLib) {
                db.removeBook(book.getId());
                Toast.makeText(this, "Удалено из библиотеки: " + book.getTitle(), Toast.LENGTH_SHORT).show();
            } else {
                db.addBook(book);
                Toast.makeText(this, "Добавлено на полку: " + book.getTitle(), Toast.LENGTH_SHORT).show();
            }
            updateLibraryButton();
        });

        // Read
        btnRead.setOnClickListener(v -> {
            BookDownloader.openBook(this, book);
        });
    }

    private void updateLibraryButton() {
        boolean inLib = db.isBookInLibrary(book.getId());
        if (inLib) {
            btnToLibrary.setText("В библиотеке");
            btnToLibrary.setTextColor(getResources().getColor(R.color.accent_green));
            btnToLibrary.setContentDescription("Книга уже в библиотеке. Нажмите, чтобы удалить с полки");
        } else {
            btnToLibrary.setText("В библиотеку");
            btnToLibrary.setTextColor(getResources().getColor(R.color.text_secondary));
            btnToLibrary.setContentDescription("Добавить книгу в библиотеку на полку");
        }
    }
}
