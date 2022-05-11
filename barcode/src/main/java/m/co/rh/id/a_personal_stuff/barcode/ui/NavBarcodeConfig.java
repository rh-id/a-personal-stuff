package m.co.rh.id.a_personal_stuff.barcode.ui;

import android.content.Context;

import java.util.LinkedHashMap;
import java.util.Map;

import m.co.rh.id.a_personal_stuff.barcode.R;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.component.StatefulViewFactory;

@SuppressWarnings("rawtypes")
public class NavBarcodeConfig {
    private static final String ROUTE_SCAN_BARCODE = "ROUTE_SCAN_BARCODE";

    private Map<String, String> mRouteMap;
    private Map<String, StatefulViewFactory> mNavMap;

    public NavBarcodeConfig(Context context) {
        mRouteMap = new LinkedHashMap<>();
        String scanBarcode = context.getString(R.string.barcode_route_scan_barcode_page);
        mRouteMap.put(ROUTE_SCAN_BARCODE, scanBarcode);

        mNavMap = new LinkedHashMap<>();
        mNavMap.put(scanBarcode, (args, activity) -> new ScanBarcodePage());
    }

    public Map<String, StatefulViewFactory> getNavMap() {
        return mNavMap;
    }

    public String route_scanBarcodePage() {
        return mRouteMap.get(ROUTE_SCAN_BARCODE);
    }

    public String result_scanBarcodePage_barcode(NavRoute navRoute) {
        ScanBarcodePage.Result result = ScanBarcodePage.Result.of(navRoute);
        if (result != null) {
            return result.getBarcode();
        }
        return null;
    }
}
