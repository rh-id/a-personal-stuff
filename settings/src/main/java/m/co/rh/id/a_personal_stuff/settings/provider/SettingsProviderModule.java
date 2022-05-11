package m.co.rh.id.a_personal_stuff.settings.provider;

import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class SettingsProviderModule implements ProviderModule {
    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerAsync(SettingsSharedPreferences.class, () -> new SettingsSharedPreferences(provider));
    }

    @Override
    public void dispose(Provider provider) {
        // Leave blank
    }
}
