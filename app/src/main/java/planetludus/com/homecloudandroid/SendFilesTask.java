package planetludus.com.homecloudandroid;

import android.app.NotificationManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Task to send the files over the network in background
 *
 * @author Pablo Carnero
 */

public class SendFilesTask extends AsyncTask<String, Integer, Boolean> {

    private final static String TAG = "SendFilesTask";
    private final static SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Context context;

    public SendFilesTask(Context context) {
        super();
        this.context = context;
    }

    /**
     * Copy the files in background.
     *
     * @param params first param is ip
     *               second param is port
     *               third param is user id
     * @return a result, defined by the subclass of this task.
     */
    @Override
    protected Boolean doInBackground(String... params) {

        // input parameters
        if (params == null || params.length != 3) {
            Log.e(TAG, "doInBackground: Input param error: "
                    + params == null ? "Null" : String.valueOf(params.length));
            return false;
        }

        String ip = params[0];
        String port = params[1];
        String id = params[2];

        // initialize notifications
        NotificationManager mNotifyManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);
        mBuilder.setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_info_black_24dp);

         // perform the synchronization
        Log.d(TAG, "doInBackground: Establishing connection with " + ip + ":" + port);
        try (Socket socket = new Socket(ip, Integer.valueOf(port));
             InputStream in = socket.getInputStream();
             DataInputStream clientData = new DataInputStream(in);
             OutputStream out = socket.getOutputStream();
             DataOutputStream serverData = new DataOutputStream(out)) {

            Log.d(TAG, "doInBackground: Connection established.");

            Log.d(TAG, "doInBackground: Sending the client id.");
            serverData.writeUTF(id);

            Log.d(TAG, "doInBackground: Receiving the buffer size");
            int bufferSize = clientData.readInt();

            // get images, video and audio newer than last sync date
            Date lastSyncDate = dateFormatter.parse(clientData.readUTF());
            Log.d(TAG, "doInBackground: Receiving the last sync date: " + lastSyncDate);
            List<File> fileList = new ArrayList<>();
            fileList.addAll(getMediaFrom(lastSyncDate, MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
            fileList.addAll(getMediaFrom(lastSyncDate, MediaStore.Video.Media.EXTERNAL_CONTENT_URI));

            // update progress bar
            if (fileList.size() > 0){
                mBuilder.setProgress(fileList.size(), 0, false);
            }

            Log.d(TAG, "doInBackground: We are going to send " + fileList.size() + " files.");
            serverData.writeInt(fileList.size());

            MessageDigest digest = MessageDigest.getInstance("MD5");
            // send all the files
            for (File file : fileList) {
                Log.d(TAG, "doInBackground: Sending the file: " + file.getName() + " size: " + file.length());
                serverData.writeUTF(file.getName());
                serverData.writeLong(file.length());

                byte[] buffer = new byte[bufferSize];
                try (InputStream inFile = new FileInputStream(file)) {
                    int numRead;
                    while ((numRead = inFile.read(buffer)) > 0) {
                        out.write(buffer, 0, numRead);
                        digest.update(buffer, 0, numRead);
                    }
                    byte [] md5Bytes = digest.digest();
                    String md5 = convertHashToString(md5Bytes);
                    Log.d(TAG, "doInBackground: Sending the md5: " + md5);
                    serverData.writeUTF(md5);

                    // update progress bar
                    mBuilder.setProgress(fileList.size(), 1, false);
                }
            }

            // removes the progress bar
            if (fileList.size() > 0) {
                mBuilder.setContentText(context.getString(R.string.notification_sync_completed))
                        .setProgress(0, 0, false);
                mNotifyManager.notify(1, mBuilder.build());
            }

            return true;
        } catch (ConnectException ex) {
            Log.e(TAG, "ConnectException.", ex);
            mBuilder.setContentText(context.getString(R.string.notification_error_connection_error))
                    .setProgress(0, 0, false);
            mNotifyManager.notify(1, mBuilder.build());
            return false;
        } catch (Exception ex) {
            Log.e(TAG, "Error communicating with the service", ex);
            mBuilder.setContentText(context.getString(R.string.notification_error_generic))
                    .setProgress(0, 0, false);
            mNotifyManager.notify(1, mBuilder.build());
            return false;
        }
    }

    /**
     * Convert md5 hash to string
     *
     * @param md5Bytes md5 byte array
     * @return md5 string
     */
    private static String convertHashToString(byte[] md5Bytes) {
        String returnVal = "";
        for (int i = 0; i < md5Bytes.length; i++) {
            returnVal += Integer.toString(( md5Bytes[i] & 0xff ) + 0x100, 16).substring(1);
        }
        return returnVal.toUpperCase();
    }

    @Override
    protected void onPostExecute(Boolean aBoolean) {
        super.onPostExecute(aBoolean);
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
    }

    /**
     * Get the file list from folder newer than date
     *
     * @param date Min date of files to be get
     * @param uri media Uri, typically
     *            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
     *            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
     *            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
     * @return file list
     */
    private List<File> getMediaFrom(Date date, Uri uri) {

        String[] projection = { MediaStore.MediaColumns.DATA,
                                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                                MediaStore.Images.Media.DATE_MODIFIED};
        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);

        List<File> fileList = new ArrayList<>();
        int column_index_data = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
        while (cursor.moveToNext()) {
            String absolutePathOfImage = cursor.getString(column_index_data);
            File currentImage = new File(absolutePathOfImage);
            Date dateFile = new Date(currentImage.lastModified());
            if (dateFile.compareTo(date) > 0) {
                fileList.add(currentImage);
            }
        }

        return fileList;
    }

}
