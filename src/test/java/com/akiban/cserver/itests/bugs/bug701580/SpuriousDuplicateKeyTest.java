package com.akiban.cserver.itests.bugs.bug701580;

import com.akiban.ais.model.Index;
import com.akiban.ais.model.UserTable;
import com.akiban.cserver.api.common.TableId;
import com.akiban.cserver.itests.ApiTestBase;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public final class SpuriousDuplicateKeyTest extends ApiTestBase {
    @Test
    public void simpleOnce() throws Exception {
        simpleTestCase();
    }

    @Test
    public void simpleTwice() throws Exception {
        simpleTestCase();
        simpleTestCase();
    }

    private void simpleTestCase() throws Exception {
        createTable("test", "t1", "bid1 int, token varchar(64), primary key(bid1), key (token)");
        TableId t2 = createTable("test", "t2", "bid int, theme varchar(64), primary key (bid), unique key (theme)");

        confirmIds("t1", 0, 2, 2);
        confirmIds("t2", 0, 2, 2);

        writeRows(
                createNewRow(t2, 1, "0"),
                createNewRow(t2, 2, "1"),
                createNewRow(t2, 3, "2")
        );
        dropAllTables();
    }

    @Test
    public void indexIdsLocalToGroup() throws Exception {
        createTable("test", "t1", "bid1 int, token varchar(64), primary key(bid1), key (token)");

        createTable("test", "t2", "bid int, theme varchar(64), primary key (bid), unique key (theme)");
        createTable("test", "t3", "id int key, bid_id int, "
                +"CONSTRAINT __akiban_fk FOREIGN KEY (bid_id) REFERENCES t2 (bid)");

        confirmIds("t1", 0, 2, 2);
        confirmIds("t2", 0, 2, 4);
        confirmIds("t3", 2, 2, 4);
    }

    /**
     * Confirm that the given table has sequential index IDs starting from the given number, and that its
     * group table has all those indexes as well.
     * @param tableName the table to start at
     * @param  startingAt the index to start at
     * @throws Exception
     */
    private void confirmIds(String tableName, int startingAt, int expectedUIndexes, int expectedGIndexes)
            throws Exception {
        UserTable uTable = ddl().getAIS(session).getUserTable("test", tableName);

        Set<Integer> expectedUTableIds = new HashSet<Integer>();
        Set<Integer> actualUTableIds = new HashSet<Integer>();
        for (Index index : uTable.getIndexes()) {
            actualUTableIds.add(index.getIndexId());
            expectedUTableIds.add( expectedUTableIds.size() + startingAt );
        }

        Set<Integer> actualGTableIds = new HashSet<Integer>();
        for(Index index : uTable.getGroup().getGroupTable().getIndexes()) {
            actualGTableIds.add(index.getIndexId());
        }

        assertEquals("uTable index count", expectedUIndexes, actualUTableIds.size());
        assertEquals("actualUTableIds", actualUTableIds, expectedUTableIds);

        if(!actualGTableIds.containsAll(actualUTableIds)) {
            Set<Integer> missing = new HashSet<Integer>(actualUTableIds);
            missing.removeAll(actualGTableIds);
            fail(String.format("missing %s: %s doesn't contain all of %s", missing, actualGTableIds, actualUTableIds));
        }
        assertEquals("uTable index count", expectedGIndexes, actualGTableIds.size());
    }
}
