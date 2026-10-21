package parking.store;

import java.util.List;

/** Repositories own table names and serialization; the store only owns file IO. */
public interface RowStore {
    List<String[]> read(String table);
    void write(String table, List<String[]> rows);
}
