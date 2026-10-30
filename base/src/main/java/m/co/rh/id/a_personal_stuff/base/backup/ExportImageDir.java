package m.co.rh.id.a_personal_stuff.base.backup;

import java.io.File;
import java.util.Set;

/**
 * One image directory a module contributes to the backup zip: only files
 * whose name is in {@code referencedFileNames} are zipped under
 * {@code entryPrefix/} from {@code parentDir}.
 */
public class ExportImageDir {

    private final String mEntryPrefix;
    private final File mParentDir;
    private final Set<String> mReferencedFileNames;

    public ExportImageDir(String entryPrefix, File parentDir, Set<String> referencedFileNames) {
        mEntryPrefix = entryPrefix;
        mParentDir = parentDir;
        mReferencedFileNames = referencedFileNames;
    }

    public String getEntryPrefix() {
        return mEntryPrefix;
    }

    public File getParentDir() {
        return mParentDir;
    }

    public Set<String> getReferencedFileNames() {
        return mReferencedFileNames;
    }
}
