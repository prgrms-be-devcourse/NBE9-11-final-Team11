package com.fxflow.domain.admin.repository;

import com.fxflow.domain.admin.dto.AdminTransactionFilter;
import com.fxflow.domain.admin.dto.AdminTransactionItem;
import com.fxflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AdminTransactionRepository 통합 테스트")
class AdminTransactionRepositoryTest extends AbstractIntegrationTest {

    @Autowired private AdminTransactionRepository adminTransactionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // ledger_entries: CHARGE(3일 전), EXCHANGE(2일 전), TRANSFER(1일 전)
        insertLedgerEntry("JNL-001", "CHARGE",   "CREDIT", "KRW", "100000", LocalDate.now().minusDays(3));
        insertLedgerEntry("JNL-002", "EXCHANGE",  "DEBIT",  "USD", "200000", LocalDate.now().minusDays(2));
        insertLedgerEntry("JNL-003", "TRANSFER",  "DEBIT",  "KRW", "50000",  LocalDate.now().minusDays(1));

        // rebalancing_orders: SUCCESS(4일 전), MANUAL_REQUIRED(오늘)
        insertRebalancingOrder("SUCCESS",         "AUTO",   "idem-key-001", LocalDate.now().minusDays(4));
        insertRebalancingOrder("MANUAL_REQUIRED", "MANUAL", "idem-key-002", LocalDate.now());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE ledger_entries CASCADE");
        jdbcTemplate.execute("DELETE FROM rebalancing_orders");
    }

    @Test
    @DisplayName("LEDGER와 SUCCESS 리밸런싱 항목이 createdAt 내림차순으로 합쳐져 반환된다 (MANUAL_REQUIRED 제외)")
    void findAll_returnsUnionInDescOrder() {
        AdminTransactionFilter filter = new AdminTransactionFilter(null, null);

        List<AdminTransactionItem> result = adminTransactionRepository.findAll(filter, 0, 10);

        // MANUAL_REQUIRED 리밸런싱은 status = 'SUCCESS' 조건으로 제외 → 4건
        assertThat(result).hasSize(4);
        assertThat(result.get(0).sourceType()).isEqualTo("LEDGER");  // 1일 전 (오늘 항목은 MANUAL_REQUIRED라 제외)
        assertThat(result.get(0).subType()).isEqualTo("TRANSFER");

        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(result.get(i).createdAt())
                    .isAfterOrEqualTo(result.get(i + 1).createdAt());
        }
    }

    @Test
    @DisplayName("페이지네이션 size가 적용된다")
    void findAll_paginationBySize() {
        AdminTransactionFilter filter = new AdminTransactionFilter(null, null);

        List<AdminTransactionItem> page0 = adminTransactionRepository.findAll(filter, 0, 2);
        List<AdminTransactionItem> page1 = adminTransactionRepository.findAll(filter, 1, 2);

        assertThat(page0).hasSize(2);
        assertThat(page1).hasSize(2);
        // id는 테이블별 독립 시퀀스라 LEDGER와 REBALANCING 간 값이 겹칠 수 있음
        // DESC 정렬이므로 page0의 첫 항목이 page1의 첫 항목보다 최신이어야 함
        assertThat(page0.get(0).createdAt()).isAfter(page1.get(0).createdAt());
    }

    @Test
    @DisplayName("from 날짜 필터가 적용된다")
    void findAll_fromFilter() {
        // 2일 전 이후: EXCHANGE(2일 전), TRANSFER(1일 전) = 2건 (MANUAL_REQUIRED는 SUCCESS 아니므로 제외)
        AdminTransactionFilter filter = new AdminTransactionFilter(LocalDate.now().minusDays(2), null);

        List<AdminTransactionItem> result = adminTransactionRepository.findAll(filter, 0, 10);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(item ->
                !item.createdAt().toLocalDate().isBefore(LocalDate.now().minusDays(2))
        );
    }

    @Test
    @DisplayName("to 날짜 필터가 적용된다")
    void findAll_toFilter() {
        // 2일 전 이하: CHARGE(3일 전), EXCHANGE(2일 전), SUCCESS(4일 전) = 3건
        AdminTransactionFilter filter = new AdminTransactionFilter(null, LocalDate.now().minusDays(2));

        List<AdminTransactionItem> result = adminTransactionRepository.findAll(filter, 0, 10);

        assertThat(result).hasSize(3);
        assertThat(result).allMatch(item ->
                !item.createdAt().toLocalDate().isAfter(LocalDate.now().minusDays(2))
        );
    }

    @Test
    @DisplayName("count가 전체 건수를 반환한다")
    void count_returnsTotal() {
        AdminTransactionFilter filter = new AdminTransactionFilter(null, null);

        long count = adminTransactionRepository.count(filter);

        // MANUAL_REQUIRED 리밸런싱 제외 → 4건
        assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("count에 날짜 필터가 적용된다")
    void count_withDateFilter() {
        // 1일 전 이후: TRANSFER(1일 전) = 1건 (MANUAL_REQUIRED는 SUCCESS 아니므로 제외)
        AdminTransactionFilter filter = new AdminTransactionFilter(LocalDate.now().minusDays(1), null);

        long count = adminTransactionRepository.count(filter);

        assertThat(count).isEqualTo(1);
    }

    private void insertLedgerEntry(String journalId, String entryType, String direction,
                                   String currency, String amount, LocalDate date) {
        jdbcTemplate.update("""
                INSERT INTO ledger_entries
                    (journal_id, entry_type, ledger_direction, currency_code,
                     amount, balance_before, balance_after, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?)
                """,
                journalId, entryType, direction, currency,
                new BigDecimal(amount), new BigDecimal(amount),
                date.atStartOfDay(), date.atStartOfDay());
    }

    private void insertRebalancingOrder(String status, String triggerType,
                                        String idempotencyKey, LocalDate date) {
        jdbcTemplate.update("""
                INSERT INTO rebalancing_orders
                    (status, trigger_type, idempotency_key, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                status, triggerType, idempotencyKey,
                date.atStartOfDay(), date.atStartOfDay());
    }
}