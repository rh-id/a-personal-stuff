package m.co.rh.id.a_personal_stuff.item_checklist.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.item_checklist.R;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.component.ItemChecklistService;
import m.co.rh.id.aprovider.Provider;

public class NewItemChecklistCmd {
    protected Context mContext;
    protected ExecutorService mExecutorService;
    protected ItemChecklistService mItemChecklistService;

    protected BehaviorSubject<String> mTitleValidSubject;
    protected Subject<String> mTitleValidEmitter;

    public NewItemChecklistCmd(Provider provider) {
        mContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mItemChecklistService = provider.get(ItemChecklistService.class);
        mTitleValidSubject = BehaviorSubject.create();
        mTitleValidEmitter = mTitleValidSubject.toSerialized();
    }

    public Single<ItemChecklistState> execute(ItemChecklistState itemChecklistState) {
        // the insert + checklistAdded notification is shared checklist domain
        // logic (the app-level shopping-list orchestrator creates the same
        // designated checklist), so it lives in ItemChecklistService
        return Single.fromCallable(() ->
                        mItemChecklistService.createChecklist(itemChecklistState))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    public boolean valid(ItemChecklistState itemChecklistState) {
        boolean valid = false;
        if (itemChecklistState != null) {
            boolean titleValid;
            String title = itemChecklistState.getTitle();
            if (title != null && !title.trim().isEmpty()) {
                titleValid = true;
                mTitleValidEmitter.onNext("");
            } else {
                titleValid = false;
                mTitleValidEmitter.onNext(mContext.getString(R.string.checklist_title_required));
            }
            valid = titleValid;
        }
        return valid;
    }

    public String getValidationError() {
        String titleValid = mTitleValidSubject.getValue();
        if (titleValid != null && !titleValid.isEmpty()) {
            return titleValid;
        }
        return "";
    }

    public Flowable<String> getTitleValidFlow() {
        return Flowable.fromObservable(mTitleValidEmitter, BackpressureStrategy.BUFFER);
    }
}
