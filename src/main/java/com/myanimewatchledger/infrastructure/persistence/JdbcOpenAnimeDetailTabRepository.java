package com.myanimewatchledger.infrastructure.persistence;

import com.myanimewatchledger.application.port.OpenAnimeDetailTabRepository;
import com.myanimewatchledger.domain.catalogue.OpenAnimeDetailTab;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JdbcOpenAnimeDetailTabRepository implements OpenAnimeDetailTabRepository {
    private final DatabaseTransactionManager transactions;

    public JdbcOpenAnimeDetailTabRepository(DatabaseTransactionManager transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public List<OpenAnimeDetailTab> findAllOrdered() {
        return transactions.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT mal_id, tab_order FROM open_anime_detail_tab ORDER BY tab_order")) {
                List<OpenAnimeDetailTab> resultTabs = new ArrayList<>();
                while (result.next()) {
                    resultTabs.add(new OpenAnimeDetailTab(result.getLong("mal_id"),
                            result.getInt("tab_order")));
                }
                return resultTabs;
            }
        });
    }

    @Override
    public void replaceAll(List<OpenAnimeDetailTab> tabs) {
        Objects.requireNonNull(tabs, "tabs");
        transactions.executeInTransaction(() -> {
            transactions.withConnection(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM open_anime_detail_tab");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO open_anime_detail_tab(mal_id, tab_order) VALUES(?, ?)")) {
                    for (OpenAnimeDetailTab tab : tabs) {
                        statement.setLong(1, tab.malId());
                        statement.setInt(2, tab.tabOrder());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                return null;
            });
            return null;
        });
    }
}
