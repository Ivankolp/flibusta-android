package is.flibusta.client.data;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private String status; // "Читаю", "Прочитано", "В планах", "Скачано"
    private String authorId;
    private String localPath;
    private String dateAdded;

    // Extended fields for sorting and series management
    private String seriesName = "";
    private String seriesId = "";
    private int seriesNumber = 0;
    private String year = "";
    private String updatedDate = "";
    private int downloads = 0;

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
        return id != null ? id : "";
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getNumericId() {
        if (id == null || id.isEmpty()) return 0;
        try {
            return Long.parseLong(id.replaceAll("\\D", ""));
        } catch (Exception e) {
            return 0;
        }
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

    public long getNumericSize() {
        if (size == null || size.isEmpty()) return 0;
        try {
            Matcher m = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(kb|mb|кб|мб|гб|b|б)?", Pattern.CASE_INSENSITIVE).matcher(size);
            if (m.find()) {
                double val = Double.parseDouble(m.group(1));
                String unit = m.group(2) != null ? m.group(2).toLowerCase() : "kb";
                if (unit.contains("mb") || unit.contains("мб")) {
                    return (long) (val * 1024 * 1024);
                } else if (unit.contains("gb") || unit.contains("гб")) {
                    return (long) (val * 1024 * 1024 * 1024);
                } else if (unit.contains("kb") || unit.contains("кб")) {
                    return (long) (val * 1024);
                } else {
                    return (long) val;
                }
            }
        } catch (Exception ignored) {}
        return 0;
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

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getDateAdded() {
        return dateAdded != null ? dateAdded : "";
    }

    public void setDateAdded(String dateAdded) {
        this.dateAdded = dateAdded;
    }

    public String getSeriesName() {
        return seriesName != null ? seriesName : "";
    }

    public void setSeriesName(String seriesName) {
        this.seriesName = seriesName;
    }

    public String getSeriesId() {
        return seriesId != null ? seriesId : "";
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    public int getSeriesNumber() {
        return seriesNumber;
    }

    public void setSeriesNumber(int seriesNumber) {
        this.seriesNumber = seriesNumber;
    }

    public String getYear() {
        return year != null ? year : "";
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getUpdatedDate() {
        return updatedDate != null ? updatedDate : "";
    }

    public void setUpdatedDate(String updatedDate) {
        this.updatedDate = updatedDate;
    }

    public int getDownloads() {
        return downloads;
    }

    public void setDownloads(int downloads) {
        this.downloads = downloads;
    }

    /**
     * Clear, comprehensive description for TalkBack screen reader.
     */
    public String getFullAccessibilityDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Книга: ").append(getTitle()).append(". ");
        sb.append("Автор: ").append(getAuthor()).append(". ");
        if (seriesName != null && !seriesName.isEmpty()) {
            sb.append("Серия: ").append(seriesName);
            if (seriesNumber > 0) {
                sb.append(", книга номер ").append(seriesNumber);
            }
            sb.append(". ");
        }
        if (year != null && !year.isEmpty()) {
            sb.append("Год: ").append(year).append(". ");
        }
        if (size != null && !size.isEmpty()) {
            sb.append("Размер: ").append(size).append(". ");
        }
        sb.append("Формат: ").append(getFormat().toUpperCase()).append(". ");
        sb.append("Жанр: ").append(getGenre()).append(".");
        return sb.toString();
    }
}
