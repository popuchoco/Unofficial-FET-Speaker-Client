package community.speaker.client;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

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

    @Test public void keepsSameTimeMessagesDistinctWhenOneIsDeleted() throws IOException {
        VoiceMessageStore store = new VoiceMessageStore(folder.getRoot());
        File first = save(store);
        File second = save(store);
        long sameTime = 1_800_000_000_000L;
        assertTrue(first.setLastModified(sameTime));
        assertTrue(second.setLastModified(sameTime));

        List<File> recordings = store.list();
        assertEquals(2, recordings.size());
        assertNotEquals(recordings.get(0).getName(), recordings.get(1).getName());
        File selected = recordings.get(0);
        File remaining = recordings.get(1);
        assertTrue(store.delete(selected));
        assertEquals(1, store.list().size());
        assertEquals(remaining, store.list().get(0));
    }

    private static File save(VoiceMessageStore store) throws IOException {
        File draft = store.begin();
        try (FileOutputStream output = new FileOutputStream(draft)) {
            output.write(42);
        }
        return store.save(draft);
    }
}
