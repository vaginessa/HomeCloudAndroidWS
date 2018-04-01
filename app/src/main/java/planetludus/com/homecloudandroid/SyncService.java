package planetludus.com.homecloudandroid;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Synchronization service
 *
 * @author Pablo Carnero
 */
public class SyncService extends JobService {

    private final static String TAG = "SyncService";

    private String ip;
    private String port;
    private String id;
    private String password;

    @Override
    public void onCreate() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        ip = prefs.getString(getString(R.string.pref_key_ip), "127.0.0.1");
        port = prefs.getString(getString(R.string.pref_key_port), "3999");
        id = prefs.getString(getString(R.string.pref_key_id), "anonymous");
        password = prefs.getString(getString(R.string.pref_key_password), "");

        super.onCreate();
    }

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        Log.d(TAG, "onStartJob");

        // send the files in background
        SendFilesTask sendFilesTask = new SendFilesTask(this);
        sendFilesTask.execute(ip, port, id, password);

        jobFinished(jobParameters, true);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.d(TAG, "onStopJob");
        return true;
    }
}
