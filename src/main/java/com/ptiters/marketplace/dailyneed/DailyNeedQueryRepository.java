package com.ptiters.marketplace.dailyneed;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DailyNeedQueryRepository {

    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @PersistenceContext
    private EntityManager em;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public static LocalDate getVietnamToday() {
        return LocalDate.now(VN_ZONE);
    }

    public List<DailyNeed> findTodayNotes(LocalDate today, int limit) {
        return em.createQuery(
                        "SELECT d FROM DailyNeed d " +
                        "JOIN FETCH d.user " +
                        "WHERE d.noteDate = :today " +
                        "ORDER BY d.updatedAt DESC, d.dailyNeedId DESC",
                        DailyNeed.class)
                .setParameter("today", today)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<DailyNeed> findRecentNotes(int limit) {
        return em.createQuery(
                        "SELECT d FROM DailyNeed d " +
                        "JOIN FETCH d.user " +
                        "ORDER BY d.noteDate DESC, d.updatedAt DESC, d.dailyNeedId DESC",
                        DailyNeed.class)
                .setMaxResults(limit)
                .getResultList();
    }

    public Optional<DailyNeed> findUserNoteForDate(Long userId, LocalDate noteDate) {
        List<DailyNeed> results = em.createQuery(
                        "SELECT d FROM DailyNeed d " +
                        "JOIN FETCH d.user " +
                        "WHERE d.userId = :userId AND d.noteDate = :noteDate",
                        DailyNeed.class)
                .setParameter("userId", userId)
                .setParameter("noteDate", noteDate)
                .getResultList();
        return results.stream().findFirst();
    }

    public Long upsertUserNote(Long userId, LocalDate noteDate, String content) {
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        String sql = """
                INSERT INTO daily_needs (user_id, note_date, content, created_at, updated_at)
                VALUES (:userId, :noteDate, :content, :now, :now)
                ON CONFLICT (user_id, note_date)
                DO UPDATE SET content = EXCLUDED.content, updated_at = EXCLUDED.updated_at
                RETURNING daily_need_id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("noteDate", noteDate, Types.DATE)
                .addValue("content", content)
                .addValue("now", nowUtc, Types.TIMESTAMP_WITH_TIMEZONE);
        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }

    public boolean recordCheckin(Long userId, LocalDate checkinDate) {
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        String sql = """
                INSERT INTO daily_journal_checkins (user_id, checkin_date, created_at)
                VALUES (:userId, :checkinDate, :now)
                ON CONFLICT (user_id, checkin_date) DO NOTHING
                RETURNING checkin_id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("checkinDate", checkinDate, Types.DATE)
                .addValue("now", nowUtc, Types.TIMESTAMP_WITH_TIMEZONE);
        List<Long> insertedIds = jdbcTemplate.queryForList(sql, params, Long.class);
        return !insertedIds.isEmpty();
    }

    public int deleteUserNoteForDate(Long userId, LocalDate noteDate) {
        return em.createQuery(
                        "DELETE FROM DailyNeed d WHERE d.userId = :userId AND d.noteDate = :noteDate")
                .setParameter("userId", userId)
                .setParameter("noteDate", noteDate)
                .executeUpdate();
    }

    public boolean hasUserCheckedIn(Long userId, LocalDate checkinDate) {
        Long count = em.createQuery(
                        "SELECT COUNT(c) FROM DailyJournalCheckin c " +
                        "WHERE c.userId = :userId AND c.checkinDate = :checkinDate",
                        Long.class)
                .setParameter("userId", userId)
                .setParameter("checkinDate", checkinDate)
                .getSingleResult();
        return count != null && count > 0;
    }

    public int calculateUserStreak(Long userId, LocalDate anchorDate) {
        String sql = """
                WITH RECURSIVE streak_chain AS (
                    SELECT checkin_date, 1 AS streak_len
                    FROM daily_journal_checkins
                    WHERE user_id = :userId AND checkin_date = :anchorDate
                    UNION ALL
                    SELECT c.checkin_date, sc.streak_len + 1
                    FROM daily_journal_checkins c
                    JOIN streak_chain sc ON c.checkin_date = CAST(sc.checkin_date - INTERVAL '1 day' AS DATE)
                    WHERE c.user_id = :userId
                )
                SELECT COALESCE(MAX(streak_len), 0) FROM streak_chain
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("anchorDate", anchorDate, Types.DATE);
        Integer result = jdbcTemplate.queryForObject(sql, params, Integer.class);
        return result != null ? result : 0;
    }
}
