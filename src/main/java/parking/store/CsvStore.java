package parking.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** One CSV per table. Each update replaces a whole file; nothing is cached. */
public final class CsvStore implements RowStore {
    private final Path directory;
    public CsvStore(Path directory) {
        this.directory = directory;
        try { Files.createDirectories(directory); }
        catch (IOException e) { throw new StorageException("Cannot open store", e); }
    }
    @Override public List<String[]> read(String table) {
        Path file = directory.resolve(table + ".csv");
        try {
            List<String[]> rows = new ArrayList<>();
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) rows.add(line.split(",", -1));
                }
            }
            return rows;
        } catch (IOException e) { throw new StorageException("Cannot read " + table, e); }
    }
    @Override public void write(String table, List<String[]> rows) {
        Path file = directory.resolve(table + ".csv");
        Path temporary = directory.resolve(table + ".tmp");
        StringBuilder text = new StringBuilder();
        for (String[] row : rows) {
            for (String value : row) {
                if (value.contains(",") || value.contains("\n") || value.contains("\r"))
                    throw new IllegalArgumentException("CSV fields must be delimiter-free");
            }
            text.append(String.join(",", row)).append('\n');
        }
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = StandardCharsets.UTF_8.encode(text.toString());
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        } catch (IOException e) { throw new StorageException("Cannot write " + table, e); }
        try {
            // Fail visibly if atomic replacement is unsupported; never fake a commit.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { throw new StorageException("Cannot commit " + table, e); }
    }
}
