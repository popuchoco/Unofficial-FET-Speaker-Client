package community.speaker.client;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

final class VoiceMessageStore {
    static final long ABANDONED_DRAFT_TTL_MILLIS = 24 * 60 * 60 * 1000L;
    private static final String PREFIX = "message-";
    private static final String DRAFT = ".pending";
    private static final String RECORDING = ".m4a";
    private final File directory;

    VoiceMessageStore(File privateAppDirectory) {
        directory = new File(privateAppDirectory, "voice_messages");
    }

    File begin() throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Cannot create private voice message directory");
        }
        return File.createTempFile(PREFIX, DRAFT, directory);
    }

    File save(File draft) throws IOException {
        if (!isMessageFile(draft, DRAFT) || !draft.isFile() || draft.length() == 0) {
            throw new IOException("Recording is missing or empty");
        }
        String basename = draft.getName().substring(0, draft.getName().length() - DRAFT.length());
        File saved = new File(directory, basename + RECORDING);
        if (saved.exists() || !draft.renameTo(saved)) throw new IOException("Cannot save voice message");
        return saved;
    }

    boolean discard(File draft) {
        return isMessageFile(draft, DRAFT) && (!draft.exists() || draft.delete());
    }

    boolean delete(File recording) {
        return isMessageFile(recording, RECORDING) && (!recording.exists() || recording.delete());
    }

    List<File> list() throws IOException {
        if (!directory.exists()) return java.util.Collections.emptyList();
        removeExpiredDrafts(System.currentTimeMillis());
        File[] saved = directory.listFiles(file -> file.isFile() && isMessageFile(file, RECORDING));
        if (saved == null) throw new IOException("Cannot list voice messages");
        Arrays.sort(saved, Comparator.comparingLong(File::lastModified).reversed().thenComparing(File::getName));
        return Arrays.asList(saved);
    }

    private void removeExpiredDrafts(long nowMillis) throws IOException {
        File[] drafts = directory.listFiles(file -> file.isFile() && isMessageFile(file, DRAFT)
                && nowMillis >= file.lastModified()
                && nowMillis - file.lastModified() >= ABANDONED_DRAFT_TTL_MILLIS);
        if (drafts == null) throw new IOException("Cannot clean voice message drafts");
        for (File draft : drafts) {
            if (!draft.delete() && draft.exists()) throw new IOException("Cannot remove abandoned voice message draft");
        }
    }

    private boolean isMessageFile(File file, String extension) {
        return file != null && directory.equals(file.getParentFile())
                && file.getName().startsWith(PREFIX) && file.getName().endsWith(extension);
    }
}
