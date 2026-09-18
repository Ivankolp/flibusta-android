package is.flibusta.client.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SeriesPage implements Serializable {
    private List<Series> seriesList;
    private String nextPageUrl;

    public SeriesPage() {
        this.seriesList = new ArrayList<>();
        this.nextPageUrl = null;
    }

    public SeriesPage(List<Series> seriesList, String nextPageUrl) {
        this.seriesList = seriesList != null ? seriesList : new ArrayList<>();
        this.nextPageUrl = nextPageUrl;
    }

    public List<Series> getSeriesList() {
        return seriesList;
    }

    public void setSeriesList(List<Series> seriesList) {
        this.seriesList = seriesList;
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
