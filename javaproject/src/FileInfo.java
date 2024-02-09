import com.google.gson.annotations.SerializedName;

public class FileInfo {
    @SerializedName("filename")
    private String fieldname;
    @SerializedName("originalname")
    private String originalname;
    @SerializedName("encoding")
    private String encoding;
    @SerializedName("mimetype")
    private String mimetype;
    @SerializedName("destination")
    private String destination;
    @SerializedName("path")
    private String path;
    @SerializedName("size")
    private int size;

    public String getFieldname() {
        return fieldname;
    }

    public void setFieldname(String fieldname) {
        this.fieldname = fieldname;
    }

    public String getOriginalname() {
        return originalname;
    }

    public void setOriginalname(String originalname) {
        this.originalname = originalname;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getMimetype() {
        return mimetype;
    }

    public void setMimetype(String mimetype) {
        this.mimetype = mimetype;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
