package org.schabi.newpipe.downloader.get;

import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.schabi.newpipe.downloader.get.sqlite.DownloadDataSource;
import org.schabi.newpipe.downloader.util.IntentsProvider;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link DownloadManagerImpl}
 * <p>
 * TODO: test loading from .giga files, startMission and improve tests
 */
public class DownloadManagerImplTest {

    private DownloadManagerImpl downloadManager;
    private ArrayList<DownloadMissionImpl> missions;

    @Mock
    private DownloadDataSource downloadDataSource;

    @Mock
    private IntentsProvider intentsProvider;

    @org.junit.Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        missions = new ArrayList<>();
        for (int i = 0; i < 50; ++i) {
            missions.add(generateFinishedDownloadMission());
        }
        when(downloadDataSource.loadMissions()).thenReturn(new ArrayList<>(missions));
        downloadManager = new DownloadManagerImpl(new ArrayList<>(), intentsProvider, downloadDataSource);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorWithNullAsDownloadDataSource() {
        new DownloadManagerImpl(new ArrayList<>(), intentsProvider, null);
    }


    private static DownloadMissionImpl generateFinishedDownloadMission() throws IOException {
        File file = File.createTempFile("newpipetest", ".mp4");
        file.deleteOnExit();
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        randomAccessFile.setLength(1000);
        randomAccessFile.close();
        DownloadMissionImpl downloadMission = new DownloadMissionImpl(file.getName(),
                "http://google.com/?q=how+to+google", file.getParent());
        downloadMission.blocks = 1000;
        downloadMission.done = 1000;
        downloadMission.finished = true;
        return spy(downloadMission);
    }

    private static void assertMissionEquals(String message, DownloadMissionImpl expected, DownloadMissionImpl actual) {
        if (expected == actual) return;
        assertEquals(message + ": Name", expected.getName(), actual.getName());
        assertEquals(message + ": Location", expected.getLocation(), actual.getLocation());
        assertEquals(message + ": Url", expected.getUrl(), actual.getUrl());
    }

    @Test
    public void testThatMissionsAreLoaded() throws IOException {
        ArrayList<DownloadMissionImpl> missions = new ArrayList<>();
        long millis = System.currentTimeMillis();
        for (int i = 0; i < 50; ++i) {
            DownloadMissionImpl mission = generateFinishedDownloadMission();
            mission.timestamp = millis - i; // reverse order by timestamp
            missions.add(mission);
        }

        downloadDataSource = mock(DownloadDataSource.class);
        when(downloadDataSource.loadMissions()).thenReturn(new ArrayList<>(missions));
        downloadManager = new DownloadManagerImpl(new ArrayList<>(), intentsProvider, downloadDataSource);
        verify(downloadDataSource, times(1)).loadMissions();

        assertEquals(50, downloadManager.getCount());

        for (int i = 0; i < 50; ++i) {
            assertMissionEquals("mission " + i, missions.get(50 - 1 - i), downloadManager.getMission(i));
        }
    }

    @Ignore
    @Test
    public void startMission() throws Exception {
        DownloadMissionImpl mission = missions.get(0);
        mission = spy(mission);
        missions.set(0, mission);
        String url = "https://github.com/favicon.ico";
        // create a temp file and delete it so we have a temp directory
        File tempFile = File.createTempFile("favicon", ".ico");
        String name = tempFile.getName();
        String location = tempFile.getParent();
        assertTrue(tempFile.delete());
        int id = downloadManager.startMission(url, location, name, true, 10);
    }

    @Test
    public void resumeMission() {
        DownloadMissionImpl mission = missions.get(0);
        mission.running = true;
        verify(mission, never()).start();
        downloadManager.resumeMission(0);
        verify(mission, never()).start();
        mission.running = false;
        downloadManager.resumeMission(0);
        verify(mission, times(1)).start();
    }

    @Test
    public void pauseMission() {
        DownloadMissionImpl mission = missions.get(0);
        mission.running = false;
        downloadManager.pauseMission(0);
        verify(mission, never()).pause();
        mission.running = true;
        downloadManager.pauseMission(0);
        verify(mission, times(1)).pause();
    }

    @Test
    public void deleteMission() {
        DownloadMissionImpl mission = missions.get(0);
        assertEquals(mission, downloadManager.getMission(0));
        downloadManager.deleteMission(0);
        verify(mission, times(1)).delete();
        assertNotEquals(mission, downloadManager.getMission(0));
        assertEquals(49, downloadManager.getCount());
    }

    @Test(expected = RuntimeException.class)
    public void getMissionWithNegativeIndex() {
        downloadManager.getMission(-1);
    }

    @Test
    public void getMission() {
        assertSame(missions.get(0), downloadManager.getMission(0));
        assertSame(missions.get(1), downloadManager.getMission(1));
    }

    @Test
    public void sortByTimestamp() {
        ArrayList<DownloadMissionImpl> downloadMissions = new ArrayList<>();
        DownloadMissionImpl mission = new DownloadMissionImpl();
        mission.timestamp = 0;

        DownloadMissionImpl mission1 = new DownloadMissionImpl();
        mission1.timestamp = Integer.MAX_VALUE + 1L;

        DownloadMissionImpl mission2 = new DownloadMissionImpl();
        mission2.timestamp = 2L * Integer.MAX_VALUE;

        DownloadMissionImpl mission3 = new DownloadMissionImpl();
        mission3.timestamp = 2L * Integer.MAX_VALUE + 5L;


        downloadMissions.add(mission3);
        downloadMissions.add(mission1);
        downloadMissions.add(mission2);
        downloadMissions.add(mission);


        DownloadManagerImpl.sortByTimestamp(downloadMissions);

        assertEquals(mission, downloadMissions.get(0));
        assertEquals(mission1, downloadMissions.get(1));
        assertEquals(mission2, downloadMissions.get(2));
        assertEquals(mission3, downloadMissions.get(3));
    }

}