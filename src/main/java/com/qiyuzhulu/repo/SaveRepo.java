package com.qiyuzhulu.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyuzhulu.model.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 存档持久化 — JSON兼容 + SQLite(回合快照+历史统计)。
 * 对标 Python game_db.py（925行）。
 */
@Repository
public class SaveRepo {

    @Value("${qiyu.save.directory:saves}")
    private String saveDir;

    private Connection conn;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void init() throws Exception {
        Files.createDirectories(Path.of(saveDir));
        conn = DriverManager.getConnection("jdbc:sqlite:" + saveDir + "/qiyu.db");
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=ON");
        }
        initTables();
    }

    @PreDestroy
    public void close() { try { if (conn != null) conn.close(); } catch (Exception ignored) {} }

    // ═════════════════════════════════════ 建表 ═════════════════════════════════════

    private void initTables() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS game_meta (" +
                    "slot TEXT PRIMARY KEY, version INTEGER, phase INTEGER, turn INTEGER, game_date TEXT," +
                    "player_faction_id TEXT, action_points INTEGER, ap_max INTEGER, created_at TEXT, updated_at TEXT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS turn_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, save_slot TEXT, turn INTEGER, game_date TEXT," +
                    "faction_id TEXT, faction_name TEXT, is_player INTEGER," +
                    "industry INTEGER, agriculture INTEGER, military INTEGER, economy INTEGER," +
                    "ideology INTEGER, diplomacy INTEGER, naval_power INTEGER," +
                    "treasury INTEGER, population_support INTEGER, territory_count INTEGER, unit_count INTEGER)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_th_turn ON turn_history(turn)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_th_slot ON turn_history(save_slot)");
        }
    }

    // ═════════════════════════════════════ 回合快照 ═════════════════════════════════════

    public void snapshotTurn(GameState state, String slot) {
        try {
            conn.setAutoCommit(false);
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO turn_history (save_slot,turn,game_date,faction_id,faction_name,is_player," +
                    "industry,agriculture,military,economy,ideology,diplomacy,naval_power," +
                    "treasury,population_support,territory_count,unit_count) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            insertSnap(ps, state, slot, state.getPlayerFactionId(), state.getFactionState().getName(), true, state.getFactionState());
            for (var ae : state.getAiFactions().entrySet()) {
                FactionState afs = ae.getValue().getFactionState();
                if (afs != null) insertSnap(ps, state, slot, ae.getKey(),
                        afs.getName() != null ? afs.getName() : ae.getKey(), false, afs);
            }
            ps.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) { try { conn.rollback(); } catch (Exception ignored) {} }
    }

    private void insertSnap(PreparedStatement ps, GameState state, String slot,
                             String fid, String name, boolean isPlayer, FactionState fs) throws SQLException {
        Stats s = fs.getStats();
        ps.setString(1, slot); ps.setInt(2, state.getTurn()); ps.setString(3, state.getGameDate());
        ps.setString(4, fid); ps.setString(5, name); ps.setInt(6, isPlayer ? 1 : 0);
        ps.setInt(7, s.getIndustry()); ps.setInt(8, s.getAgriculture());
        ps.setInt(9, s.getMilitary()); ps.setInt(10, s.getEconomy());
        ps.setInt(11, s.getIdeology()); ps.setInt(12, s.getDiplomacy()); ps.setInt(13, s.getNavalPower());
        ps.setInt(14, fs.getTreasury()); ps.setInt(15, fs.getPopulationSupport());
        ps.setInt(16, fs.getTerritories() != null ? fs.getTerritories().size() : 0);
        ps.setInt(17, fs.getUnits() != null ? (int) fs.getUnits().stream().filter(Unit::isActive).count() : 0);
        ps.addBatch();
    }

    // ═════════════════════════════════════ JSON存档 ═════════════════════════════════════

    public void save(String slot, GameState state) throws IOException {
        state.setUpdatedAt(LocalDateTime.now().toString());
        if (state.getCreatedAt() == null) state.setCreatedAt(LocalDateTime.now().toString());
        Path file = Path.of(saveDir, slot + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), state);
        snapshotTurn(state, slot);
        updateMeta(slot, state);
    }

    public GameState load(String slot) throws IOException {
        Path file = Path.of(saveDir, slot + ".json");
        if (!Files.exists(file)) return null;
        return mapper.readValue(file.toFile(), GameState.class);
    }

    public List<Map<String, Object>> listSaves() {
        List<Map<String, Object>> result = new ArrayList<>();
        try (var stream = Files.list(Path.of(saveDir))) {
            for (Path p : stream.filter(f -> f.toString().endsWith(".json")).collect(Collectors.toList())) {
                String slot = p.getFileName().toString().replace(".json", "");
                try {
                    GameState gs = mapper.readValue(p.toFile(), GameState.class);
                    result.add(Map.<String,Object>of("slot", slot, "faction",
                            gs.getFactionState() != null ? gs.getFactionState().getName() : "?",
                            "turn", gs.getTurn(), "date", gs.getGameDate()));
                } catch (Exception ignored) {}
            }
        } catch (IOException ignored) {}
        result.sort((a, b) -> b.get("slot").toString().compareTo(a.get("slot").toString()));
        return result;
    }

    public boolean exists(String slot) { return Files.exists(Path.of(saveDir, slot + ".json")); }

    private void updateMeta(String slot, GameState state) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO game_meta (slot,version,phase,turn,game_date,player_faction_id,action_points,ap_max,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, slot); ps.setInt(2, state.getVersion()); ps.setInt(3, state.getPhase());
            ps.setInt(4, state.getTurn()); ps.setString(5, state.getGameDate());
            ps.setString(6, state.getPlayerFactionId()); ps.setInt(7, state.getActionPoints());
            ps.setInt(8, state.getApMax());
            ps.setString(9, state.getCreatedAt() != null ? state.getCreatedAt() : "");
            ps.setString(10, state.getUpdatedAt() != null ? state.getUpdatedAt() : "");
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // ═════════════════════════════════════ 统计查询 ═════════════════════════════════════

    public List<Map<String, Object>> getRankings(String slot) {
        List<Map<String, Object>> r = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM turn_history WHERE save_slot=? AND turn=(SELECT MAX(turn) FROM turn_history WHERE save_slot=?) " +
                "ORDER BY (military*0.4+economy*0.3+industry*0.2+territory_count*0.1) DESC")) {
            ps.setString(1, slot); ps.setString(2, slot);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) r.add(Map.of("faction_id", rs.getString("faction_id"), "name", rs.getString("faction_name"),
                    "is_player", rs.getInt("is_player")==1, "military", rs.getInt("military"),
                    "economy", rs.getInt("economy"), "industry", rs.getInt("industry"),
                    "territory_count", rs.getInt("territory_count"), "treasury", rs.getInt("treasury")));
        } catch (SQLException ignored) {}
        return r;
    }

    public List<Map<String, Object>> getPlayerTrend(String slot) {
        List<Map<String, Object>> r = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT turn,game_date,military,economy,industry,agriculture,ideology,diplomacy,treasury,population_support,territory_count,unit_count FROM turn_history WHERE save_slot=? AND is_player=1 ORDER BY turn")) {
            ps.setString(1, slot);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("turn", rs.getInt("turn")); row.put("date", rs.getString("game_date"));
                row.put("military", rs.getInt("military")); row.put("economy", rs.getInt("economy"));
                row.put("industry", rs.getInt("industry")); row.put("agriculture", rs.getInt("agriculture"));
                row.put("ideology", rs.getInt("ideology")); row.put("diplomacy", rs.getInt("diplomacy"));
                row.put("treasury", rs.getInt("treasury")); row.put("population_support", rs.getInt("population_support"));
                row.put("territory_count", rs.getInt("territory_count")); row.put("unit_count", rs.getInt("unit_count"));
                r.add(row);
            }
        } catch (SQLException ignored) {}
        return r;
    }

    public Map<String, Object> getSummary(String slot) {
        Map<String, Object> r = new LinkedHashMap<>();
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM game_meta WHERE slot=?");
            ps.setString(1, slot); ResultSet rs = ps.executeQuery();
            if (rs.next()) { r.put("turn", rs.getInt("turn")); r.put("date", rs.getString("game_date")); r.put("phase", rs.getInt("phase")); }
            ps = conn.prepareStatement("SELECT MAX(military),MAX(economy),MAX(territory_count),MAX(treasury) FROM turn_history WHERE save_slot=?");
            ps.setString(1, slot); rs = ps.executeQuery();
            if (rs.next()) { r.put("peak_military", rs.getInt(1)); r.put("peak_economy", rs.getInt(2));
                r.put("peak_territories", rs.getInt(3)); r.put("peak_treasury", rs.getInt(4)); }
            ps = conn.prepareStatement("SELECT COUNT(DISTINCT faction_id) FROM turn_history WHERE save_slot=? AND military=0 AND is_player=0");
            ps.setString(1, slot); rs = ps.executeQuery();
            if (rs.next()) r.put("factions_eliminated", rs.getInt(1));
        } catch (SQLException ignored) {}
        return r;
    }
}
