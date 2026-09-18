package is.flibusta.client;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import is.flibusta.client.adapter.BookAdapter;
import is.flibusta.client.adapter.LibraryAdapter;
import is.flibusta.client.adapter.SeriesAdapter;
import is.flibusta.client.data.Book;
import is.flibusta.client.data.DatabaseHelper;
import is.flibusta.client.data.Series;
import is.flibusta.client.network.BookDownloader;
import is.flibusta.client.network.FlibustaApi;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Views
    private View viewCatalog;
    private View viewSearch;
    private View viewLibrary;
    private BottomNavigationView bottomNav;
    private TextView btnMirrorStatus;

    // Database
    private DatabaseHelper db;

    // Catalog Tab
    private ProgressBar pbCatalogLoading;
    private LinearLayout layoutCatalogError;
    private TextView btnCatalogRetry;
    private ScrollView scrollCatalogContent;
    private View cardFeatured;
    private TextView tvFeaturedTitle;
    private TextView tvFeaturedAuthor;
    private TextView tvFeaturedDesc;
    private TextView btnFeaturedToLibrary;
    private TextView btnFeaturedDownload;
    private TextView tvHeaderSeries;
    private RecyclerView rvPopularSeries;
    private RecyclerView rvRecommendedBooks;
    private SeriesAdapter popularSeriesAdapter;
    private BookAdapter recommendedBooksAdapter;
    private Book currentFeaturedBook;

    // Search Tab
    private EditText etSearchQuery;
    private TextView btnSearchGo;
    private TextView chipSearchBooks;
    private TextView chipSearchSeries;
    private ProgressBar pbSearchLoading;
    private LinearLayout layoutSearchEmpty;
    private RecyclerView rvSearchResults;
    private BookAdapter searchBooksAdapter;
    private SeriesAdapter searchSeriesAdapter;
    private boolean isSearchBooksMode = true;

    // Library Tab
    private TextView chipLibAll;
    private TextView chipLibReading;
    private TextView chipLibDone;
    private TextView chipLibPlanned;
    private LinearLayout layoutLibraryEmpty;
    private RecyclerView rvLibraryBooks;
    private LibraryAdapter libraryAdapter;
    private String currentLibFilter = "Все";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new DatabaseHelper(this);

        initViews();
        setupCatalogTab();
        setupSearchTab();
        setupLibraryTab();
        setupNavigation();

        // Load live catalog from Flibusta
        loadLiveCatalog();
    }

    private void initViews() {
        viewCatalog = findViewById(R.id.view_catalog);
        viewSearch = findViewById(R.id.view_search);
        viewLibrary = findViewById(R.id.view_library);
        bottomNav = findViewById(R.id.bottom_navigation);
        btnMirrorStatus = findViewById(R.id.btn_mirror_status);

        btnMirrorStatus.setOnClickListener(v -> showMirrorDialog());
    }

    private void setupCatalogTab() {
        pbCatalogLoading = findViewById(R.id.pb_catalog_loading);
        layoutCatalogError = findViewById(R.id.layout_catalog_error);
        btnCatalogRetry = findViewById(R.id.btn_catalog_retry);
        scrollCatalogContent = findViewById(R.id.scroll_catalog_content);

        cardFeatured = findViewById(R.id.card_featured);
        tvFeaturedTitle = findViewById(R.id.tv_featured_title);
        tvFeaturedAuthor = findViewById(R.id.tv_featured_author);
        tvFeaturedDesc = findViewById(R.id.tv_featured_desc);
        btnFeaturedToLibrary = findViewById(R.id.btn_featured_to_library);
        btnFeaturedDownload = findViewById(R.id.btn_featured_download);

        tvHeaderSeries = findViewById(R.id.tv_header_series);
        rvPopularSeries = findViewById(R.id.rv_popular_series);
        rvPopularSeries.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        popularSeriesAdapter = new SeriesAdapter(this, new ArrayList<>());
        popularSeriesAdapter.setListener(this::showSeriesDetailsDialog);
        rvPopularSeries.setAdapter(popularSeriesAdapter);

        rvRecommendedBooks = findViewById(R.id.rv_recommended_books);
        rvRecommendedBooks.setLayoutManager(new LinearLayoutManager(this));
        recommendedBooksAdapter = new BookAdapter(this, new ArrayList<>());
        recommendedBooksAdapter.setListener(new BookAdapter.OnBookActionListener() {
            @Override
            public void onDownload(Book book) {
                BookDownloader.downloadBook(MainActivity.this, book, "fb2");
                refreshLibrary();
            }

            @Override
            public void onAddToLibrary(Book book) {
                db.addBook(book);
                recommendedBooksAdapter.notifyDataSetChanged();
                refreshLibrary();
                Toast.makeText(MainActivity.this, "Добавлено на полку: " + book.getTitle(), Toast.LENGTH_SHORT).show();
            }
        });
        rvRecommendedBooks.setAdapter(recommendedBooksAdapter);

        btnCatalogRetry.setOnClickListener(v -> loadLiveCatalog());

        btnFeaturedToLibrary.setOnClickListener(v -> {
            if (currentFeaturedBook != null) {
                db.addBook(currentFeaturedBook);
                Toast.makeText(this, "«" + currentFeaturedBook.getTitle() + "» добавлен на полку!", Toast.LENGTH_SHORT).show();
                refreshLibrary();
            }
        });

        btnFeaturedDownload.setOnClickListener(v -> {
            if (currentFeaturedBook != null) {
                BookDownloader.downloadBook(this, currentFeaturedBook, "fb2");
                refreshLibrary();
            }
        });
    }

    private void loadLiveCatalog() {
        pbCatalogLoading.setVisibility(View.VISIBLE);
        scrollCatalogContent.setVisibility(View.GONE);
        layoutCatalogError.setVisibility(View.GONE);

        FlibustaApi.fetchLiveCatalog(new FlibustaApi.Callback<FlibustaApi.CatalogFeed>() {
            @Override
            public void onSuccess(FlibustaApi.CatalogFeed feed) {
                pbCatalogLoading.setVisibility(View.GONE);
                scrollCatalogContent.setVisibility(View.VISIBLE);

                // 1. Featured book
                if (feed.featuredBook != null) {
                    currentFeaturedBook = feed.featuredBook;
                    cardFeatured.setVisibility(View.VISIBLE);
                    tvFeaturedTitle.setText(feed.featuredBook.getTitle());
                    tvFeaturedAuthor.setText(feed.featuredBook.getAuthor() + " • " + feed.featuredBook.getGenre());
                    String desc = feed.featuredBook.getDescription();
                    if (desc != null && !desc.isEmpty()) {
                        tvFeaturedDesc.setVisibility(View.VISIBLE);
                        tvFeaturedDesc.setText(desc);
                    } else {
                        tvFeaturedDesc.setVisibility(View.GONE);
                    }
                } else {
                    cardFeatured.setVisibility(View.GONE);
                }

                // 2. Series carousel
                if (feed.popularSeries != null && !feed.popularSeries.isEmpty()) {
                    tvHeaderSeries.setVisibility(View.VISIBLE);
                    rvPopularSeries.setVisibility(View.VISIBLE);
                    popularSeriesAdapter.updateList(feed.popularSeries);
                } else {
                    tvHeaderSeries.setVisibility(View.GONE);
                    rvPopularSeries.setVisibility(View.GONE);
                }

                // 3. Recommended books
                if (feed.recommendedBooks != null) {
                    recommendedBooksAdapter.updateList(feed.recommendedBooks);
                }
            }

            @Override
            public void onError(Exception e) {
                pbCatalogLoading.setVisibility(View.GONE);
                scrollCatalogContent.setVisibility(View.GONE);
                layoutCatalogError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupSearchTab() {
        etSearchQuery = findViewById(R.id.et_search_query);
        btnSearchGo = findViewById(R.id.btn_search_go);
        chipSearchBooks = findViewById(R.id.chip_search_books);
        chipSearchSeries = findViewById(R.id.chip_search_series);
        pbSearchLoading = findViewById(R.id.pb_search_loading);
        layoutSearchEmpty = findViewById(R.id.layout_search_empty);
        rvSearchResults = findViewById(R.id.rv_search_results);

        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        searchBooksAdapter = new BookAdapter(this, null);
        searchSeriesAdapter = new SeriesAdapter(this, null);
        searchSeriesAdapter.setListener(this::showSeriesDetailsDialog);

        btnSearchGo.setOnClickListener(v -> performSearch());
        etSearchQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        chipSearchBooks.setOnClickListener(v -> {
            isSearchBooksMode = true;
            chipSearchBooks.setBackgroundResource(R.drawable.bg_chip_selected);
            chipSearchBooks.setTextColor(getResources().getColor(R.color.text_primary));
            chipSearchSeries.setBackgroundResource(R.drawable.bg_chip);
            chipSearchSeries.setTextColor(getResources().getColor(R.color.text_secondary));
            rvSearchResults.setAdapter(searchBooksAdapter);
            performSearch();
        });

        chipSearchSeries.setOnClickListener(v -> {
            isSearchBooksMode = false;
            chipSearchSeries.setBackgroundResource(R.drawable.bg_chip_selected);
            chipSearchSeries.setTextColor(getResources().getColor(R.color.text_primary));
            chipSearchBooks.setBackgroundResource(R.drawable.bg_chip);
            chipSearchBooks.setTextColor(getResources().getColor(R.color.text_secondary));
            rvSearchResults.setAdapter(searchSeriesAdapter);
            performSearch();
        });
    }

    private void performSearch() {
        String query = etSearchQuery.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Введите поисковый запрос", Toast.LENGTH_SHORT).show();
            return;
        }

        pbSearchLoading.setVisibility(View.VISIBLE);
        layoutSearchEmpty.setVisibility(View.GONE);
        rvSearchResults.setVisibility(View.GONE);

        if (isSearchBooksMode) {
            rvSearchResults.setAdapter(searchBooksAdapter);
            FlibustaApi.searchBooks(query, new FlibustaApi.Callback<List<Book>>() {
                @Override
                public void onSuccess(List<Book> result) {
                    pbSearchLoading.setVisibility(View.GONE);
                    if (result != null && !result.isEmpty()) {
                        searchBooksAdapter.updateList(result);
                        rvSearchResults.setVisibility(View.VISIBLE);
                    } else {
                        layoutSearchEmpty.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onError(Exception e) {
                    pbSearchLoading.setVisibility(View.GONE);
                    layoutSearchEmpty.setVisibility(View.VISIBLE);
                    Toast.makeText(MainActivity.this, "Поиск не удался: проверьте сеть / зеркало", Toast.LENGTH_LONG).show();
                }
            });
        } else {
            rvSearchResults.setAdapter(searchSeriesAdapter);
            FlibustaApi.searchSeries(query, new FlibustaApi.Callback<List<Series>>() {
                @Override
                public void onSuccess(List<Series> result) {
                    pbSearchLoading.setVisibility(View.GONE);
                    if (result != null && !result.isEmpty()) {
                        searchSeriesAdapter.updateList(result);
                        rvSearchResults.setVisibility(View.VISIBLE);
                    } else {
                        layoutSearchEmpty.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onError(Exception e) {
                    pbSearchLoading.setVisibility(View.GONE);
                    layoutSearchEmpty.setVisibility(View.VISIBLE);
                    Toast.makeText(MainActivity.this, "Поиск не удался: проверьте сеть / зеркало", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void setupLibraryTab() {
        chipLibAll = findViewById(R.id.chip_lib_all);
        chipLibReading = findViewById(R.id.chip_lib_reading);
        chipLibDone = findViewById(R.id.chip_lib_done);
        chipLibPlanned = findViewById(R.id.chip_lib_planned);
        layoutLibraryEmpty = findViewById(R.id.layout_library_empty);
        rvLibraryBooks = findViewById(R.id.rv_library_books);

        rvLibraryBooks.setLayoutManager(new LinearLayoutManager(this));
        libraryAdapter = new LibraryAdapter(this, null, this::refreshLibrary);
        rvLibraryBooks.setAdapter(libraryAdapter);

        chipLibAll.setOnClickListener(v -> setLibraryFilter("Все", chipLibAll));
        chipLibReading.setOnClickListener(v -> setLibraryFilter("Читаю", chipLibReading));
        chipLibDone.setOnClickListener(v -> setLibraryFilter("Прочитано", chipLibDone));
        chipLibPlanned.setOnClickListener(v -> setLibraryFilter("В планах", chipLibPlanned));

        refreshLibrary();
    }

    private void setLibraryFilter(String filter, TextView activeChip) {
        currentLibFilter = filter;
        TextView[] chips = {chipLibAll, chipLibReading, chipLibDone, chipLibPlanned};
        for (TextView c : chips) {
            if (c == activeChip) {
                c.setBackgroundResource(R.drawable.bg_chip_selected);
                c.setTextColor(getResources().getColor(R.color.text_primary));
            } else {
                c.setBackgroundResource(R.drawable.bg_chip);
                c.setTextColor(getResources().getColor(R.color.text_secondary));
            }
        }
        refreshLibrary();
    }

    private void refreshLibrary() {
        List<Book> books = db.getBooks(currentLibFilter);
        if (books.isEmpty()) {
            layoutLibraryEmpty.setVisibility(View.VISIBLE);
            rvLibraryBooks.setVisibility(View.GONE);
        } else {
            layoutLibraryEmpty.setVisibility(View.GONE);
            rvLibraryBooks.setVisibility(View.VISIBLE);
            libraryAdapter.updateList(books);
        }
    }

    private void setupNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_catalog) {
                viewCatalog.setVisibility(View.VISIBLE);
                viewSearch.setVisibility(View.GONE);
                viewLibrary.setVisibility(View.GONE);
                return true;
            } else if (id == R.id.nav_search) {
                viewCatalog.setVisibility(View.GONE);
                viewSearch.setVisibility(View.VISIBLE);
                viewLibrary.setVisibility(View.GONE);
                return true;
            } else if (id == R.id.nav_library) {
                viewCatalog.setVisibility(View.GONE);
                viewSearch.setVisibility(View.GONE);
                viewLibrary.setVisibility(View.VISIBLE);
                refreshLibrary();
                return true;
            }
            return false;
        });
    }

    private void showSeriesDetailsDialog(Series series) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_series_books);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        }

        TextView tvTitle = dialog.findViewById(R.id.tv_dialog_series_title);
        TextView tvAuthor = dialog.findViewById(R.id.tv_dialog_series_author);
        TextView btnClose = dialog.findViewById(R.id.btn_dialog_close);
        RecyclerView rvBooks = dialog.findViewById(R.id.rv_dialog_series_books);

        tvTitle.setText(series.getTitle());
        tvAuthor.setText(series.getAuthor() + " • " + (series.getBookCount() > 0 ? series.getBookCount() + " книг" : "Серия"));
        btnClose.setOnClickListener(v -> dialog.dismiss());

        rvBooks.setLayoutManager(new LinearLayoutManager(this));
        BookAdapter adapter = new BookAdapter(this, series.getBooks());
        adapter.setListener(new BookAdapter.OnBookActionListener() {
            @Override
            public void onDownload(Book book) {
                BookDownloader.downloadBook(MainActivity.this, book, "fb2");
                refreshLibrary();
            }

            @Override
            public void onAddToLibrary(Book book) {
                db.addBook(book);
                adapter.notifyDataSetChanged();
                refreshLibrary();
                Toast.makeText(MainActivity.this, "Добавлено на полку: " + book.getTitle(), Toast.LENGTH_SHORT).show();
            }
        });
        rvBooks.setAdapter(adapter);

        if (series.getBooks() == null || series.getBooks().isEmpty()) {
            Toast.makeText(this, "Загрузка книг цикла с Флибусты...", Toast.LENGTH_SHORT).show();
            FlibustaApi.loadSeriesDetails(series, new FlibustaApi.Callback<Series>() {
                @Override
                public void onSuccess(Series result) {
                    adapter.updateList(result.getBooks());
                    tvAuthor.setText(result.getAuthor() + " • " + result.getBookCount() + " книг");
                }

                @Override
                public void onError(Exception e) {
                    Toast.makeText(MainActivity.this, "Не удалось загрузить книги цикла", Toast.LENGTH_SHORT).show();
                }
            });
        }

        dialog.show();
    }

    private void showMirrorDialog() {
        String[] mirrors = new String[]{"http://flibusta.is", "http://flibusta.club", "http://flibusta.site"};
        new AlertDialog.Builder(this)
                .setTitle("Выбор зеркала Флибусты")
                .setSingleChoiceItems(mirrors, 0, (dialog, which) -> {
                    String selected = mirrors[which];
                    FlibustaApi.setMirror(selected);
                    btnMirrorStatus.setText(selected.replace("http://", "") + " ●");
                    Toast.makeText(this, "Зеркало изменено: " + selected, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadLiveCatalog();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}
