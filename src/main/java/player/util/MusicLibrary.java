// 文件：src/main/java/player/util/MusicLibrary.java
package player.util;

import player.model.Song;
import player.model.LyricLine;
import javafx.scene.image.Image;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.FieldKey;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 用于扫描 MusicList 文件夹并提取所有支持的音频文件信息
 */
public class MusicLibrary {
    private static final String[] EXTENSIONS = {".mp3", ".wav", ".flac"};

    public static List<Song> loadAllSongs() {
        List<Song> songs = new ArrayList<>();
        File musicDir = new File("MusicList");
        if (!musicDir.exists() || !musicDir.isDirectory()) {
            return songs;
        }
        File[] files = musicDir.listFiles();
        if (files == null) {
            return songs;
        }
        for (File file : files) {
            if (file.isFile() && matchesExtension(file.getName())) {
                Song song = parseSongFile(file);
                if (song != null) {
                    songs.add(song);
                }
            }
        }
        return songs;
    }

    private static boolean matchesExtension(String fileName) {
        String lower = fileName.toLowerCase();
        for (String ext : EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static Song parseSongFile(File file) {
        try {
            AudioFile audioFile = AudioFileIO.read(file);
            Tag tag = audioFile.getTag();
            AudioHeader header = audioFile.getAudioHeader();

            String artist = "";
            String title  = "";

            // —— 先尝试从标签里取 TITLE/ARTIST ——
            if (tag != null) {
                String t = tag.getFirst(FieldKey.TITLE);
                String a = tag.getFirst(FieldKey.ARTIST);
                if (t != null && !t.isEmpty()) {
                    title = t;
                }
                if (a != null && !a.isEmpty()) {
                    artist = a;
                }
            }

            // —— 如果标签里没有 Title 或 Artist，就从文件名拆分 ——
            if (title.isEmpty() && artist.isEmpty()) {
                String name = file.getName();
                int dot = name.lastIndexOf('.');
                String base = (dot > 0) ? name.substring(0, dot) : name;
                if (base.contains(" - ")) {
                    String[] parts = base.split(" - ", 2);
                    artist = parts[0];
                    String tpart = parts[1];
                    if (tpart.endsWith("_SQ") || tpart.endsWith("_HQ")) {
                        title = tpart.substring(0, tpart.length() - 3);
                    } else {
                        title = tpart;
                    }
                } else {
                    title = base;
                }
            }

            // —— 封面图处理 ——
            Image coverImage;
            if (tag != null && tag.getFirstArtwork() != null) {
                byte[] imageData = tag.getFirstArtwork().getBinaryData();
                coverImage = new Image(new ByteArrayInputStream(imageData));
            } else {
                coverImage = new Image(
                        MusicLibrary.class.getResource("/images/disc.png").toExternalForm()
                );
            }

            // —— 解析歌词：优先标签内嵌歌词，否则查找同名 .lrc ——
            List<LyricLine> lyricList = new ArrayList<>();

            // 1) 如果标签内有歌词，先按简单行拆，也可忽略，后面检测 .lrc 覆盖
            if (tag != null) {
                String rawLyrics = tag.getFirst(FieldKey.LYRICS);
                if (rawLyrics != null && !rawLyrics.isEmpty()) {
                    String[] lines = rawLyrics.split("\\r?\\n");
                    long ts = 0L;
                    for (String line : lines) {
                        lyricList.add(new LyricLine(ts, line));
                        ts += 1000L;
                    }
                }
            }

            // 2) 查找同目录下与音频同名的 .lrc 文件
            String fileName = file.getName();
            int dotIndex = fileName.lastIndexOf('.');
            String baseName = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
            File lrcFile = new File(file.getParent(), baseName + ".lrc");
            if (lrcFile.exists() && lrcFile.isFile()) {
                // 把之前标签里简单拆的清空，改用 LRC 解析结果
                lyricList.clear();
                parseLrcFile(lrcFile, lyricList);
            }

            // 3) 按时间戳排序，确保升序
            lyricList.sort(Comparator.comparingLong(LyricLine::getTimeInMillis));

            // —— 构造并返回 Song 对象 ——
            return new Song(
                    file,
                    title,
                    artist,
                    coverImage,
                    lyricList
            );

        } catch (Exception e) {
            System.err.println("读取歌曲失败：" + file.getName());
            e.printStackTrace();
            return null;
        }
    }

    /** 解析 .lrc 文件，把解析出的 (毫秒, 文本) 填入 lyricList **/
    private static void parseLrcFile(File lrcFile, List<LyricLine> lyricList) {
        // 时间戳正则：支持 [mm:ss.xx] 或 [mm:ss.xxx]
        Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})]");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(lrcFile), Charset.forName("GBK"))
        );
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                List<Long> times = new ArrayList<>();
                int lastMatchEnd = 0;
                while (matcher.find()) {
                    int min = Integer.parseInt(matcher.group(1));
                    int sec = Integer.parseInt(matcher.group(2));
                    String frac = matcher.group(3);
                    long millis;
                    if (frac.length() == 2) {
                        millis = Integer.parseInt(frac) * 10L;
                    } else {
                        millis = Integer.parseInt(frac);
                    }
                    long total = min * 60 * 1000L + sec * 1000L + millis;
                    times.add(total);
                    lastMatchEnd = matcher.end();
                }
                if (!times.isEmpty()) {
                    String text = line.substring(lastMatchEnd).trim();
                    for (Long t : times) {
                        lyricList.add(new LyricLine(t, text));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("解析 LRC 文件失败：" + lrcFile.getName());
            e.printStackTrace();
        }
    }
}
