package is.flibusta.client.data;

import java.io.Serializable;

public class Book implements Serializable {
    private String id;
    private String title;
    private String author;
    private String genre;
    private String size;
    private String format;
    private String downloadUrl;
    private String coverUrl;
    private String description;
    private String status; // "Читаю", "Прочитано", "В планах"
    private String localPath;
    private String dateAdded;

    public Book() {
    }

    public Book(String id, String title, String author, String genre, String size, String format, String downloadUrl) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.genre = genre;
        this.size = size;
        this.format = format != null ? format : "fb2";
        this.downloadUrl = downloadUrl;
        this.status = "В планах";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title != null ? title : "Без названия";
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author != null ? author : "Неизвестный автор";
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getGenre() {
        return genre != null ? genre : "Художественная литература";
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getSize() {
        return size != null ? size : "";
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getFormat() {
        return format != null ? format : "fb2";
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getDownloadUrl() {
        return downloadUrl != null ? downloadUrl : "";
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status != null ? status : "В планах";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getDateAdded() {
        return dateAdded != null ? dateAdded : "";
    }

    public void setDateAdded(String dateAdded) {
        this.dateAdded = dateAdded;
    }
}
