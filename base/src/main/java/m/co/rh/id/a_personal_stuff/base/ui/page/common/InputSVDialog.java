package m.co.rh.id.a_personal_stuff.base.ui.page.common;

import android.app.Activity;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.Serializable;

import m.co.rh.id.a_personal_stuff.base.R;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.RequireNavRoute;

/**
 * Routed text-input dialog following the a-navigator-extension-dialog patterns
 * (like ConfirmSVDialog) so it survives configuration changes as a nav route.
 * Pops with {@link Result} when OK is pressed, null when cancelled or dismissed.
 */
public class InputSVDialog extends StatefulViewDialog<Activity> implements RequireNavRoute, View.OnClickListener {

    @NavInject
    private transient NavRoute mNavRoute;

    private String mText;

    private transient TextWatcher mTextWatcher;

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.sv_input_dialog, container, false);
        TextView textTitle = rootLayout.findViewById(R.id.text_title);
        TextInputLayout inputLayout = rootLayout.findViewById(R.id.input_layout);
        TextInputEditText editText = rootLayout.findViewById(R.id.edit_text_input);
        Args args = Args.of(mNavRoute);
        if (args != null) {
            textTitle.setText(args.mTitle);
            inputLayout.setHint(args.mHint);
            editText.setText(mText != null ? mText : args.mText);
            if (args.mMultiline) {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                editText.setMaxLines(6);
            }
        }
        mTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                mText = editable.toString();
            }
        };
        editText.addTextChangedListener(mTextWatcher);
        Button buttonOk = rootLayout.findViewById(R.id.button_ok);
        buttonOk.setOnClickListener(this);
        Button buttonCancel = rootLayout.findViewById(R.id.button_cancel);
        buttonCancel.setOnClickListener(this);
        return rootLayout;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_ok) {
            getNavigator().pop(Result.withResult(mText));
        } else if (id == R.id.button_cancel) {
            getNavigator().pop(null);
        }
    }

    public static class Result implements Serializable {
        public static Result withResult(String text) {
            Result result = new Result();
            result.mText = text;
            return result;
        }

        public static Result of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteResult());
            }
            return null;
        }

        public static Result of(Serializable serializable) {
            if (serializable instanceof Result) {
                return (Result) serializable;
            }
            return null;
        }

        private String mText;

        public String getText() {
            return mText;
        }
    }

    public static class Args implements Serializable {
        public static Args newArgs(String title, String hint, String text) {
            return newArgs(title, hint, text, false);
        }

        public static Args newArgs(String title, String hint, String text, boolean multiline) {
            Args args = new Args();
            args.mTitle = title;
            args.mHint = hint;
            args.mText = text;
            args.mMultiline = multiline;
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

        private String mTitle;
        private String mHint;
        private String mText;
        private boolean mMultiline;
    }
}
