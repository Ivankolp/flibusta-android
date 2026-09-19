package is.flibusta.client;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import is.flibusta.client.data.Book;
import is.flibusta.client.data.DatabaseHelper;
import is.flibusta.client.network.BookDownloader;
import is.flibusta.client.network.FlibustaApi;
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
    private ProgressBar pbDetailDesc;
    private TextView btnDetailRetryDesc;

    private View layoutAuthor;
    private View layoutGenre;
    private View layoutSeries;
    private TextView tvSeries;

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
        pbDetailDesc = findViewById(R.id.pb_detail_desc);
        btnDetailRetryDesc = findViewById(R.id.btn_detail_retry_desc);

        layoutAuthor = findViewById(R.id.layout_detail_author);
        layoutGenre = findViewById(R.id.layout_detail_genre);
        layoutSeries = findViewById(R.id.layout_detail_series);
        tvSeries = findViewById(R.id.tv_detail_series);

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

        updateSeriesView();

        String desc = book.getDescription();
        boolean hasDetailedDesc = desc != null && !desc.trim().isEmpty() && desc.trim().length() > 50 && !desc.trim().startsWith("Размер:");
        if (hasDetailedDesc) {
            tvDescription.setText(desc);
            if (btnDetailRetryDesc != null) btnDetailRetryDesc.setVisibility(View.GONE);
        } else {
            if (desc != null && !desc.trim().isEmpty()) {
                tvDescription.setText(desc);
            } else {
                tvDescription.setText("Загрузка аннотации с Флибусты...");
            }
            loadAnnotation();
        }

        if (btnDetailRetryDesc != null) {
            btnDetailRetryDesc.setOnClickListener(v -> loadAnnotation());
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

    private void updateSeriesView() {
        if (layoutSeries == null || tvSeries == null) return;
        String sName = book.getSeriesName();
        if (sName != null && !sName.trim().isEmpty()) {
            layoutSeries.setVisibility(View.VISIBLE);
            String text = "Серия: " + sName;
            if (book.getSeriesNumber() > 0) {
                text += " (книга " + book.getSeriesNumber() + ")";
            }
            tvSeries.setText(text);
            layoutSeries.setContentDescription("Серия: " + sName + (book.getSeriesNumber() > 0 ? ", книга " + book.getSeriesNumber() : "") + ". Нажмите, чтобы открыть все книги этой серии");
            layoutSeries.setOnClickListener(v -> {
                Intent intent = new Intent(BookDetailActivity.this, BooksListActivity.class);
                List<Book> localSeriesBooks = db.getBooksBySeries(sName);
                if ((localSeriesBooks != null && !localSeriesBooks.isEmpty()) && !FlibustaApi.isOnline(BookDetailActivity.this)) {
                    intent.putExtra("type", "library_series");
                    intent.putExtra("series_name", sName);
                } else if (book.getSeriesId() != null && !book.getSeriesId().isEmpty()) {
                    intent.putExtra("type", "series");
                    intent.putExtra("series_id", book.getSeriesId());
                    intent.putExtra("series_name", sName);
                } else if (localSeriesBooks != null && !localSeriesBooks.isEmpty()) {
                    intent.putExtra("type", "library_series");
                    intent.putExtra("series_name", sName);
                } else {
                    intent.putExtra("type", "series_name");
                    intent.putExtra("query", sName);
                    intent.putExtra("series_name", sName);
                }
                intent.putExtra("author", book.getAuthor());
                intent.putExtra("title", "Серия: " + sName);
                startActivity(intent);
            });
        } else {
            layoutSeries.setVisibility(View.GONE);
        }
    }

    private void loadAnnotation() {
        if (!FlibustaApi.isOnline(this)) {
            if (pbDetailDesc != null) pbDetailDesc.setVisibility(View.GONE);
            if (btnDetailRetryDesc != null) btnDetailRetryDesc.setVisibility(View.VISIBLE);
            if (tvDescription != null && tvDescription.getText().toString().contains("Загрузка")) {
                tvDescription.setText("Аннотация недоступна в офлайн-режиме (нет подключения к интернету).");
            }
            return;
        }

        if (pbDetailDesc != null) pbDetailDesc.setVisibility(View.VISIBLE);
        if (btnDetailRetryDesc != null) btnDetailRetryDesc.setVisibility(View.GONE);

        FlibustaApi.fetchBookAnnotation(book.getId(), new FlibustaApi.Callback<FlibustaApi.BookAnnotationResult>() {
            @Override
            public void onSuccess(FlibustaApi.BookAnnotationResult result) {
                if (pbDetailDesc != null) pbDetailDesc.setVisibility(View.GONE);
                if (result != null) {
                    if (result.annotation != null && !result.annotation.trim().isEmpty()) {
                        tvDescription.setText(result.annotation);
                        book.setDescription(result.annotation);
                    } else if (tvDescription.getText().toString().contains("Загрузка")) {
                        tvDescription.setText("Аннотация отсутствует для этого издания на сервере Флибусты.");
                    }

                    if ((book.getSeriesName() == null || book.getSeriesName().isEmpty()) && result.seriesName != null && !result.seriesName.isEmpty()) {
                        book.setSeriesName(result.seriesName);
                        book.setSeriesId(result.seriesId);
                        book.setSeriesNumber(result.seriesNumber);
                        updateSeriesView();
                    }

                    if ((book.getCoverUrl() == null || book.getCoverUrl().isEmpty()) && result.coverUrl != null && !result.coverUrl.isEmpty()) {
                        book.setCoverUrl(result.coverUrl);
                        ImageLoader.loadCover(ivCover, result.coverUrl);
                    }

                    if (db.isBookInLibrary(book.getId())) {
                        db.addBook(book);
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                if (pbDetailDesc != null) pbDetailDesc.setVisibility(View.GONE);
                if (btnDetailRetryDesc != null) btnDetailRetryDesc.setVisibility(View.VISIBLE);
                if (tvDescription != null && tvDescription.getText().toString().contains("Загрузка")) {
                    tvDescription.setText("Не удалось загрузить подробную аннотацию с сервера.");
                }
            }
        });
    }
}
