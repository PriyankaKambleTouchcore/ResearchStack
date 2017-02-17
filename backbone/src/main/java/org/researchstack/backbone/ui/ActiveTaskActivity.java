package org.researchstack.backbone.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.Surface;
import android.view.WindowManager;

import org.researchstack.backbone.result.FileResult;
import org.researchstack.backbone.result.StepResult;
import org.researchstack.backbone.result.logger.DataLoggerManager;
import org.researchstack.backbone.step.Step;
import org.researchstack.backbone.step.active.ActiveStep;
import org.researchstack.backbone.step.active.CountdownStep;
import org.researchstack.backbone.task.Task;
import org.researchstack.backbone.ui.step.layout.ActiveStepLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by TheMDP on 2/8/17.
 *
 * The ActiveTaskActivity is responsible for displaying any task with an ActiveStepLayout
 * It will manage the DataLogger files that are created, make sure none of the dirty ones leak,
 * and make sure that they are correctly bundled and uploaded at the end
 */

public class ActiveTaskActivity extends ViewTaskActivity {

    private boolean isBackButtonEnabled;

    public static Intent newIntent(Context context, Task task) {
        Intent intent = new Intent(context, ActiveTaskActivity.class);
        intent.putExtra(EXTRA_TASK, task);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(savedInstanceState == null) {
            init();  // for the first time
        }
    }

    protected void init() {
        DataLoggerManager.initialize(this);
        DataLoggerManager.getInstance().deleteAllDirtyFiles();
    }

    @Override
    protected void discardResultsAndFinish() {
        if (currentStepLayout instanceof ActiveStepLayout) {
            ((ActiveStepLayout) currentStepLayout).forceStop();
        }
        DataLoggerManager.getInstance().deleteAllDirtyFiles();
        super.discardResultsAndFinish();
    }

    @Override
    protected void showStep(Step step, boolean alwaysReplaceView) {

        // compute back button status while currentStep is actually the previousStep at this point
        isBackButtonEnabled =
                (!(step instanceof ActiveStep) || (step instanceof CountdownStep)) &&
                 !(currentStep instanceof ActiveStep); // currentStep is one previously showing at this point
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(isBackButtonEnabled);
        }

        // ActiveSteps have a particular lifecycle to where they should not be re-created
        // unnecessarily, so if it already exists and is showing, then do not re-show the StepLayout
        if (!isStepAnAlreadyShowingActiveStep(step)) {
            super.showStep(step, alwaysReplaceView);
        }

        // Active steps lock screen on and orientation so that the view is not unnecessarily
        // destroyed and recreated while the data logger is recording
        if (step instanceof ActiveStep) {
            lockScreenOn();
            lockOrientation();
        } else {
            unlockScreenOn();
            unlockOrientation();
        }
    }

    @Override
    public void notifyStepOfBackPress() {
        // intercept and block any back buttons
        if (isBackButtonEnabled) {
            super.notifyStepOfBackPress();
        }
    }

    private boolean isStepAnAlreadyShowingActiveStep(Step step) {
        return step instanceof ActiveStep && step.equals(currentStep);
    }

    @Override
    public void setRequestedOrientation(int requestedOrientation) {
        super.setRequestedOrientation(requestedOrientation);
    }

    @Override
    protected void saveAndFinish() {
        taskResult.setEndDate(new Date());

        // Loop through and find all the FileResult files
        List<File> fileList = new ArrayList<>();
        Map<String, StepResult> stepResultMap = taskResult.getResults();
        for (String key : stepResultMap.keySet()) {
            StepResult stepResult = stepResultMap.get(key);
            if (stepResult != null) {
                Map resultMap = stepResult.getResults();
                if (resultMap != null) {
                    for (Object resultKey : resultMap.keySet()) {
                        Object value = resultMap.get(resultKey);
                        if (value != null && value instanceof FileResult) {
                            FileResult fileResult = (FileResult)value;
                            fileList.add(fileResult.getFile());
                        }
                    }
                }
            }
        }
        // Since we are now about to try and upload the files to the server, let's update their status
        // These files will now stick around until we have successfully uploaded them
        DataLoggerManager.getInstance().updateFileListToAttemptedUploadStatus(fileList);

        // TODO: this is not correct, since we need to know if it succeeded or not
        //DataProvider.getInstance().uploadTaskResult(this, taskResult);

        // TODO: move this to the successful/failure block of the upload web service call
        super.saveAndFinish();
    }

    /**
     * Active Steps lock screen to on so it can avoid any interruptions during data logging
     */
    private void lockScreenOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void unlockScreenOn() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * Active Steps lock orientation so it can avoid any interruptions during data logging
     */
    private void lockOrientation() {
        int orientation;
        int rotation = ((WindowManager) getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                break;
            case Surface.ROTATION_90:
                orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                break;
            case Surface.ROTATION_180:
                orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                break;
            default:
                orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                break;
        }
        setRequestedOrientation(orientation);
    }

    private void unlockOrientation() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }
}
