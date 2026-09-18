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
import is.flibusta.client.data.BookPage;
import is.flibusta.client.data.DatabaseHelper;
import is.flibusta.client.network.BookDownloader;
import is.flibusta.client.network.FlibustaApi;

import java.util.ArrayList;
import java.util.List;

public class BooksListActivity extends AppCompatActivity {

    private String type;
    private String query;
    private String seriesId;
    private String authorId;
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

    // Pagination via exact nextPageUrl from OPDS/HTML
    private LinearLayout layoutBooksPagination;
    private TextView tvBooksPageInfo;
    private ProgressBar pbBooksLoadMore;
    private TextView btnBooksLoadMore;
    private String currentNextPageUrl = null;
    private boolean isLoadingMore = false;
    private int pageNumber = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_books_list);

        db = new DatabaseHelper(this);

        Intent intent = getIntent();
        type = intent.getStringExtra("type");
        query = intent.getStringExtra("query");
        seriesId = intent.getStringExtra("series_id");
        authorId = intent.getStringExtra("author_id");
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
                if (dy > 0 && currentNextPageUrl != null && !isLoadingMore) {
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
        currentNextPageUrl = null;
        pageNumber = 1;
        isLoadingMore = false;

        pbLoading.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        rvBooks.setVisibility(View.GONE);
        if (layoutBooksPagination != null) {
            layoutBooksPagination.setVisibility(View.GONE);
        }

        FlibustaApi.Callback<BookPage> callback = new FlibustaApi.Callback<BookPage>() {
            @Override
            public void onSuccess(BookPage page) {
                pbLoading.setVisibility(View.GONE);
                List<Book> books = page != null ? page.getBooks() : null;
                if (books == null || books.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    rvBooks.setVisibility(View.GONE);
                    tvSubtitle.setVisibility(View.GONE);
                    if (layoutBooksPagination != null) {
                        layoutBooksPagination.setVisibility(View.GONE);
                    }
                } else {
                    currentNextPageUrl = page.getNextPageUrl();

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
                    tvSubtitle.setText("Книг: " + adapter.getItemCount());

                    if (currentNextPageUrl != null) {
                        layoutBooksPagination.setVisibility(View.VISIBLE);
                        tvBooksPageInfo.setText("Страница 1 • Книг: " + adapter.getItemCount());
                        btnBooksLoadMore.setVisibility(View.VISIBLE);
                    } else {
                        layoutBooksPagination.setVisibility(View.GONE);
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
            FlibustaApi.loadSeriesBooksPage(seriesId, null, defaultAuthor, callback);
        } else if ("genre".equals(type) && genreUrl != null) {
            FlibustaApi.fetchBooksPage(genreUrl, callback);
        } else if ("author".equals(type)) {
            if (authorId != null && !authorId.isEmpty()) {
                FlibustaApi.loadAuthorBooksPage(authorId, defaultAuthor, callback);
            } else if (query != null) {
                FlibustaApi.searchBooksByAuthorPage(query, null, callback);
            } else {
                pbLoading.setVisibility(View.GONE);
                layoutEmpty.setVisibility(View.VISIBLE);
            }
        } else if ("genre_search".equals(type) && query != null) {
            FlibustaApi.searchBooksPage(query, null, callback);
        } else if (query != null) {
            FlibustaApi.searchBooksPage(query, null, callback);
        } else {
            pbLoading.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
        }
    }

    private void loadMoreData() {
        if (isLoadingMore || currentNextPageUrl == null || currentNextPageUrl.isEmpty()) return;

        isLoadingMore = true;
        pbBooksLoadMore.setVisibility(View.VISIBLE);
        btnBooksLoadMore.setEnabled(false);

        String urlToLoad = currentNextPageUrl;

        FlibustaApi.fetchBooksPage(urlToLoad, new FlibustaApi.Callback<BookPage>() {
            @Override
            public void onSuccess(BookPage nextPage) {
                isLoadingMore = false;
                pbBooksLoadMore.setVisibility(View.GONE);
                btnBooksLoadMore.setEnabled(true);

                List<Book> moreBooks = nextPage != null ? nextPage.getBooks() : null;
                if (moreBooks == null || moreBooks.isEmpty()) {
                    currentNextPageUrl = null;
                    btnBooksLoadMore.setVisibility(View.GONE);
                    tvBooksPageInfo.setText("Все книги загружены • Всего: " + adapter.getItemCount());
                    Toast.makeText(BooksListActivity.this, "Все доступные книги загружены", Toast.LENGTH_SHORT).show();
                    return;
                }

                pageNumber++;
                currentNextPageUrl = nextPage.getNextPageUrl();

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
                tvBooksPageInfo.setText("Страница " + pageNumber + " • Книг: " + adapter.getItemCount());

                if (currentNextPageUrl == null) {
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
        });
    }
}
