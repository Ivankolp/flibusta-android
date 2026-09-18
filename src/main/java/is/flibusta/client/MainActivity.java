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

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import is.flibusta.client.adapter.BookAdapter;
import is.flibusta.client.adapter.GenreAdapter;
import is.flibusta.client.adapter.LibraryAdapter;
import is.flibusta.client.adapter.SeriesAdapter;
import is.flibusta.client.data.Book;
import is.flibusta.client.data.DatabaseHelper;
import is.flibusta.client.data.GenreItem;
import is.flibusta.client.data.Series;
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
        btnCatalogRetry = findViewById(R.id.btn_catalog_retry);
        scrollCatalogContent = findViewById(R.id.scroll_catalog_content);

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
        genreAdapter = new GenreAdapter(this, genresList);
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
                    genreAdapter.updateList(genresList);
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
        if ("Все".equalsIgnoreCase(currentSelectedGenre)) {
            recommendedBooksAdapter.updateList(allLoadedBooks);
            return;
        }

        List<Book> filtered = new ArrayList<>();
        for (Book b : allLoadedBooks) {
            String g = (b.getGenre() != null ? b.getGenre() : "").toLowerCase();
            String t = (b.getTitle() != null ? b.getTitle() : "").toLowerCase();
            String d = (b.getDescription() != null ? b.getDescription() : "").toLowerCase();

            if (g.contains(currentSelectedGenre) || t.contains(currentSelectedGenre) || d.contains(currentSelectedGenre)) {
                filtered.add(b);
            }
        }

        if (filtered.isEmpty()) {
            recommendedBooksAdapter.updateList(allLoadedBooks);
            Toast.makeText(this, "В свежей ленте мало книг этого жанра, показаны все", Toast.LENGTH_SHORT).show();
        } else {
            recommendedBooksAdapter.updateList(filtered);
        }
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
                    Toast.makeText(MainActivity.this, "Поиск не удался: проверьте сеть", Toast.LENGTH_LONG).show();
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
                    Toast.makeText(MainActivity.this, "Поиск не удался: проверьте сеть", Toast.LENGTH_LONG).show();
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
        libraryAdapter.setClickListener(this::openBookDetailActivity);
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
