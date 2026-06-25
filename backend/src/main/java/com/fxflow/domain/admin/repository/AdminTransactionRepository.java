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
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AdminTransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    // to 필터는 exclusive 상한(< toExclusive)으로 처리하므로, sentinel은 다음 세기 첫날 자정
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

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
            WHERE l.created_at >= ? AND l.created_at < ?

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
            WHERE r.created_at >= ? AND r.created_at < ?
            """;

    public List<AdminTransactionItem> findAll(AdminTransactionFilter filter, int page, int size) {
        LocalDateTime from = filter.from() != null ? filter.from().atStartOfDay() : MIN_DATE;
        // filter.to() 당일 포함을 위해 exclusive 상한으로 변환 (다음 날 자정 미만)
        LocalDateTime to   = filter.to()   != null ? filter.to().plusDays(1).atStartOfDay() : MAX_DATE;

        String sql = UNION_SQL + " ORDER BY created_at DESC LIMIT ? OFFSET ?";

        return jdbcTemplate.query(sql, this::mapRow,
                from, to, from, to, size, (long) page * size);
    }

    public long count(AdminTransactionFilter filter) {
        LocalDateTime from = filter.from() != null ? filter.from().atStartOfDay() : MIN_DATE;
        LocalDateTime to   = filter.to()   != null ? filter.to().plusDays(1).atStartOfDay() : MAX_DATE;

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
}