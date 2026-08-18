package m.co.rh.id.a_personal_stuff.item_checklist.provider;

import m.co.rh.id.a_personal_stuff.item_checklist.provider.component.ItemChecklistEventHandler;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.notifier.ItemChecklistChangeNotifier;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemChecklistProviderModule implements ProviderModule {
    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new ItemChecklistDatabaseProviderModule());
        providerRegistry.registerLazy(ItemChecklistChangeNotifier.class, ItemChecklistChangeNotifier::new);
        providerRegistry.registerAsync(ItemChecklistEventHandler.class, () -> new ItemChecklistEventHandler(provider));
    }
}
