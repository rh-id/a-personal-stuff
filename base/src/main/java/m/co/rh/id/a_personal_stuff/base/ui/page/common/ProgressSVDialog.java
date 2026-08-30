package m.co.rh.id.a_personal_stuff.base.ui.page.common;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.Serializable;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import m.co.rh.id.a_personal_stuff.base.R;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.RequireNavRoute;

/**
 * Non-cancellable progress dialog showing a title, a message and an
 * indeterminate {@link android.widget.ProgressBar}.
 * <p>
 * Unlike the result-based dialogs in a-navigator-extension-dialog
 * ({@code MessageSVDialog}/{@code ConfirmSVDialog}), this dialog does not wait
 * for user input. The caller is responsible for popping it when its async work
 * completes (typically via {@code navigator.pop()} from a {@code doFinally}).
 * It is intentionally task-agnostic so it can be reused for any background
 * operation that needs a blocking progress indicator. The message can stream
 * live updates by pushing them to {@link Args#getProgressSubject()}.
 */
public class ProgressSVDialog extends StatefulViewDialog<Activity> implements RequireNavRoute {

    @NavInject
    private transient NavRoute mNavRoute;
    private transient Disposable mMessageDisposable;

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.sv_progress_dialog, container, false);
        TextView textTitle = rootLayout.findViewById(R.id.text_title);
        TextView textMessage = rootLayout.findViewById(R.id.text_message);
        Args args = Args.of(mNavRoute);
        if (args != null) {
            textTitle.setText(args.mTitle);
            String current = args.mProgressSubject.getValue();
            if (current != null) {
                textMessage.setText(current);
            } else {
                textMessage.setVisibility(View.GONE);
            }
            if (args.mTitle == null) {
                textTitle.setVisibility(View.GONE);
            }
            if (mMessageDisposable != null) {
                mMessageDisposable.dispose();
            }
            mMessageDisposable = args.mProgressSubject.getSubject()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(message -> {
                        textMessage.setVisibility(View.VISIBLE);
                        textMessage.setText(message);
                    });
        } else {
            textTitle.setVisibility(View.GONE);
            textMessage.setVisibility(View.GONE);
        }
        return rootLayout;
    }

    @Override
    protected Dialog createDialog(Activity activity) {
        Dialog dialog = super.createDialog(activity);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mMessageDisposable != null) {
            mMessageDisposable.dispose();
            mMessageDisposable = null;
        }
    }

    public static class Args implements Serializable {
        public static Args newArgs(String title, String message) {
            Args args = new Args();
            args.mTitle = title;
            if (message != null) {
                args.mProgressSubject.onNext(message);
            }
            return args;
        }

        public static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteArgs());
            }
            return null;
        }

        public static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }

        public SerialBehaviorSubject<String> getProgressSubject() {
            return mProgressSubject;
        }

        private String mTitle;
        private final SerialBehaviorSubject<String> mProgressSubject = new SerialBehaviorSubject<>();
    }
}
