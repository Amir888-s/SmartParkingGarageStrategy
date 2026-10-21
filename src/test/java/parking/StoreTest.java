package parking;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import parking.store.*;
import static org.junit.jupiter.api.Assertions.*;

class StoreTest {
    @TempDir Path directory;
    @Test void csvRoundTripPersistsAndRejectsDelimiterInjection() {
        CsvStore disk = new CsvStore(directory); assertTrue(disk.read("test").isEmpty());
        disk.write("test", Collections.singletonList(new String[]{"one", "two", ""}));
        assertArrayEquals(new String[]{"one", "two", ""}, new CsvStore(directory).read("test").get(0));
        assertThrows(IllegalArgumentException.class, () -> disk.write("test", Collections.singletonList(new String[]{"a,b"})));
        disk.write("test", List.of()); assertTrue(disk.read("test").isEmpty());
    }
    @Test void actualFileFailuresAreReported() throws Exception {
        Path ordinaryFile = directory.resolve("file"); Files.writeString(ordinaryFile, "not a directory");
        assertThrows(StorageException.class, () -> new CsvStore(ordinaryFile));
        CsvStore disk = new CsvStore(directory);
        Files.createDirectory(directory.resolve("bad.csv")); assertThrows(StorageException.class, () -> disk.read("bad"));
        Files.createDirectory(directory.resolve("blocked.tmp")); assertThrows(StorageException.class, () -> disk.write("blocked", List.of()));
        Files.createDirectories(directory.resolve("commit.csv/nonempty")); assertThrows(StorageException.class, () -> disk.write("commit", List.of()));
    }
}
