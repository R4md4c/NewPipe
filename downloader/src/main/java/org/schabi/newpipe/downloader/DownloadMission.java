package org.schabi.newpipe.downloader;

import android.support.annotation.NonNull;

public interface DownloadMission {

    /**
     * @return The file name
     */
    String getName();

    /**
     * @return The url of the file to download.
     */
    String getUrl();

    /**
     * @return Number of bytes in that mission.
     */
    long getLength();

    /**
     * @return Number of bytes that are already downloaded.
     */
    long getDone();

    /**
     * @return The directory to store the download
     */
    String getLocation();

    /**
     * @return Whether this mission is running or not.
     */
    boolean isRunning();

    /**
     * @return Whether this mission has finished or not.
     */
    boolean hasFinished();

    /**
     * @return the Error code of that mission if there was any, otherwise -1 if there was none.
     */
    int getErrorCode();

    /**
     * Adds a listener to that mission.
     *
     * @param listener that will listen for this mission's updates.
     */
    void addListener(@NonNull MissionListener listener);

    /**
     * Removes the listener from that mission.
     *
     * @param listener to be removed.
     */
    void removeListener(@NonNull MissionListener listener);


    interface MissionListener {

        /**
         * Gets called each time a mission has a progress update.
         *
         * @param downloadMission The download mission that has this update.
         * @param done            the amount of bytes that are already downloaded.
         * @param total           the total number of bytes that are expected from this mission.
         */
        void onProgressUpdate(@NonNull DownloadMission downloadMission, long done, long total);

        /**
         * Gets called when the mission finishes downloading.
         *
         * @param downloadMission the mission that got downloaded.
         */
        void onFinish(@NonNull DownloadMission downloadMission);

        /**
         * Called when an error happens to that mission.
         *
         * @param downloadMission the mission that got this error.
         * @param errCode         the error code that this mission received.
         */
        void onError(@NonNull DownloadMission downloadMission, int errCode);
    }
}
