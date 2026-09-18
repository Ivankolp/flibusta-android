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

    // Pagination
    private LinearLayout layoutBooksPagination;
    private TextView tvBooksPageInfo;
    private ProgressBar pbBooksLoadMore;
    private TextView btnBooksLoadMore;
    private int currentPage = 0;
    private boolean isLoadingMore = false;
    private boolean hasMorePages = true;

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

        // Pagination views
        layoutBooksPagination = findViewById(R.id.layout_books_pagination);
        tvBooksPageInfo = findViewById(R.id.tv_books_page_info);
        pbBooksLoadMore = findViewById(R.id.pb_books_load_more);
        btnBooksLoadMore = findViewById(R.id.btn_books_load_more);

        if (displayTitle != null && !displayTitle.isEmpty()) {
            tvTitle.setText(displayTitle);
        } else {
            tvTitle.setText("Список книг");
        }

        LinearLayoutManager lm = new LinearLayoutManager(this);
        rvBooks.setLayoutManager(lm);
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

        // Scroll listener for smooth infinite scrolling
        rvBooks.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && hasMorePages && !isLoadingMore) {
                    if (lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
                        loadMoreData();
                    }
                }
            }
        });

        btnRetry.setOnClickListener(v -> loadData());
        btnBooksLoadMore.setOnClickListener(v -> loadMoreData());
    }

    private void loadData() {
        currentPage = 0;
        hasMorePages = true;
        isLoadingMore = false;

        pbLoading.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        rvBooks.setVisibility(View.GONE);
        if (layoutBooksPagination != null) {
            layoutBooksPagination.setVisibility(View.GONE);
        }

        FlibustaApi.Callback<List<Book>> callback = new FlibustaApi.Callback<List<Book>>() {
            @Override
            public void onSuccess(List<Book> books) {
                pbLoading.setVisibility(View.GONE);
                if (books == null || books.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    rvBooks.setVisibility(View.GONE);
                    tvSubtitle.setVisibility(View.GONE);
                    if (layoutBooksPagination != null) {
                        layoutBooksPagination.setVisibility(View.GONE);
                    }
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

                    if (books.size() >= 20) {
                        layoutBooksPagination.setVisibility(View.VISIBLE);
                        tvBooksPageInfo.setText("Страница 1 • Книг: " + adapter.getItemCount());
                        btnBooksLoadMore.setVisibility(View.VISIBLE);
                    } else {
                        layoutBooksPagination.setVisibility(View.GONE);
                        hasMorePages = false;
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                pbLoading.setVisibility(View.GONE);
                layoutError.setVisibility(View.VISIBLE);
                rvBooks.setVisibility(View.GONE);
                if (layoutBooksPagination != null) {
                    layoutBooksPagination.setVisibility(View.GONE);
                }
            }
        };

        if ("series".equals(type) && seriesId != null) {
            FlibustaApi.loadSeriesBooks(seriesId, 0, defaultAuthor, callback);
        } else if ("genre".equals(type) && genreUrl != null) {
            FlibustaApi.fetchBooksFromUrl(genreUrl, 0, callback);
        } else if ("author".equals(type) && query != null) {
            FlibustaApi.searchBooksByAuthor(query, 0, callback);
        } else if ("genre_search".equals(type) && query != null) {
            FlibustaApi.searchBooks(query, 0, callback);
        } else if (query != null) {
            FlibustaApi.searchBooks(query, 0, callback);
        } else {
            pbLoading.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
        }
    }

    private void loadMoreData() {
        if (isLoadingMore || !hasMorePages) return;

        isLoadingMore = true;
        pbBooksLoadMore.setVisibility(View.VISIBLE);
        btnBooksLoadMore.setEnabled(false);

        int nextPage = currentPage + 1;

        FlibustaApi.Callback<List<Book>> callback = new FlibustaApi.Callback<List<Book>>() {
            @Override
            public void onSuccess(List<Book> moreBooks) {
                isLoadingMore = false;
                pbBooksLoadMore.setVisibility(View.GONE);
                btnBooksLoadMore.setEnabled(true);

                if (moreBooks == null || moreBooks.isEmpty()) {
                    hasMorePages = false;
                    btnBooksLoadMore.setVisibility(View.GONE);
                    tvBooksPageInfo.setText("Все книги загружены • Всего: " + adapter.getItemCount());
                    Toast.makeText(BooksListActivity.this, "Все доступные книги загружены", Toast.LENGTH_SHORT).show();
                    return;
                }

                currentPage = nextPage;
                if ("author".equals(type) && defaultAuthor != null && !defaultAuthor.isEmpty()) {
                    for (Book b : moreBooks) {
                        if (b.getAuthor() == null || b.getAuthor().isEmpty() ||
                                b.getAuthor().equalsIgnoreCase("Не указан") ||
                                b.getAuthor().equalsIgnoreCase("Неизвестный автор")) {
                            b.setAuthor(defaultAuthor);
                        }
                    }
                }

                adapter.addBooks(moreBooks);
                tvSubtitle.setText("Книг: " + adapter.getItemCount());
                tvBooksPageInfo.setText("Страница " + (currentPage + 1) + " • Книг: " + adapter.getItemCount());

                if (moreBooks.size() < 20) {
                    hasMorePages = false;
                    btnBooksLoadMore.setVisibility(View.GONE);
                    tvBooksPageInfo.setText("Все книги загружены • Всего: " + adapter.getItemCount());
                }

                Toast.makeText(BooksListActivity.this, "Загружено ещё " + moreBooks.size() + " книг", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                isLoadingMore = false;
                pbBooksLoadMore.setVisibility(View.GONE);
                btnBooksLoadMore.setEnabled(true);
                Toast.makeText(BooksListActivity.this, "Не удалось загрузить следующую страницу", Toast.LENGTH_SHORT).show();
            }
        };

        if ("series".equals(type) && seriesId != null) {
            FlibustaApi.loadSeriesBooks(seriesId, nextPage, defaultAuthor, callback);
        } else if ("genre".equals(type) && genreUrl != null) {
            FlibustaApi.fetchBooksFromUrl(genreUrl, nextPage, callback);
        } else if ("author".equals(type) && query != null) {
            FlibustaApi.searchBooksByAuthor(query, nextPage, callback);
        } else if ("genre_search".equals(type) && query != null) {
            FlibustaApi.searchBooks(query, nextPage, callback);
        } else if (query != null) {
            FlibustaApi.searchBooks(query, nextPage, callback);
        } else {
            isLoadingMore = false;
            pbBooksLoadMore.setVisibility(View.GONE);
            btnBooksLoadMore.setEnabled(true);
        }
    }
}
