package m.co.rh.id.a_personal_stuff.base.backup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One backup/export snapshot of a module: the JSON sections to merge into
 * backup.json plus the image directories (with their referenced file names)
 * to add to the backup zip.
 */
public class ModuleExport {

    private final List<JSONObject> mSections;
    private final List<ExportImageDir> mImageDirs;

    private ModuleExport(List<JSONObject> sections, List<ExportImageDir> imageDirs) {
        mSections = sections;
        mImageDirs = imageDirs;
    }

    public static ModuleExport ofSections(JSONObject... sections) {
        List<JSONObject> sectionList = new ArrayList<>();
        for (JSONObject section : sections) {
            sectionList.add(section);
        }
        return new ModuleExport(sectionList, new ArrayList<>());
    }

    public static ModuleExport ofSectionsAndImageDirs(List<JSONObject> sections,
                                                      List<ExportImageDir> imageDirs) {
        return new ModuleExport(sections, imageDirs);
    }

    /** Merges every section into {@code root}, throwing on duplicate keys. */
    public void mergeInto(JSONObject root) throws JSONException {
        for (JSONObject section : mSections) {
            JSONArray names = section.names();
            if (names == null) {
                continue;
            }
            for (int i = 0; i < names.length(); i++) {
                String key = names.getString(i);
                if (root.has(key)) {
                    throw new JSONException("Duplicate backup key: " + key);
                }
                root.put(key, section.get(key));
            }
        }
    }

    public List<ExportImageDir> getImageDirs() {
        return mImageDirs;
    }
}
