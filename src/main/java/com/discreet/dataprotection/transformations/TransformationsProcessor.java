package com.discreet.dataprotection.transformations;

import com.discreet.dataprotection.AnonymizerTable;
import com.discreet.dataprotection.anonymizer.Anonymizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import com.discreet.dataprotection.CantReadColumnException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.util.CollectionUtils.isEmpty;

@Component
@Slf4j
public class TransformationsProcessor {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    AnonymizerTable anonymizerTable;

    private static final List<String> DEFAULT_IDS_COLUMN = List.of("id");

    @Transactional
    public void process(List<Transformation> transformations) {
        for (Transformation transformation: transformations) {
            addPostCodeAnonymizerIfNeeded(transformation);
            processTransformation(transformation);
        }
    }

    void processTransformation(Transformation transformation) {
        log.info("Processing db table {}.{}...", transformation.getSchema(), transformation.getTable());
        if (!transformation.getColumnToAnonymizerMap().isEmpty()) {
            String columns = String.join(",", transformation.getColumnToAnonymizerMap().keySet());
            String sql = String.format("select %s,%s from %s.%s",
                    getIdColumnsAsString(transformation),
                    columns, transformation.getSchema(),
                    transformation.getTable());
            try {
                jdbcTemplate.query(sql, rs -> {
                    anonymizeRow(rs, transformation);
                });
            } catch (CantReadColumnException ex) {
                log.error(String.format("There was a problem reading id column for %s.%s, skipping the table...",
                        transformation.getSchema(),
                        transformation.getTable()));
                // skip
            }
        }
    }

    private void addPostCodeAnonymizerIfNeeded(Transformation transformation) {
        for (Map.Entry<String, String> entry: transformation.getColumnToAnonymizerMap().entrySet()) {
            String column = entry.getKey();
            String anonymizer = entry.getValue();
            if (anonymizer.equals("post")) {
                anonymizerTable.addPostCodeAnonymizer(transformation.getSchema(), transformation.getTable(),
                        column);
            }
        }
    }

    private void anonymizeRow(ResultSet rs, Transformation transformation) {
        String modifiedColumns = transformation.getColumnToAnonymizerMap().keySet().stream().flatMap(column -> {
           String anonymized = anonymizeColumn(transformation, column, rs);
           if (anonymized != null) {
               anonymized = anonymized.replaceAll("'", "");
           }
           return anonymized != null ? Stream.of(String.format("%s='%s'", column, anonymized)): Stream.empty();
       }).collect(Collectors.joining(","));

       String update = String.format("update %s.%s set %s where %s", transformation.getSchema(),
                transformation.getTable(), modifiedColumns, getIdsCondition(transformation));

       Long[] idValues = getColumnIdValues(transformation, rs);
       jdbcTemplate.update(update, idValues);
    }

    private Long[] getColumnIdValues(Transformation transformation, ResultSet rs) {
        return getIdColumnsOrId(transformation).stream()
                .map(idColumn -> {
                    try {
                        return rs.getLong(idColumn);
                    } catch (SQLException e) {
                        throw new CantReadColumnException("Can read id from column: " + idColumn);
                    }
                }).toArray(Long[]::new);
    }

    private List<String> getIdColumnsOrId(Transformation transformation) {
        List<String> ids = transformation.getIdColumns();
        if (isEmpty(ids)) {
            ids = DEFAULT_IDS_COLUMN;
        }
        return ids;
    }

    private String getIdColumnsAsString(Transformation transformation) {
        return String.join(",", getIdColumnsOrId(transformation));
    }

    private String getIdsCondition(Transformation transformation) {
        List<String> idsWithWildCard = getIdColumnsOrId(transformation).stream()
                .map(id -> id + "=?").collect(Collectors.toList());
        return "(" + String.join(" AND ", idsWithWildCard) + ")";
    }

    private String anonymizeColumn(Transformation transformation, String column, ResultSet rs) {
        String anonymizerName = transformation.getColumnToAnonymizerMap().get(column);
        Anonymizer anonymizer = anonymizerTable.getAnonymizer(anonymizerName, transformation.getSchema(),
                transformation.getTable(), column);
        String input;
        try {
            if (anonymizerName.equals("birthdate")) {
                input = String.valueOf(rs.getDate(column));
            } else {
                input = rs.getString(column);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return input != null ? anonymizer.anonymize(input) : null;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
}
