package org.schabi.newpipe.downloader.get;

import java.io.BufferedInputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

// Single-threaded fallback mode
public class DownloadRunnableFallback implements Runnable {
    private final DownloadMissionImpl mMission;
    //private int mId;

    public DownloadRunnableFallback(DownloadMissionImpl mission) {
        if (mission == null) throw new NullPointerException("mission is null");
        //mId = id;
        mMission = mission;
    }

    @Override
    public void run() {
        try {
            URL url = new URL(mMission.getUrl());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if (conn.getResponseCode() != 200 && conn.getResponseCode() != 206) {
                notifyError(DownloadMissionImpl.ERROR_SERVER_UNSUPPORTED);
            } else {
                RandomAccessFile f = new RandomAccessFile(mMission.getLocation() + "/" + mMission.getName(), "rw");
                f.seek(0);
                BufferedInputStream ipt = new BufferedInputStream(conn.getInputStream());
                byte[] buf = new byte[512];
                int len = 0;

                while ((len = ipt.read(buf, 0, 512)) != -1 && mMission.running) {
                    f.write(buf, 0, len);
                    notifyProgress(len);

                    if (Thread.interrupted()) {
                        break;
                    }

                }

                f.close();
                ipt.close();
            }
        } catch (Exception e) {
            notifyError(DownloadMissionImpl.ERROR_UNKNOWN);
        }

        if (mMission.errCode == -1 && mMission.running) {
            notifyFinished();
        }
    }

    private void notifyProgress(final long len) {
        synchronized (mMission) {
            mMission.notifyProgress(len);
        }
    }

    private void notifyError(final int err) {
        synchronized (mMission) {
            mMission.notifyError(err);
            mMission.pause();
        }
    }

    private void notifyFinished() {
        synchronized (mMission) {
            mMission.notifyFinished();
        }
    }
}
