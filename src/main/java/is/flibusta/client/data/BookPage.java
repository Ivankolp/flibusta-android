package is.flibusta.client.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class BookPage implements Serializable {
    private List<Book> books;
    private String nextPageUrl;

    public BookPage() {
        this.books = new ArrayList<>();
        this.nextPageUrl = null;
    }

    public BookPage(List<Book> books, String nextPageUrl) {
        this.books = books != null ? books : new ArrayList<>();
        this.nextPageUrl = nextPageUrl;
    }

    public List<Book> getBooks() {
        return books;
    }

    public void setBooks(List<Book> books) {
        this.books = books;
    }

    public String getNextPageUrl() {
        return nextPageUrl;
    }

    public void setNextPageUrl(String nextPageUrl) {
        this.nextPageUrl = nextPageUrl;
    }

    public boolean hasNextPage() {
        return nextPageUrl != null && !nextPageUrl.isEmpty();
    }
}
