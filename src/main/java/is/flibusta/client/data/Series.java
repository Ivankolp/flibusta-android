package is.flibusta.client.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Series implements Serializable {
    private String id;
    private String title;
    private String author;
    private int bookCount;
    private List<Book> books;

    public Series(String id, String title, String author, int bookCount) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.bookCount = bookCount;
        this.books = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public int getBookCount() {
        return bookCount > 0 ? bookCount : (books != null ? books.size() : 0);
    }

    public List<Book> getBooks() {
        return books;
    }

    public void setBooks(List<Book> books) {
        this.books = books;
        if (books != null) {
            this.bookCount = books.size();
        }
    }

    public void addBook(Book book) {
        if (books == null) {
            books = new ArrayList<>();
        }
        books.add(book);
        bookCount = books.size();
    }
}
