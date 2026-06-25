package com.fxflow.domain.admin.repository;

import com.fxflow.domain.admin.dto.AdminTransactionFilter;
import com.fxflow.domain.admin.dto.AdminTransactionItem;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AdminTransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    private static final String UNION_SQL = """
            SELECT 'LEDGER'      AS source_type,
                   l.id,
                   l.entry_type  AS sub_type,
                   l.created_at,
                   l.amount,
                   l.currency_code,
                   l.journal_id,
                   NULL          AS trigger_type
            FROM ledger_entries l
            WHERE l.created_at >= ? AND l.created_at <= ?

            UNION ALL

            SELECT 'REBALANCING' AS source_type,
                   r.id,
                   r.status      AS sub_type,
                   r.created_at,
                   r.buy_amount  AS amount,
                   NULL          AS currency_code,
                   NULL          AS journal_id,
                   r.trigger_type
            FROM rebalancing_orders r
            WHERE r.created_at >= ? AND r.created_at <= ?
            """;

    public List<AdminTransactionItem> findAll(AdminTransactionFilter filter, int page, int size) {
        Timestamp from = toTimestamp(filter.from() != null ? filter.from().atStartOfDay() : MIN_DATE);
        Timestamp to   = toTimestamp(filter.to()   != null ? filter.to().atTime(LocalTime.MAX) : MAX_DATE);

        String sql = UNION_SQL + " ORDER BY created_at DESC LIMIT ? OFFSET ?";

        return jdbcTemplate.query(sql, this::mapRow,
                from, to, from, to, size, (long) page * size);
    }

    public long count(AdminTransactionFilter filter) {
        Timestamp from = toTimestamp(filter.from() != null ? filter.from().atStartOfDay() : MIN_DATE);
        Timestamp to   = toTimestamp(filter.to()   != null ? filter.to().atTime(LocalTime.MAX) : MAX_DATE);

        String sql = "SELECT COUNT(*) FROM (" + UNION_SQL + ") AS t";

        Long count = jdbcTemplate.queryForObject(sql, Long.class, from, to, from, to);
        return count != null ? count : 0L;
    }

    private AdminTransactionItem mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        BigDecimal amount = rs.getBigDecimal("amount");
        return new AdminTransactionItem(
                rs.getLong("id"),
                rs.getString("source_type"),
                rs.getString("sub_type"),
                createdAt != null ? createdAt.toLocalDateTime() : null,
                amount,
                rs.getString("currency_code"),
                rs.getString("journal_id"),
                rs.getString("trigger_type")
        );
    }

    private static Timestamp toTimestamp(LocalDateTime ldt) {
        return Timestamp.valueOf(ldt);
    }
}