package is.flibusta.client.data;

import java.io.Serializable;

public class GenreItem implements Serializable {
    private String title;
    private String url;
    private String countOrDesc;
    private boolean isLeaf;

    public GenreItem(String title, String url, String countOrDesc, boolean isLeaf) {
        this.title = title;
        this.url = url;
        this.countOrDesc = countOrDesc;
        this.isLeaf = isLeaf;
    }

    public String getTitle() {
        return title != null ? title : "";
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url != null ? url : "";
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCountOrDesc() {
        return countOrDesc != null ? countOrDesc : "";
    }

    public void setCountOrDesc(String countOrDesc) {
        this.countOrDesc = countOrDesc;
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    public void setLeaf(boolean leaf) {
        isLeaf = leaf;
    }
}
