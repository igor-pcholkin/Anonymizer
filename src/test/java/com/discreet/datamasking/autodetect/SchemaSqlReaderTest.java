package com.discreet.datamasking.autodetect;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchemaSqlReaderTest {
    SchemaSqlReader schemaSqlReader = new SchemaSqlReader();

    @Test
    public void testLoadSchema() {
        schemaSqlReader.setDefaultSchema("test2");
        List<DBTable> schema = schemaSqlReader.readDDL("src/test/resources/com/discreet/datamasking/itest/schema.sql");
        String actualResult = schema.toString();
        String expectedResult = "[DBTable{schema='test2', table='USERS', columns=[Column{name='id', type='INTEGER'}, " +
                "Column{name='name', type='VARCHAR'}, Column{name='email', type='VARCHAR'}, " +
                "Column{name='address', type='VARCHAR'}, Column{name='birthdate', type='DATE'}, " +
                "Column{name='socialNumber', type='CHAR'}, Column{name='ccard', type='CHAR'}, " +
                "Column{name='post_code', type='VARCHAR'}]}, " +

                "DBTable{schema='test2', table='COMPANIES', columns=[Column{name='company_id', type='INTEGER'}, " +
                "Column{name='name', type='VARCHAR'}, Column{name='address', type='VARCHAR'}]}]";
        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void testExceptionForNoShemaName() {
        schemaSqlReader.setDefaultSchema(null);
        try {
            schemaSqlReader.readDDL("src/test/resources/com/discreet/datamasking/itest/schema.sql");
            fail();
        } catch (RuntimeException ex) {
            assertEquals("No schema name is found for table USERS, please use -dsn (--defaultSchemaName) command line argument to set a default one.", ex.getMessage());
        }
    }
}