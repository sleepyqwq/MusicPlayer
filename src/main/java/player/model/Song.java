// 文件：src/main/java/player/model/Song.java
package player.model;

import javafx.scene.image.Image;

import java.io.File;
import java.util.List;

/**
 * 表示一首歌曲，包含文件路径、标题、艺术家、封面图片，以及对应的歌词列表
 */
public class Song {
    /** 本地音频文件（如 .mp3、.wav、.flac） */
    private final File file;

    /** 歌曲标题 */
    private final String title;

    /** 歌曲艺术家 */
    private final String artist;

    /** 歌曲封面，如果音频文件中没有封面，则使用默认封面 */
    private final Image coverImage;

    /** 已解析的歌词行列表，按时间升序排列 */
    private final List<LyricLine> lyrics;

    /**
     * 构造一个 Song 对象
     *
     *本 @param file       地音频文件
     * @param title      歌曲标题
     * @param artist     艺术家名称
     * @param coverImage 封面图片（从文件中提取或默认图片）
     * @param lyrics     歌词行列表（时间戳升序）
     */
    public Song(File file, String title, String artist, Image coverImage, List<LyricLine> lyrics) {
        this.file = file;
        this.title = title;
        this.artist = artist;
        this.coverImage = coverImage;
        this.lyrics = lyrics;
    }

    /** 返回本地音频文件 */
    public File getFile() {
        return file;
    }

    /** 返回歌曲标题 */
    public String getTitle() {
        return title;
    }

    /** 返回艺术家名称 */
    public String getArtist() {
        return artist;
    }

    /** 返回封面图片 */
    public Image getCoverImage() {
        return coverImage;
    }

    /** 返回已解析的歌词行列表 */
    public List<LyricLine> getLyrics() {
        return lyrics;
    }
}
