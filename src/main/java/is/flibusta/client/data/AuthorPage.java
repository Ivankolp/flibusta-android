package is.flibusta.client.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AuthorPage implements Serializable {
    private List<Author> authors;
    private String nextPageUrl;

    public AuthorPage(List<Author> authors, String nextPageUrl) {
        this.authors = authors != null ? authors : new ArrayList<>();
        this.nextPageUrl = nextPageUrl;
    }

    public List<Author> getAuthors() {
        return authors;
    }

    public String getNextPageUrl() {
        return nextPageUrl;
    }
}
