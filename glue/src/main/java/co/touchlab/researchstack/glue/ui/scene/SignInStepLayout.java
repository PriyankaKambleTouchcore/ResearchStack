package co.touchlab.researchstack.glue.ui.scene;

import android.content.Context;
import android.support.v7.widget.AppCompatEditText;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jakewharton.rxbinding.view.RxView;

import co.touchlab.researchstack.core.result.StepResult;
import co.touchlab.researchstack.core.step.Step;
import co.touchlab.researchstack.core.ui.callbacks.SceneCallbacks;
import co.touchlab.researchstack.core.ui.step.layout.StepLayout;
import co.touchlab.researchstack.glue.ObservableUtils;
import co.touchlab.researchstack.glue.R;
import co.touchlab.researchstack.glue.ResearchStack;
import co.touchlab.researchstack.glue.ui.adapter.TextWatcherAdapter;

public class SignInStepLayout extends RelativeLayout implements StepLayout
{
    public static final String SIGNED_IN = "signedIn";
    private View progress;
    private AppCompatEditText  username;
    private AppCompatEditText  password;
    private TextView           forgotPassword;
    private Step               step;
    private StepResult<String> result;
    private SceneCallbacks     callbacks;

    public SignInStepLayout(Context context)
    {
        super(context);
    }

    public SignInStepLayout(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public SignInStepLayout(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void initialize(Step step, StepResult result)
    {
        this.step = step;
        this.result = result == null ? new StepResult<>(step.getIdentifier()) : result;

        View layout = LayoutInflater.from(getContext()).inflate(R.layout.item_sign_in, this, true);

        progress = layout.findViewById(R.id.progress);

        username = (AppCompatEditText) layout.findViewById(R.id.username);
        username.setText("walter@touchlab.co");
        username.addTextChangedListener(new TextWatcherAdapter()
        {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                if(! TextUtils.isEmpty(username.getError()))
                {
                    username.setError(null);
                }
            }
        });

        password = (AppCompatEditText) layout.findViewById(R.id.password);
        password.setText("password");
        password.addTextChangedListener(new TextWatcherAdapter()
        {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                if(! TextUtils.isEmpty(password.getError()))
                {
                    password.setError(null);
                }
            }
        });
        password.setOnEditorActionListener((v, actionId, event) -> {
            if((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) ||
                    (actionId == EditorInfo.IME_ACTION_DONE))
            {
                signIn();
                return true;
            }
            return false;
        });

        forgotPassword = (TextView) layout.findViewById(R.id.forgot_password);
        RxView.clicks(forgotPassword).subscribe(v -> {
            //TODO implement call on data provider
            Toast.makeText(getContext(), "TODO RESET PASSWORD", Toast.LENGTH_SHORT).show();
        });

        RxView.clicks(layout.findViewById(R.id.next)).subscribe(v -> {
            signIn();
        });
    }

    private void signIn()
    {
        if(isAnswerValid())
        {
            final String username = this.username.getText().toString();
            final String password = this.password.getText().toString();

            progress.animate().alpha(1).withStartAction(() -> {
                progress.setVisibility(View.VISIBLE);
                progress.setAlpha(0);
            }).withEndAction(() -> {
                ResearchStack.getInstance()
                        .getDataProvider()
                        .signIn(username, password)
                        .compose(ObservableUtils.applyDefault())
                        .subscribe(dataResponse -> {
                            // TODO figure out a better way to return the password if necessary
                            result.setResult(SIGNED_IN);
                            callbacks.onSaveStep(SceneCallbacks.ACTION_NEXT, step, result);
                        }, throwable -> {
                            progress.animate()
                                    .alpha(0)
                                    .withEndAction(() -> progress.setVisibility(View.GONE));

                            if(username.equals(ResearchStack.getInstance()
                                    .getDataProvider()
                                    .getUserEmail(getContext())))
                            {
                                // Sign in returns 404 if they haven't verified email. If the email
                                // matches the one they used to sign up, go to verification activity
                                // TODO figure out a better way to return the password if necessary
                                result.setResult(password);
                                callbacks.onSaveStep(SceneCallbacks.ACTION_NEXT, step, result);
                            }

                            // TODO Cast throwable to HttpException -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
                            // Convert errorBody to JSON-String, convert json-string to object
                            // (BridgeMessageResponse) and pass BridgeMessageResponse.getMessage()to
                            // toast
                            Toast.makeText(getContext(), throwable.getMessage(), Toast.LENGTH_SHORT)
                                    .show();
                        });
            });
        }
    }

    public boolean isAnswerValid()
    {
        if(! isEmailValid())
        {
            username.setError(getResources().getString(R.string.error_invalid_email));
        }

        if(! isPasswordValid())
        {
            password.setError(getResources().getString(R.string.error_invalid_password));
        }

        return TextUtils.isEmpty(username.getError()) && TextUtils.isEmpty(password.getError());
    }

    public boolean isEmailValid()
    {
        CharSequence target = username.getText();
        return ! TextUtils.isEmpty(target) &&
                android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches();
    }

    public boolean isPasswordValid()
    {
        CharSequence target = password.getText();
        return ! TextUtils.isEmpty(target);
    }

    @Override
    public View getLayout()
    {
        return this;
    }

    @Override
    public boolean isBackEventConsumed()
    {
        callbacks.onSaveStep(SceneCallbacks.ACTION_PREV, step, result);
        return false;
    }

    @Override
    public void setCallbacks(SceneCallbacks callbacks)
    {
        this.callbacks = callbacks;
    }

}
