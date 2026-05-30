package com.qiyuzhulu.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyuzhulu.model.GameState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * 存档读写 — JSON文件为主（兼容Python版），SQLite为辅（统计查询）。
 */
@Repository
public class SaveRepo {

    @Value("${qiyu.save.directory:saves}")
    private String saveDir;

    private final ObjectMapper mapper = new ObjectMapper();

    /** 确保存档目录存在 */
    private Path ensureDir() throws IOException {
        Path dir = Path.of(saveDir);
        Files.createDirectories(dir);
        return dir;
    }

    /** 列出所有存档 */
    public List<Map<String, Object>> listSaves() throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        Path dir = ensureDir();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : stream) {
                String slot = p.getFileName().toString().replace(".json", "");
                try {
                    GameState gs = mapper.readValue(p.toFile(), GameState.class);
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("slot", slot);
                    info.put("faction", gs.getFactionState() != null ? gs.getFactionState().getName() : "?");
                    info.put("turn", gs.getTurn());
                    info.put("date", gs.getGameDate());
                    info.put("phase", gs.getPhase());
                    result.add(info);
                } catch (Exception e) {
                    // 跳过损坏的存档
                }
            }
        }
        result.sort((a, b) -> b.get("slot").toString().compareTo(a.get("slot").toString()));
        return result;
    }

    /** 加载存档 */
    public GameState load(String slot) throws IOException {
        Path dir = ensureDir();
        Path file = dir.resolve(slot + ".json");
        if (!Files.exists(file)) return null;
        return mapper.readValue(file.toFile(), GameState.class);
    }

    /** 保存存档 */
    public void save(String slot, GameState state) throws IOException {
        Path dir = ensureDir();
        Path file = dir.resolve(slot + ".json");
        state.setUpdatedAt(java.time.LocalDateTime.now().toString());
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), state);
    }

    /** 检查存档是否存在 */
    public boolean exists(String slot) {
        return Files.exists(Path.of(saveDir, slot + ".json"));
    }
}
