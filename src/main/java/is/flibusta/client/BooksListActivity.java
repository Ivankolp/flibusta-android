package is.flibusta.client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import is.flibusta.client.adapter.BookAdapter;
import is.flibusta.client.data.Book;
import is.flibusta.client.data.BookPage;
import is.flibusta.client.data.DatabaseHelper;
import is.flibusta.client.network.BookDownloader;
import is.flibusta.client.network.FlibustaApi;
import is.flibusta.client.service.SeriesDownloaderService;
import is.flibusta.client.util.BookSorter;

import java.util.ArrayList;
import java.util.List;

public class BooksListActivity extends AppCompatActivity {

    private String type;
    private String query;
    private String seriesId;
    private String seriesName;
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

    // Sorting & Series Download Controls
    private TextView btnSortSelector;
    private LinearLayout layoutSeriesAction;
    private TextView btnDownloadSeries;
    private LinearLayout layoutSeriesProgress;
    private TextView tvSeriesProgress;
    private ProgressBar pbSeriesDownload;

    private BookSorter.SortMode currentSortMode = BookSorter.SortMode.DATE_DESC;

    private BookAdapter adapter;
    private DatabaseHelper db;

    // Pagination
    private LinearLayout layoutBooksPagination;
    private TextView tvBooksPageInfo;
    private ProgressBar pbBooksLoadMore;
    private TextView btnBooksLoadMore;
    private String currentNextPageUrl = null;
    private boolean isLoadingMore = false;
    private int pageNumber = 1;

    private final BroadcastReceiver seriesProgressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            String sId = intent.getStringExtra(SeriesDownloaderService.EXTRA_SERIES_ID);

