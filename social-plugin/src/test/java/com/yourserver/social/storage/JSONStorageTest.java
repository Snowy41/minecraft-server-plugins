package com.yourserver.social.storage;

import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class JSONStorageTest {

    static class TestData {
        public Map<String, String> data = new HashMap<>();
    }

    @Test
    void saveAndLoad_withData_persistsCorrectly(@TempDir File tempDir) {
        Logger logger = Logger.getLogger("TestLogger");
        JSONStorage<TestData> storage = new JSONStorage<>(
                tempDir,
                "test.json",
                new TypeToken<TestData>(){},
                new TestData(),
                logger
        );

        TestData original = new TestData();
        original.data.put("key1", "value1");
        original.data.put("key2", "value2");

        // Save
        storage.save(original).join();

        // Load
        TestData loaded = storage.load().join();

        assertEquals(2, loaded.data.size());
        assertEquals("value1", loaded.data.get("key1"));
        assertEquals("value2", loaded.data.get("key2"));
    }

    @Test
    void load_nonExistentFile_returnsEmptyValue(@TempDir File tempDir) {
        Logger logger = Logger.getLogger("TestLogger");
        JSONStorage<TestData> storage = new JSONStorage<>(
                tempDir,
                "nonexistent.json",
                new TypeToken<TestData>(){},
                new TestData(),
                logger
        );

        TestData loaded = storage.load().join();

        assertNotNull(loaded);
        assertTrue(loaded.data.isEmpty());
    }

    @Test
    void save_createsBackup(@TempDir File tempDir) {
        Logger logger = Logger.getLogger("TestLogger");
        JSONStorage<TestData> storage = new JSONStorage<>(
                tempDir,
                "test.json",
                new TypeToken<TestData>(){},
                new TestData(),
                logger
        );

        TestData data1 = new TestData();
        data1.data.put("key", "value1");
        storage.save(data1).join();

        TestData data2 = new TestData();
        data2.data.put("key", "value2");
        storage.save(data2).join();

        assertTrue(storage.getBackupFile().exists());
    }
}