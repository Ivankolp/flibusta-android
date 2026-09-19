package is.flibusta.client;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import is.flibusta.client.util.BookSorter;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import is.flibusta.client.adapter.BookAdapter;
import is.flibusta.client.adapter.GenreAdapter;
import is.flibusta.client.adapter.LibraryAdapter;
import is.flibusta.client.adapter.SeriesAdapter;
import is.flibusta.client.data.Book;
import is.flibusta.client.data.BookPage;
import is.flibusta.client.data.DatabaseHelper;
import is.flibusta.client.data.GenreItem;
import is.flibusta.client.data.Series;
import is.flibusta.client.data.SeriesPage;
import is.flibusta.client.network.BookDownloader;
import is.flibusta.client.network.FlibustaApi;
import is.flibusta.client.network.ImageLoader;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Views
    private View viewCatalog;
    private View viewGenres;
    private View viewSearch;
    private View viewLibrary;
    private BottomNavigationView bottomNav;

    // Database
    private DatabaseHelper db;

    // Catalog Tab
    private ProgressBar pbCatalogLoading;
    private LinearLayout layoutCatalogError;
    private TextView tvCatalogErrorTitle;
    private TextView tvCatalogErrorDesc;
    private TextView btnCatalogOpenLibrary;
    private TextView btnCatalogRetry;
    private ScrollView scrollCatalogContent;
    private View cardFeatured;
    private ImageView ivFeaturedCover;
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
    private final List<Book> allLoadedBooks = new ArrayList<>();

    // Quick Actions & Sorting
    private TextView btnCatalogRandom;
    private TextView btnCatalogNewArrivals;
    private TextView btnCatalogSort;
    private TextView btnSearchSort;
    private TextView btnLibSort;
    private is.flibusta.client.util.BookSorter.SortMode catalogSortMode = is.flibusta.client.util.BookSorter.SortMode.DATE_DESC;
    private is.flibusta.client.util.BookSorter.SortMode searchSortMode = is.flibusta.client.util.BookSorter.SortMode.DATE_DESC;
    private is.flibusta.client.util.BookSorter.SortMode libSortMode = is.flibusta.client.util.BookSorter.SortMode.DATE_DESC;

    // Genre Sorter Chips in Catalog
    private TextView chipGenreAll, chipGenreFantasy, chipGenreScifi, chipGenrePopadantsy;
    private TextView chipGenreDetective, chipGenreLitrpg, chipGenreAdventure, chipGenreAction;
    private String currentSelectedGenre = "Все";

    // Genres Tab
    private ImageButton btnGenresBack;
    private TextView tvGenresHeaderTitle;
    private ProgressBar pbGenresLoading;
    private LinearLayout layoutGenresError;
    private TextView btnGenresRetry;
    private RecyclerView rvGenresList;
    private GenreAdapter genreAdapter;
    private final List<GenreItem> genresList = new ArrayList<>();
    private String currentGenreUrl = null;
    private boolean isInsideCategory = false;

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

    // Search Pagination
    private LinearLayout layoutSearchPagination;
    private TextView tvSearchPageInfo;
    private ProgressBar pbSearchLoadMore;
    private TextView btnSearchLoadMore;
    private String searchBooksNextPageUrl = null;
    private String searchSeriesNextPageUrl = null;
    private boolean isSearchLoadingMore = false;
    private String lastSearchQuery = "";

    // Library Tab
    private TextView chipLibAll;
    private TextView chipLibSeries;
    private TextView chipLibDownloaded;
    private TextView chipLibReading;
    private TextView chipLibDone;
    private TextView chipLibPlanned;
    private LinearLayout layoutLibraryEmpty;
    private RecyclerView rvLibraryBooks;
    private LibraryAdapter libraryAdapter;
    private SeriesAdapter librarySeriesAdapter;
    private String currentLibFilter = "Все";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new DatabaseHelper(this);

        initViews();
        setupCatalogTab();
        setupGenresTab();
        setupSearchTab();
        setupLibraryTab();
        setupNavigation();

        // Load live catalog from Flibusta
        loadLiveCatalog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLibrary();
        if (recommendedBooksAdapter != null) {
            recommendedBooksAdapter.notifyDataSetChanged();
        }
        if (searchBooksAdapter != null) {
            searchBooksAdapter.notifyDataSetChanged();
        }
    }

    private void initViews() {
        viewCatalog = findViewById(R.id.view_catalog);
        viewGenres = findViewById(R.id.view_genres);
        viewSearch = findViewById(R.id.view_search);
        viewLibrary = findViewById(R.id.view_library);
        bottomNav = findViewById(R.id.bottom_navigation);
    }

    private void setupCatalogTab() {
        pbCatalogLoading = findViewById(R.id.pb_catalog_loading);
        layoutCatalogError = findViewById(R.id.layout_catalog_error);
        tvCatalogErrorTitle = findViewById(R.id.tv_catalog_error_title);
        tvCatalogErrorDesc = findViewById(R.id.tv_catalog_error_desc);
        btnCatalogOpenLibrary = findViewById(R.id.btn_catalog_open_library);
        btnCatalogRetry = findViewById(R.id.btn_catalog_retry);
        scrollCatalogContent = findViewById(R.id.scroll_catalog_content);

        if (btnCatalogOpenLibrary != null) {
            btnCatalogOpenLibrary.setOnClickListener(v -> bottomNav.setSelectedItemId(R.id.nav_library));
        }

        cardFeatured = findViewById(R.id.card_featured);
        ivFeaturedCover = findViewById(R.id.iv_featured_cover);
        tvFeaturedTitle = findViewById(R.id.tv_featured_title);
        tvFeaturedAuthor = findViewById(R.id.tv_featured_author);
        tvFeaturedDesc = findViewById(R.id.tv_featured_desc);
        btnFeaturedToLibrary = findViewById(R.id.btn_featured_to_library);
        btnFeaturedDownload = findViewById(R.id.btn_featured_download);

        tvHeaderSeries = findViewById(R.id.tv_header_series);
        rvPopularSeries = findViewById(R.id.rv_popular_series);
        rvPopularSeries.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        popularSeriesAdapter = new SeriesAdapter(this, new ArrayList<>());
        popularSeriesAdapter.setListener(this::openSeriesActivity);
        rvPopularSeries.setAdapter(popularSeriesAdapter);

        rvRecommendedBooks = findViewById(R.id.rv_recommended_books);
        rvRecommendedBooks.setLayoutManager(new LinearLayoutManager(this));
        recommendedBooksAdapter = new BookAdapter(this, new ArrayList<>());
        recommendedBooksAdapter.setListener(new BookAdapter.OnBookActionListener() {
            @Override
            public void onBookClick(Book book) {
                openBookDetailActivity(book);
            }

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

        // Featured book actions
        cardFeatured.setOnClickListener(v -> {
            if (currentFeaturedBook != null) {
                openBookDetailActivity(currentFeaturedBook);
            }
        });

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

        btnCatalogRetry.setOnClickListener(v -> loadLiveCatalog());

        btnCatalogRandom = findViewById(R.id.btn_catalog_random);
        btnCatalogNewArrivals = findViewById(R.id.btn_catalog_new_arrivals);
        btnCatalogSort = findViewById(R.id.btn_catalog_sort);

        btnCatalogRandom.setOnClickListener(v -> {
            Toast.makeText(this, "Подбираем случайную книгу...", Toast.LENGTH_SHORT).show();
            FlibustaApi.fetchRandomBook(new FlibustaApi.Callback<Book>() {
                @Override
                public void onSuccess(Book book) {
                    openBookDetailActivity(book);
                }

                @Override
                public void onError(Exception e) {
                    Toast.makeText(MainActivity.this, "Не удалось открыть случайную книгу", Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnCatalogNewArrivals.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BooksListActivity.class);
            intent.putExtra("type", "genre");
            intent.putExtra("genre_url", "http://flibusta.is/opds/new/0/new");
            intent.putExtra("title", "Все новинки недели");
            startActivity(intent);
        });

        btnCatalogSort.setOnClickListener(v -> showCatalogSortDialog());

        // Setup Genre Chips
        setupGenreChips();
    }

    private void setupGenresTab() {
        btnGenresBack = findViewById(R.id.btn_genres_back);
        tvGenresHeaderTitle = findViewById(R.id.tv_genres_header_title);
        pbGenresLoading = findViewById(R.id.pb_genres_loading);
        layoutGenresError = findViewById(R.id.layout_genres_error);
        btnGenresRetry = findViewById(R.id.btn_genres_retry);
        rvGenresList = findViewById(R.id.rv_genres_list);

        rvGenresList.setLayoutManager(new LinearLayoutManager(this));
        genreAdapter = new GenreAdapter(this, new ArrayList<>());
        genreAdapter.setListener(genre -> {
            if (genre.isLeaf()) {
                // Leaf genre: open BooksListActivity
                Intent intent = new Intent(MainActivity.this, BooksListActivity.class);
                intent.putExtra("type", "genre");
                intent.putExtra("genre_url", genre.getUrl());
                intent.putExtra("title", "Жанр: " + genre.getTitle());
                startActivity(intent);
            } else {
                // Category with subgenres: load subgenres
                loadCategorySubgenres(genre.getUrl(), genre.getTitle());
            }
        });
        rvGenresList.setAdapter(genreAdapter);

        btnGenresBack.setOnClickListener(v -> loadRootGenres());
        btnGenresRetry.setOnClickListener(v -> {
            if (isInsideCategory && currentGenreUrl != null) {
                loadCategorySubgenres(currentGenreUrl, tvGenresHeaderTitle.getText().toString());
            } else {
                loadRootGenres();
            }
        });
    }

    private void loadRootGenres() {
        isInsideCategory = false;
        currentGenreUrl = null;
        btnGenresBack.setVisibility(View.GONE);
        tvGenresHeaderTitle.setText("Жанры литературы");

        pbGenresLoading.setVisibility(View.VISIBLE);
        layoutGenresError.setVisibility(View.GONE);
        rvGenresList.setVisibility(View.GONE);

        FlibustaApi.fetchGenres(null, new FlibustaApi.Callback<List<GenreItem>>() {
            @Override
            public void onSuccess(List<GenreItem> result) {
                pbGenresLoading.setVisibility(View.GONE);
                if (result != null && !result.isEmpty()) {
                    genresList.clear();
                    genresList.addAll(result);
                    genreAdapter.updateList(result);
                    rvGenresList.setVisibility(View.VISIBLE);
                } else {
                    layoutGenresError.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(Exception e) {
                pbGenresLoading.setVisibility(View.GONE);
                layoutGenresError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void loadCategorySubgenres(String url, String categoryTitle) {
        isInsideCategory = true;
        currentGenreUrl = url;
        btnGenresBack.setVisibility(View.VISIBLE);
        tvGenresHeaderTitle.setText(categoryTitle);

        pbGenresLoading.setVisibility(View.VISIBLE);
        layoutGenresError.setVisibility(View.GONE);
        rvGenresList.setVisibility(View.GONE);

        FlibustaApi.fetchGenres(url, new FlibustaApi.Callback<List<GenreItem>>() {
            @Override
            public void onSuccess(List<GenreItem> result) {
                pbGenresLoading.setVisibility(View.GONE);
                if (result != null && !result.isEmpty()) {
                    genreAdapter.updateList(result);
                    rvGenresList.setVisibility(View.VISIBLE);
                } else {
                    layoutGenresError.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(Exception e) {
                pbGenresLoading.setVisibility(View.GONE);
                layoutGenresError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupGenreChips() {
        chipGenreAll = findViewById(R.id.chip_genre_all);
        chipGenreFantasy = findViewById(R.id.chip_genre_fantasy);
        chipGenreScifi = findViewById(R.id.chip_genre_scifi);
        chipGenrePopadantsy = findViewById(R.id.chip_genre_popadantsy);
        chipGenreDetective = findViewById(R.id.chip_genre_detective);
        chipGenreLitrpg = findViewById(R.id.chip_genre_litrpg);
        chipGenreAdventure = findViewById(R.id.chip_genre_adventure);
        chipGenreAction = findViewById(R.id.chip_genre_action);

        chipGenreAll.setOnClickListener(v -> selectGenre("Все", chipGenreAll));
        chipGenreFantasy.setOnClickListener(v -> selectGenre("фэнтези", chipGenreFantasy));
        chipGenreScifi.setOnClickListener(v -> selectGenre("фантастик", chipGenreScifi));
        chipGenrePopadantsy.setOnClickListener(v -> selectGenre("попадан", chipGenrePopadantsy));
        chipGenreDetective.setOnClickListener(v -> selectGenre("детектив", chipGenreDetective));
        chipGenreLitrpg.setOnClickListener(v -> selectGenre("литрпг", chipGenreLitrpg));
        chipGenreAdventure.setOnClickListener(v -> selectGenre("приключ", chipGenreAdventure));
        chipGenreAction.setOnClickListener(v -> selectGenre("боевик", chipGenreAction));
    }

    private void selectGenre(String genreFilter, TextView activeChip) {
        currentSelectedGenre = genreFilter;
        TextView[] chips = {chipGenreAll, chipGenreFantasy, chipGenreScifi, chipGenrePopadantsy,
                chipGenreDetective, chipGenreLitrpg, chipGenreAdventure, chipGenreAction};

        for (TextView c : chips) {
            if (c == activeChip) {
                c.setBackgroundResource(R.drawable.bg_chip_selected);
                c.setTextColor(getResources().getColor(R.color.text_primary));
            } else {
                c.setBackgroundResource(R.drawable.bg_chip);
                c.setTextColor(getResources().getColor(R.color.text_secondary));
            }
        }

        filterBooksByGenre();
    }

    private void filterBooksByGenre() {
        List<Book> list;
        if ("Все".equalsIgnoreCase(currentSelectedGenre)) {
            list = new ArrayList<>(allLoadedBooks);
        } else {
            list = new ArrayList<>();
            for (Book b : allLoadedBooks) {
                String g = (b.getGenre() != null ? b.getGenre() : "").toLowerCase();
                String t = (b.getTitle() != null ? b.getTitle() : "").toLowerCase();
                String d = (b.getDescription() != null ? b.getDescription() : "").toLowerCase();

                if (g.contains(currentSelectedGenre) || t.contains(currentSelectedGenre) || d.contains(currentSelectedGenre)) {
                    list.add(b);
                }
            }
            if (list.isEmpty()) {
                list = new ArrayList<>(allLoadedBooks);
                Toast.makeText(this, "В свежей ленте мало книг этого жанра, показаны все", Toast.LENGTH_SHORT).show();
            }
        }
        is.flibusta.client.util.BookSorter.sort(list, catalogSortMode);
        recommendedBooksAdapter.updateList(list);
    }

    private void loadLiveCatalog() {
        if (!FlibustaApi.isOnline(this)) {
            pbCatalogLoading.setVisibility(View.GONE);
            scrollCatalogContent.setVisibility(View.GONE);
            layoutCatalogError.setVisibility(View.VISIBLE);
            if (tvCatalogErrorTitle != null) tvCatalogErrorTitle.setText("Нет подключения к интернету");
            if (tvCatalogErrorDesc != null) tvCatalogErrorDesc.setText("Проверьте Wi-Fi или мобильную сеть, либо откройте скачанные книги из вашей библиотеки.");
            Toast.makeText(this, "Нет подключения к интернету. Доступна ваша библиотека", Toast.LENGTH_LONG).show();
            return;
        }

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
                    ImageLoader.loadCover(ivFeaturedCover, feed.featuredBook.getCoverUrl());

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
                allLoadedBooks.clear();
                if (feed.recommendedBooks != null) {
                    allLoadedBooks.addAll(feed.recommendedBooks);
                }
                filterBooksByGenre();
            }

            @Override
            public void onError(Exception e) {
                pbCatalogLoading.setVisibility(View.GONE);
                scrollCatalogContent.setVisibility(View.GONE);
                layoutCatalogError.setVisibility(View.VISIBLE);
                if (!FlibustaApi.isOnline(MainActivity.this)) {
                    if (tvCatalogErrorTitle != null) tvCatalogErrorTitle.setText("Нет подключения к интернету");
                    if (tvCatalogErrorDesc != null) tvCatalogErrorDesc.setText("Проверьте Wi-Fi или мобильную сеть, либо откройте скачанные книги из вашей библиотеки.");
                } else {
                    if (tvCatalogErrorTitle != null) tvCatalogErrorTitle.setText("Сервер Флибусты не отвечает");
                    if (tvCatalogErrorDesc != null) tvCatalogErrorDesc.setText("Не удалось получить каталог с сервера. Попробуйте ещё раз или почитайте книги с вашей полки.");
                }
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

        // Search Pagination views
        layoutSearchPagination = findViewById(R.id.layout_search_pagination);
        tvSearchPageInfo = findViewById(R.id.tv_search_page_info);
        pbSearchLoadMore = findViewById(R.id.pb_search_load_more);
        btnSearchLoadMore = findViewById(R.id.btn_search_load_more);
        btnSearchLoadMore.setOnClickListener(v -> loadMoreSearchResults());

        LinearLayoutManager lm = new LinearLayoutManager(this);
        rvSearchResults.setLayoutManager(lm);
        rvSearchResults.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && !isSearchLoadingMore) {
                    boolean hasMore = isSearchBooksMode ? (searchBooksNextPageUrl != null) : (searchSeriesNextPageUrl != null);
                    if (hasMore) {
                        int total = isSearchBooksMode ? searchBooksAdapter.getItemCount() : searchSeriesAdapter.getItemCount();
                        if (lm.findLastVisibleItemPosition() >= total - 3) {
                            loadMoreSearchResults();
                        }
                    }
                }
            }
        });

        searchBooksAdapter = new BookAdapter(this, null);
        searchBooksAdapter.setListener(new BookAdapter.OnBookActionListener() {
            @Override
            public void onBookClick(Book book) {
                openBookDetailActivity(book);
            }

            @Override
            public void onDownload(Book book) {
                BookDownloader.downloadBook(MainActivity.this, book, "fb2");
                refreshLibrary();
            }

            @Override
            public void onAddToLibrary(Book book) {
                db.addBook(book);
                searchBooksAdapter.notifyDataSetChanged();
                refreshLibrary();
                Toast.makeText(MainActivity.this, "Добавлено на полку: " + book.getTitle(), Toast.LENGTH_SHORT).show();
            }
        });

        searchSeriesAdapter = new SeriesAdapter(this, null);
        searchSeriesAdapter.setListener(this::openSeriesActivity);

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

        btnSearchSort = findViewById(R.id.btn_search_sort);
        btnSearchSort.setOnClickListener(v -> showSearchSortDialog());
    }

    private void performSearch() {
        String query = etSearchQuery.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Введите поисковый запрос", Toast.LENGTH_SHORT).show();
            return;
        }

        lastSearchQuery = query;
        searchBooksNextPageUrl = null;
        searchSeriesNextPageUrl = null;
        isSearchLoadingMore = false;
        if (layoutSearchPagination != null) layoutSearchPagination.setVisibility(View.GONE);

        pbSearchLoading.setVisibility(View.VISIBLE);
        layoutSearchEmpty.setVisibility(View.GONE);
        rvSearchResults.setVisibility(View.GONE);

        if (isSearchBooksMode) {
            rvSearchResults.setAdapter(searchBooksAdapter);
            FlibustaApi.searchBooksPage(query, null, new FlibustaApi.Callback<BookPage>() {
                @Override
                public void onSuccess(BookPage result) {
                    pbSearchLoading.setVisibility(View.GONE);
                    List<Book> books = result != null ? result.getBooks() : null;
                    if (books != null && !books.isEmpty()) {
                        is.flibusta.client.util.BookSorter.sort(books, searchSortMode);
                        searchBooksNextPageUrl = result.getNextPageUrl();
                        searchBooksAdapter.updateList(books);
                        rvSearchResults.setVisibility(View.VISIBLE);
                        if (searchBooksNextPageUrl != null) {
                            layoutSearchPagination.setVisibility(View.VISIBLE);
                            tvSearchPageInfo.setText("Книг: " + searchBooksAdapter.getItemCount());
                            btnSearchLoadMore.setText("Загрузить ещё 20 книг");
                            btnSearchLoadMore.setVisibility(View.VISIBLE);
                        } else {
                            layoutSearchPagination.setVisibility(View.GONE);
                        }
                    } else {
                        layoutSearchEmpty.setVisibility(View.VISIBLE);
                        if (layoutSearchPagination != null) layoutSearchPagination.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onError(Exception e) {
                    pbSearchLoading.setVisibility(View.GONE);
                    layoutSearchEmpty.setVisibility(View.VISIBLE);
                    if (layoutSearchPagination != null) layoutSearchPagination.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Поиск не удался: проверьте сеть", Toast.LENGTH_LONG).show();
                }
            });
        } else {
            rvSearchResults.setAdapter(searchSeriesAdapter);
            FlibustaApi.searchSeriesPage(query, null, new FlibustaApi.Callback<SeriesPage>() {
                @Override
                public void onSuccess(SeriesPage result) {
                    pbSearchLoading.setVisibility(View.GONE);
                    List<Series> list = result != null ? result.getSeriesList() : null;
                    if (list != null && !list.isEmpty()) {
                        searchSeriesNextPageUrl = result.getNextPageUrl();
                        searchSeriesAdapter.updateList(list);
                        rvSearchResults.setVisibility(View.VISIBLE);
                        if (searchSeriesNextPageUrl != null) {
                            layoutSearchPagination.setVisibility(View.VISIBLE);
                            tvSearchPageInfo.setText("Серий: " + searchSeriesAdapter.getItemCount());
                            btnSearchLoadMore.setText("Загрузить ещё серии");
                            btnSearchLoadMore.setVisibility(View.VISIBLE);
                        } else {
                            layoutSearchPagination.setVisibility(View.GONE);
                        }
                    } else {
                        layoutSearchEmpty.setVisibility(View.VISIBLE);
                        if (layoutSearchPagination != null) layoutSearchPagination.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onError(Exception e) {
                    pbSearchLoading.setVisibility(View.GONE);
                    layoutSearchEmpty.setVisibility(View.VISIBLE);
                    if (layoutSearchPagination != null) layoutSearchPagination.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Поиск не удался: проверьте сеть", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void loadMoreSearchResults() {
        if (isSearchLoadingMore || lastSearchQuery == null || lastSearchQuery.isEmpty()) return;

        if (isSearchBooksMode) {
            if (searchBooksNextPageUrl == null) return;

            isSearchLoadingMore = true;
            pbSearchLoadMore.setVisibility(View.VISIBLE);
            btnSearchLoadMore.setEnabled(false);

            FlibustaApi.fetchBooksPage(searchBooksNextPageUrl, new FlibustaApi.Callback<BookPage>() {
                @Override
                public void onSuccess(BookPage morePage) {
                    isSearchLoadingMore = false;
                    pbSearchLoadMore.setVisibility(View.GONE);
                    btnSearchLoadMore.setEnabled(true);

                    List<Book> moreBooks = morePage != null ? morePage.getBooks() : null;
                    if (moreBooks == null || moreBooks.isEmpty()) {
                        searchBooksNextPageUrl = null;
                        btnSearchLoadMore.setVisibility(View.GONE);
                        tvSearchPageInfo.setText("Все книги загружены • Всего: " + searchBooksAdapter.getItemCount());
                        Toast.makeText(MainActivity.this, "Все доступные книги загружены", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    searchBooksNextPageUrl = morePage.getNextPageUrl();
                    searchBooksAdapter.addBooks(moreBooks);
                    tvSearchPageInfo.setText("Книг: " + searchBooksAdapter.getItemCount());

                    if (searchBooksNextPageUrl == null) {
                        btnSearchLoadMore.setVisibility(View.GONE);
                        tvSearchPageInfo.setText("Все книги загружены • Всего: " + searchBooksAdapter.getItemCount());
                    }

                    Toast.makeText(MainActivity.this, "Загружено ещё " + moreBooks.size() + " книг", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(Exception e) {
                    isSearchLoadingMore = false;
                    pbSearchLoadMore.setVisibility(View.GONE);
                    btnSearchLoadMore.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Не удалось загрузить следующую страницу", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            if (searchSeriesNextPageUrl == null) return;

            isSearchLoadingMore = true;
            pbSearchLoadMore.setVisibility(View.VISIBLE);
            btnSearchLoadMore.setEnabled(false);

            FlibustaApi.searchSeriesPage(lastSearchQuery, searchSeriesNextPageUrl, new FlibustaApi.Callback<SeriesPage>() {
                @Override
                public void onSuccess(SeriesPage morePage) {
                    isSearchLoadingMore = false;
                    pbSearchLoadMore.setVisibility(View.GONE);
                    btnSearchLoadMore.setEnabled(true);

                    List<Series> moreSeries = morePage != null ? morePage.getSeriesList() : null;
                    if (moreSeries == null || moreSeries.isEmpty()) {
                        searchSeriesNextPageUrl = null;
                        btnSearchLoadMore.setVisibility(View.GONE);
                        tvSearchPageInfo.setText("Все серии загружены • Всего: " + searchSeriesAdapter.getItemCount());
                        Toast.makeText(MainActivity.this, "Все доступные серии загружены", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    searchSeriesNextPageUrl = morePage.getNextPageUrl();
                    searchSeriesAdapter.addSeries(moreSeries);
                    tvSearchPageInfo.setText("Серий: " + searchSeriesAdapter.getItemCount());

                    if (searchSeriesNextPageUrl == null) {
                        btnSearchLoadMore.setVisibility(View.GONE);
                        tvSearchPageInfo.setText("Все серии загружены • Всего: " + searchSeriesAdapter.getItemCount());
                    }

                    Toast.makeText(MainActivity.this, "Загружено ещё " + moreSeries.size() + " серий", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(Exception e) {
                    isSearchLoadingMore = false;
                    pbSearchLoadMore.setVisibility(View.GONE);
                    btnSearchLoadMore.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Не удалось загрузить следующую страницу", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void setupLibraryTab() {
        chipLibAll = findViewById(R.id.chip_lib_all);
        chipLibSeries = findViewById(R.id.chip_lib_series);
        chipLibDownloaded = findViewById(R.id.chip_lib_downloaded);
        chipLibReading = findViewById(R.id.chip_lib_reading);
        chipLibDone = findViewById(R.id.chip_lib_done);
        chipLibPlanned = findViewById(R.id.chip_lib_planned);
        layoutLibraryEmpty = findViewById(R.id.layout_library_empty);
        rvLibraryBooks = findViewById(R.id.rv_library_books);

        rvLibraryBooks.setLayoutManager(new LinearLayoutManager(this));
        libraryAdapter = new LibraryAdapter(this, null, this::refreshLibrary);
        libraryAdapter.setClickListener(this::openBookDetailActivity);

        librarySeriesAdapter = new SeriesAdapter(this, null);
        librarySeriesAdapter.setListener(series -> {
            if (series == null) return;
            Intent intent = new Intent(this, BooksListActivity.class);
            intent.putExtra("type", "library_series");
            intent.putExtra("series_name", series.getTitle());
            intent.putExtra("author", series.getAuthor());
            intent.putExtra("title", "Серия: " + series.getTitle());
            startActivity(intent);
        });

        rvLibraryBooks.setAdapter(libraryAdapter);

        chipLibAll.setOnClickListener(v -> setLibraryFilter("Все", chipLibAll));
        if (chipLibSeries != null) {
            chipLibSeries.setOnClickListener(v -> setLibraryFilter("По сериям", chipLibSeries));
        }
        if (chipLibDownloaded != null) {
            chipLibDownloaded.setOnClickListener(v -> setLibraryFilter("Скачано", chipLibDownloaded));
        }
        chipLibReading.setOnClickListener(v -> setLibraryFilter("Читаю", chipLibReading));
        chipLibDone.setOnClickListener(v -> setLibraryFilter("Прочитано", chipLibDone));
        chipLibPlanned.setOnClickListener(v -> setLibraryFilter("В планах", chipLibPlanned));

        btnLibSort = findViewById(R.id.btn_lib_sort);
        if (btnLibSort != null) {
            btnLibSort.setOnClickListener(v -> showLibSortDialog());
        }

        refreshLibrary();
    }

    private void setLibraryFilter(String filter, TextView activeChip) {
        currentLibFilter = filter;
        TextView[] chips = {chipLibAll, chipLibSeries, chipLibDownloaded, chipLibReading, chipLibDone, chipLibPlanned};
        for (TextView c : chips) {
            if (c == null) continue;
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
        if ("По сериям".equals(currentLibFilter)) {
            if (btnLibSort != null) btnLibSort.setVisibility(View.GONE);
            rvLibraryBooks.setAdapter(librarySeriesAdapter);
            List<Series> seriesList = db.getDownloadedSeries();
            if (seriesList == null || seriesList.isEmpty()) {
                layoutLibraryEmpty.setVisibility(View.VISIBLE);
                rvLibraryBooks.setVisibility(View.GONE);
            } else {
                layoutLibraryEmpty.setVisibility(View.GONE);
                rvLibraryBooks.setVisibility(View.VISIBLE);
                librarySeriesAdapter.updateList(seriesList);
            }
            return;
        }

        if (btnLibSort != null) btnLibSort.setVisibility(View.VISIBLE);
        rvLibraryBooks.setAdapter(libraryAdapter);

        List<Book> books;
        if ("Скачано".equals(currentLibFilter)) {
            books = db.getDownloadedBooks();
        } else {
            books = db.getBooks(currentLibFilter);
        }

        if (books == null || books.isEmpty()) {
            layoutLibraryEmpty.setVisibility(View.VISIBLE);
            rvLibraryBooks.setVisibility(View.GONE);
        } else {
            layoutLibraryEmpty.setVisibility(View.GONE);
            rvLibraryBooks.setVisibility(View.VISIBLE);
            BookSorter.sort(books, libSortMode);
            libraryAdapter.updateList(books);
        }
    }

    private void showCatalogSortDialog() {
        BookSorter.SortMode[] modes = BookSorter.SortMode.values();
        String[] titles = new String[modes.length];
        int selectedIndex = 0;
        for (int i = 0; i < modes.length; i++) {
            titles[i] = modes[i].getTitle();
            if (modes[i] == catalogSortMode) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Сортировка каталога")
                .setSingleChoiceItems(titles, selectedIndex, (dialog, which) -> {
                    catalogSortMode = modes[which];
                    if (btnCatalogSort != null) {
                        btnCatalogSort.setText(catalogSortMode.getTitle());
                        btnCatalogSort.setContentDescription("Сортировка рекомендаций: " + catalogSortMode.getTitle());
                    }
                    filterBooksByGenre();
                    dialog.dismiss();
                    Toast.makeText(MainActivity.this, "Сортировка: " + catalogSortMode.getTitle(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showSearchSortDialog() {
        BookSorter.SortMode[] modes = BookSorter.SortMode.values();
        String[] titles = new String[modes.length];
        int selectedIndex = 0;
        for (int i = 0; i < modes.length; i++) {
            titles[i] = modes[i].getTitle();
            if (modes[i] == searchSortMode) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Сортировка результатов поиска")
                .setSingleChoiceItems(titles, selectedIndex, (dialog, which) -> {
                    searchSortMode = modes[which];
                    if (btnSearchSort != null) {
                        btnSearchSort.setText(searchSortMode.getTitle());
                        btnSearchSort.setContentDescription("Сортировка поиска: " + searchSortMode.getTitle());
                    }
                    if (isSearchBooksMode && searchBooksAdapter != null) {
                        searchBooksAdapter.sort(searchSortMode);
                    }
                    dialog.dismiss();
                    Toast.makeText(MainActivity.this, "Сортировка: " + searchSortMode.getTitle(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showLibSortDialog() {
        BookSorter.SortMode[] modes = BookSorter.SortMode.values();
        String[] titles = new String[modes.length];
        int selectedIndex = 0;
        for (int i = 0; i < modes.length; i++) {
            titles[i] = modes[i].getTitle();
            if (modes[i] == libSortMode) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Сортировка книг на полке")
                .setSingleChoiceItems(titles, selectedIndex, (dialog, which) -> {
                    libSortMode = modes[which];
                    if (btnLibSort != null) {
                        btnLibSort.setText(libSortMode.getTitle());
                        btnLibSort.setContentDescription("Сортировка полки: " + libSortMode.getTitle());
                    }
                    refreshLibrary();
                    dialog.dismiss();
                    Toast.makeText(MainActivity.this, "Сортировка: " + libSortMode.getTitle(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void setupNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_catalog) {
                viewCatalog.setVisibility(View.VISIBLE);
                viewGenres.setVisibility(View.GONE);
                viewSearch.setVisibility(View.GONE);
                viewLibrary.setVisibility(View.GONE);
                return true;
            } else if (id == R.id.nav_genres) {
                viewCatalog.setVisibility(View.GONE);
                viewGenres.setVisibility(View.VISIBLE);
                viewSearch.setVisibility(View.GONE);
                viewLibrary.setVisibility(View.GONE);
                if (genresList.isEmpty()) {
                    loadRootGenres();
                }
                return true;
            } else if (id == R.id.nav_search) {
                viewCatalog.setVisibility(View.GONE);
                viewGenres.setVisibility(View.GONE);
                viewSearch.setVisibility(View.VISIBLE);
                viewLibrary.setVisibility(View.GONE);
                return true;
            } else if (id == R.id.nav_library) {
                viewCatalog.setVisibility(View.GONE);
                viewGenres.setVisibility(View.GONE);
                viewSearch.setVisibility(View.GONE);
                viewLibrary.setVisibility(View.VISIBLE);
                refreshLibrary();
                return true;
            }
            return false;
        });
    }

    // Opens separate full-screen BookDetailActivity
    public void openBookDetailActivity(Book book) {
        if (book == null) return;
        Intent intent = new Intent(this, BookDetailActivity.class);
        intent.putExtra("book", book);
        startActivity(intent);
    }

    // Opens separate full-screen BooksListActivity for a series
    private void openSeriesActivity(Series series) {
        if (series == null) return;
        Intent intent = new Intent(this, BooksListActivity.class);
        intent.putExtra("type", "series");
        intent.putExtra("series_id", series.getId());
        intent.putExtra("author", series.getAuthor());
        intent.putExtra("title", "Серия: " + series.getTitle());
        startActivity(intent);
    }
}