            if (seriesId != null && seriesId.equals(sId)) {
                if (SeriesDownloaderService.ACTION_SERIES_PROGRESS.equals(action)) {
                    int current = intent.getIntExtra("current", 0);
                    int total = intent.getIntExtra("total", 0);
                    String bookTitle = intent.getStringExtra("book_title");

                    layoutSeriesProgress.setVisibility(View.VISIBLE);
                    pbSeriesDownload.setMax(total);
                    pbSeriesDownload.setProgress(current);
                    tvSeriesProgress.setText(String.format("Скачивание: %d из %d (%d%%): %s", current, total, (total > 0 ? (current * 100) / total : 0), bookTitle != null ? bookTitle : ""));
                } else if (SeriesDownloaderService.ACTION_SERIES_COMPLETE.equals(action)) {
                    int total = intent.getIntExtra("total", 0);
                    layoutSeriesProgress.setVisibility(View.GONE);
                    Toast.makeText(BooksListActivity.this, "Серия скачана! Все " + total + " книг сохранены.", Toast.LENGTH_LONG).show();
                    btnDownloadSeries.setText("Серия скачана (все книги)");
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_books_list);

        db = new DatabaseHelper(this);

        Intent intent = getIntent();
        type = intent.getStringExtra("type");
        query = intent.getStringExtra("query");
        seriesId = intent.getStringExtra("series_id");
        seriesName = intent.getStringExtra("series_name");
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

    @Override
    protected void onResume() {
        super.onResume();
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(SeriesDownloaderService.ACTION_SERIES_PROGRESS);
            filter.addAction(SeriesDownloaderService.ACTION_SERIES_COMPLETE);
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(seriesProgressReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(seriesProgressReceiver, filter);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        checkActiveSeriesDownload();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(seriesProgressReceiver);
        } catch (Exception ignored) {}
    }

    private void checkActiveSeriesDownload() {
        if ("series".equals(type) && seriesId != null && SeriesDownloaderService.isDownloading(seriesId)) {
            layoutSeriesProgress.setVisibility(View.VISIBLE);
            int current = SeriesDownloaderService.getProgress(seriesId);
            int total = SeriesDownloaderService.getTotal(seriesId);
            pbSeriesDownload.setMax(total);
            pbSeriesDownload.setProgress(current);
            tvSeriesProgress.setText(String.format("Скачивание: %d из %d (%d%%)...", current, total, total > 0 ? (current * 100) / total : 0));
        }
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

        // Sorting & Series Download views
        btnSortSelector = findViewById(R.id.btn_sort_selector);
        layoutSeriesAction = findViewById(R.id.layout_series_action);
        btnDownloadSeries = findViewById(R.id.btn_download_series);
        layoutSeriesProgress = findViewById(R.id.layout_series_progress);
        tvSeriesProgress = findViewById(R.id.tv_series_progress);
        pbSeriesDownload = findViewById(R.id.pb_series_download);

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

        // Setup series actions
        if ("series".equals(type) && seriesId != null) {
            layoutSeriesAction.setVisibility(View.VISIBLE);
            currentSortMode = BookSorter.SortMode.SERIES_NUM_ASC;
            btnSortSelector.setText(currentSortMode.getTitle());
            btnSortSelector.setContentDescription("Выбрать сортировку списка книг. Текущая: " + currentSortMode.getTitle());

            btnDownloadSeries.setOnClickListener(v -> {
                List<Book> loaded = adapter != null ? adapter.getBooks() : null;
                ArrayList<Book> passBooks = (loaded != null && !loaded.isEmpty()) ? new ArrayList<>(loaded) : null;
                SeriesDownloaderService.startDownload(this, seriesId, displayTitle, defaultAuthor, passBooks);
                layoutSeriesProgress.setVisibility(View.VISIBLE);
                tvSeriesProgress.setText("Запуск фоновой загрузки серии...");
                Toast.makeText(this, "Запущена фоновая загрузка серии в память устройства", Toast.LENGTH_SHORT).show();
            });
        } else if ("library_series".equals(type)) {
            layoutSeriesAction.setVisibility(View.GONE);
            currentSortMode = BookSorter.SortMode.SERIES_NUM_ASC;
            btnSortSelector.setText(currentSortMode.getTitle());
            btnSortSelector.setContentDescription("Выбрать сортировку списка книг. Текущая: " + currentSortMode.getTitle());
        }

        btnSortSelector.setOnClickListener(v -> showSortDialog());

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

        // Infinite scroll listener
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

    private void showSortDialog() {
        BookSorter.SortMode[] modes = BookSorter.SortMode.values();
        String[] titles = new String[modes.length];
        int selectedIndex = 0;
        for (int i = 0; i < modes.length; i++) {
            titles[i] = modes[i].getTitle();
            if (modes[i] == currentSortMode) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Сортировка списка")
                .setSingleChoiceItems(titles, selectedIndex, (dialog, which) -> {
                    currentSortMode = modes[which];
                    btnSortSelector.setText(currentSortMode.getTitle());
                    btnSortSelector.setContentDescription("Выбрать сортировку списка книг. Текущая: " + currentSortMode.getTitle());
                    adapter.sort(currentSortMode);
                    dialog.dismiss();
                    Toast.makeText(BooksListActivity.this, "Применена сортировка: " + currentSortMode.getTitle(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
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

        if ("library_series".equals(type)) {
            pbLoading.setVisibility(View.GONE);
            String targetSeries = seriesName != null ? seriesName : (displayTitle != null ? displayTitle.replace("Серия: ", "") : "");
            List<Book> books = db.getBooksBySeries(targetSeries);
            if (books == null || books.isEmpty()) {
                layoutEmpty.setVisibility(View.VISIBLE);
                rvBooks.setVisibility(View.GONE);
                tvSubtitle.setVisibility(View.GONE);
            } else {
                BookSorter.sort(books, currentSortMode);
                layoutEmpty.setVisibility(View.GONE);
                rvBooks.setVisibility(View.VISIBLE);
                adapter.updateList(books);
                tvSubtitle.setVisibility(View.VISIBLE);
                tvSubtitle.setText("Скачано книг: " + adapter.getItemCount());
            }
            return;
        }

        if (("series".equals(type) || "series_name".equals(type)) && !FlibustaApi.isOnline(this)) {
            String targetSeries = seriesName != null ? seriesName : (query != null ? query : (displayTitle != null ? displayTitle.replace("Серия: ", "") : ""));
            List<Book> offlineBooks = db.getBooksBySeries(targetSeries);
            if (offlineBooks != null && !offlineBooks.isEmpty()) {
                pbLoading.setVisibility(View.GONE);
                BookSorter.sort(offlineBooks, currentSortMode);
                layoutEmpty.setVisibility(View.GONE);
                rvBooks.setVisibility(View.VISIBLE);
                adapter.updateList(offlineBooks);
                tvSubtitle.setVisibility(View.VISIBLE);
                tvSubtitle.setText("Скачано книг: " + adapter.getItemCount());
                Toast.makeText(this, "Офлайн-режим: показаны скачанные книги серии", Toast.LENGTH_SHORT).show();
                return;
            }
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

                    // Apply current sort mode
                    BookSorter.sort(books, currentSortMode);

                    layoutEmpty.setVisibility(View.GONE);
                    rvBooks.setVisibility(View.VISIBLE);
                    adapter.updateList(books);
                    tvSubtitle.setVisibility(View.VISIBLE);
                    tvSubtitle.setText("Книг: " + adapter.getItemCount());

                    if (btnDownloadSeries != null && "series".equals(type)) {
                        btnDownloadSeries.setText(String.format("Скачать всю серию целиком (%d книг)", adapter.getItemCount()));
                    }

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
                    layoutBooksPagination.setVisibility(View.GONE);
                } else {
                    currentNextPageUrl = nextPage.getNextPageUrl();
                    pageNumber++;

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
                    adapter.sort(currentSortMode);

                    tvSubtitle.setText("Книг: " + adapter.getItemCount());
                    tvBooksPageInfo.setText("Страница " + pageNumber + " • Всего книг: " + adapter.getItemCount());

                    if (btnDownloadSeries != null && "series".equals(type)) {
                        btnDownloadSeries.setText(String.format("Скачать всю серию целиком (%d книг)", adapter.getItemCount()));
                    }

                    if (currentNextPageUrl == null) {
                        layoutBooksPagination.setVisibility(View.GONE);
                    }
                }
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
