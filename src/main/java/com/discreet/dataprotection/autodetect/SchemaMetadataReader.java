package com.discreet.dataprotection.autodetect;

import com.discreet.dataprotection.EmptySchemaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

import static org.springframework.util.CollectionUtils.isEmpty;

@Service
@Slf4j
public class SchemaMetadataReader {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public SchemaMetadataReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DBTable> read(String schema) {
        List<DBTable> tables = new LinkedList<>();
        try {
            jdbcTemplate.query("SHOW TABLES FROM " + schema, rs -> {
                String table = rs.getString(1);
                List<Column> columns = new LinkedList<>();
                DBTable dbTable = new DBTable(schema, table, columns);
                addColumns(dbTable);
                tables.add(dbTable);
            });
        } catch (BadSqlGrammarException ex) {
            throw new EmptySchemaException("Error: can't read schema: " + schema);
        }
        if (isEmpty(tables)) {
            throw new EmptySchemaException("Error: schema is empty or doesn't exist: " + schema);
        }
        return tables;
    }

    private void addColumns(DBTable dbTable) {
        String sql = String.format("SHOW COLUMNS FROM %s.%s", dbTable.getSchema(), dbTable.getTable());
        List<Column> columns = dbTable.getColumns();
        jdbcTemplate.query(sql, rs -> {
            Column column = new Column(rs.getString(1), rs.getString(2));
            columns.add(column);
        });
    }
}
