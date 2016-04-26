package kewengu.com.scanit;

/**
 * Created by kewen on 4/25/2016.
 */
public class Document {

    private String createTime;
    private String content;

    public Document(String createTime, String content) {
        this.createTime = createTime;
        this.content = content;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreateTime() {
        return this.createTime;
    }

    public String getContent() {
        return this.content;
    }
}
