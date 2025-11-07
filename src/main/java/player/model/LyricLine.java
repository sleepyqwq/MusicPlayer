// 文件：src/main/java/player/model/LyricLine.java
package player.model;

/**
 * 表示一行歌词，包含时间戳（毫秒）和歌词文本
 */
public class LyricLine {
    /** 歌词对应的时间戳（毫秒） */
    private final long timeInMillis;
    /** 歌词文本 */
    private final String text;

    public LyricLine(long timeInMillis, String text) {
        this.timeInMillis = timeInMillis;
        this.text = text;
    }

    /** 返回该行歌词的时间戳（毫秒） */
    public long getTimeInMillis() {
        return timeInMillis;
    }

    /** 返回该行歌词的文本 */
    public String getText() {
        return text;
    }
}
