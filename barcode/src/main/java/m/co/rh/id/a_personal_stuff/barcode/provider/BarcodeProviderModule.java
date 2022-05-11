package m.co.rh.id.a_personal_stuff.barcode.provider;

import m.co.rh.id.a_personal_stuff.barcode.ui.NavBarcodeConfig;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class BarcodeProviderModule implements ProviderModule {
    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerLazy(NavBarcodeConfig.class, () -> new NavBarcodeConfig(provider.getContext()));
    }

    @Override
    public void dispose(Provider provider) {
        // Leave blank
    }
}
