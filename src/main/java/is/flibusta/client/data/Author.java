package is.flibusta.client.data;

import java.io.Serializable;

public class Author implements Serializable {
    private String id;
    private String name;
    private int bookCount;

    public Author(String id, String name, int bookCount) {
        this.id = id != null ? id : "";
        this.name = name != null ? name : "";
        this.bookCount = bookCount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getBookCount() {
        return bookCount;
    }

    public void setBookCount(int bookCount) {
        this.bookCount = bookCount;
    }
}
