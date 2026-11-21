package community.speaker.client;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

public class VoiceMessageStoreTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void onlyListsFinalizedRecordingsAndSurvivesReopen() throws IOException {
        VoiceMessageStore store = new VoiceMessageStore(folder.getRoot());
        assertTrue(store.list().isEmpty());
        File abandoned = store.begin();
        File draft = store.begin();
        try (FileOutputStream output = new FileOutputStream(draft)) {
            output.write(42);
        }
        assertTrue(store.list().isEmpty());
        File saved = store.save(draft);
        assertFalse(draft.exists());
        assertEquals(saved, new VoiceMessageStore(folder.getRoot()).list().get(0));
        assertTrue(store.discard(abandoned));
        assertTrue(store.delete(saved));
        assertTrue(store.list().isEmpty());
    }

    @Test public void refusesEmptyDraftAndForeignFiles() throws IOException {
        VoiceMessageStore store = new VoiceMessageStore(folder.getRoot());
        File empty = store.begin();
        File foreign = folder.newFile("message-external.m4a");
        assertThrows(IOException.class, () -> store.save(empty));
        assertFalse(store.delete(foreign));
        assertTrue(foreign.exists());
        assertTrue(store.discard(empty));
    }
}
