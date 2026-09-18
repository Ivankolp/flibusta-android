package is.flibusta.client;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import is.flibusta.client.adapter.BookAdapter;
import is.flibusta.client.data.Book;
import is.flibusta.client.data.DatabaseHelper;
import is.flibusta.client.network.BookDownloader;
import is.flibusta.client.network.FlibustaApi;

import java.util.ArrayList;
import java.util.List;

public class BooksListActivity extends AppCompatActivity {

    private String type;
    private String query;
    private String seriesId;
    private String genreUrl;
    private String displayTitle;
    private String defaultAuthor;

    private TextView tvTitle;
    private TextView tvSubtitle;
    private ProgressBar pbLoading;
    private LinearLayout layoutError;
    private TextView btnRetry;
    private LinearLayout layoutEmpty;
    private RecyclerView rvBooks;

    private BookAdapter adapter;
    private DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_books_list);

        db = new DatabaseHelper(this);

        Intent intent = getIntent();
        type = intent.getStringExtra("type");
        query = intent.getStringExtra("query");
        seriesId = intent.getStringExtra("series_id");
        genreUrl = intent.getStringExtra("genre_url");
        displayTitle = intent.getStringExtra("title");
        defaultAuthor = intent.getStringExtra("author");
        if ((defaultAuthor == null || defaultAuthor.isEmpty()) && "author".equals(type)) {
            defaultAuthor = query;
        }

        initViews();
        loadData();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btn_list_back);
        btnBack.setOnClickListener(v -> finish());

        tvTitle = findViewById(R.id.tv_list_title);
        tvSubtitle = findViewById(R.id.tv_list_subtitle);
        pbLoading = findViewById(R.id.pb_list_loading);
        layoutError = findViewById(R.id.layout_list_error);
        btnRetry = findViewById(R.id.btn_list_retry);
        layoutEmpty = findViewById(R.id.layout_list_empty);
        rvBooks = findViewById(R.id.rv_books_list);

        if (displayTitle != null && !displayTitle.isEmpty()) {
            tvTitle.setText(displayTitle);
        } else {
            tvTitle.setText("Список книг");
        }

        rvBooks.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BookAdapter(this, new ArrayList<>());
        adapter.setListener(new BookAdapter.OnBookActionListener() {
            @Override
            public void onBookClick(Book book) {
                Intent detailIntent = new Intent(BooksListActivity.this, BookDetailActivity.class);
                detailIntent.putExtra("book", book);
                startActivity(detailIntent);
            }

            @Override
            public void onDownload(Book book) {
                BookDownloader.downloadBook(BooksListActivity.this, book, "fb2");
            }

            @Override
            public void onAddToLibrary(Book book) {
                db.addBook(book);
                adapter.notifyDataSetChanged();
                Toast.makeText(BooksListActivity.this, "Добавлено на полку: " + book.getTitle(), Toast.LENGTH_SHORT).show();
            }
        });
        rvBooks.setAdapter(adapter);

        btnRetry.setOnClickListener(v -> loadData());
    }

    private void loadData() {
        pbLoading.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        rvBooks.setVisibility(View.GONE);

        FlibustaApi.Callback<List<Book>> callback = new FlibustaApi.Callback<List<Book>>() {
            @Override
            public void onSuccess(List<Book> books) {
                pbLoading.setVisibility(View.GONE);
                if (books == null || books.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    rvBooks.setVisibility(View.GONE);
                    tvSubtitle.setVisibility(View.GONE);
                } else {
                    // Ensure author name is preserved for all books when viewing author's books
                    if ("author".equals(type) && defaultAuthor != null && !defaultAuthor.isEmpty()) {
                        for (Book b : books) {
                            if (b.getAuthor() == null || b.getAuthor().isEmpty() ||
                                    b.getAuthor().equalsIgnoreCase("Не указан") ||
                                    b.getAuthor().equalsIgnoreCase("Неизвестный автор")) {
                                b.setAuthor(defaultAuthor);
                            }
                        }
                    }

                    layoutEmpty.setVisibility(View.GONE);
                    rvBooks.setVisibility(View.VISIBLE);
                    adapter.updateList(books);
                    tvSubtitle.setVisibility(View.VISIBLE);
                    tvSubtitle.setText("Книг: " + books.size());
                }
            }

            @Override
            public void onError(Exception e) {
                pbLoading.setVisibility(View.GONE);
                layoutError.setVisibility(View.VISIBLE);
                rvBooks.setVisibility(View.GONE);
            }
        };

        if ("series".equals(type) && seriesId != null) {
            FlibustaApi.loadSeriesBooks(seriesId, defaultAuthor, callback);
        } else if ("genre".equals(type) && genreUrl != null) {
            FlibustaApi.fetchBooksFromUrl(genreUrl, callback);
        } else if ("author".equals(type) && query != null) {
            FlibustaApi.searchBooksByAuthor(query, callback);
        } else if ("genre_search".equals(type) && query != null) {
            FlibustaApi.searchBooks(query, callback);
        } else if (query != null) {
            FlibustaApi.searchBooks(query, callback);
        } else {
            pbLoading.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
        }
    }
}
