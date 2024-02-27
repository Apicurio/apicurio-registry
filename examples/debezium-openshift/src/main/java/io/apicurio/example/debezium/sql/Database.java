package io.apicurio.example.debezium.sql;

import io.agroal.api.AgroalDataSource;
import io.apicurio.example.debezium.model.Product;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.sql.*;
import java.util.List;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@ApplicationScoped
public class Database {

    @Inject
    AgroalDataSource dataSource;


    public int insertProduct(Product product) {
        return executeUpdate("INSERT INTO products VALUES (default,?,?,?)", List.of(
                new SqlParam(0, product.getName(), SqlParamType.STRING),
                new SqlParam(1, product.getDescription(), SqlParamType.STRING),
                new SqlParam(2, product.getWeight(), SqlParamType.FLOAT)
        ));
    }


    public void updateProduct(Product product) {
        executeUpdate("UPDATE products SET name = ?, description = ?, weight = ? WHERE id = ?", List.of(
                new SqlParam(0, product.getName(), SqlParamType.STRING),
                new SqlParam(1, product.getDescription(), SqlParamType.STRING),
                new SqlParam(2, product.getWeight(), SqlParamType.FLOAT),
                new SqlParam(3, product.getId(), SqlParamType.INTEGER)
        ));
    }


    public void deleteProduct(Product product) {
        executeUpdate("DELETE FROM products WHERE id = ?", List.of(
                new SqlParam(0, product.getId(), SqlParamType.INTEGER)
        ));
    }


    private int executeUpdate(String sql, List<SqlParam> parameters) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                parameters.forEach(p -> {
                    p.bindTo(statement);
                });
                statement.executeUpdate();
                ResultSet rs = statement.getGeneratedKeys();
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    return -1;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
