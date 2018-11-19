package org.schabi.newpipe.downloader.get.sqlite;

import org.schabi.newpipe.downloader.get.DownloadMissionImpl;

import java.util.List;

/**
 * Provides access to the storage of {@link DownloadMissionImpl}s
 */
public interface DownloadDataSource {

    /**
     * Load all missions
     *
     * @return a list of download missions
     */
    List<DownloadMissionImpl> loadMissions();

    /**
     * Add a download mission to the storage
     *
     * @param downloadMission the download mission to add
     * @return the identifier of the mission
     */
    void addMission(DownloadMissionImpl downloadMission);

    /**
     * Update a download mission which exists in the storage
     *
     * @param downloadMission the download mission to update
     * @throws IllegalArgumentException if the mission was not added to storage
     */
    void updateMission(DownloadMissionImpl downloadMission);


    /**
     * Delete a download mission
     *
     * @param downloadMission the mission to delete
     */
    void deleteMission(DownloadMissionImpl downloadMission);
}