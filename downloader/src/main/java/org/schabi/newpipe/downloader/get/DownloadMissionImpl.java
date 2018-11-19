package org.schabi.newpipe.downloader.get;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.util.Log;

import org.schabi.newpipe.downloader.DownloadMission;
import org.schabi.newpipe.downloader.util.Utility;

import java.io.File;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.schabi.newpipe.downloader.BuildConfig.DEBUG;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class DownloadMissionImpl implements Serializable, DownloadMission {
    private static final HashMap<MissionListener, Handler> HANDLER_STORE = new HashMap<>();

    private static final long serialVersionUID = 0L;

    private static final String TAG = DownloadMissionImpl.class.getSimpleName();

    static final int ERROR_SERVER_UNSUPPORTED = 206;
    static final int ERROR_UNKNOWN = 233;

    /**
     * The filename
     */
    private String name;

    /**
     * The url of the file to download
     */
    private String url;

    /**
     * The directory to store the download
     */
    private String location;

    /**
     * Number of blocks the size of {@link DownloadManager#BLOCK_SIZE}
     */
    long blocks;

    /**
     * Number of bytes
     */
    long length;

    /**
     * Number of bytes downloaded
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public long done;

    int threadCount = 3;
    private int finishCount;
    private final List<Long> threadPositions = new ArrayList<>();
    private final Map<Long, Boolean> blockState = new HashMap<>();
    public boolean running;
    public boolean finished;
    boolean fallback;
    public int errCode = -1;
    public long timestamp;

    transient boolean recovered;

    private transient ArrayList<WeakReference<MissionListener>> mListeners = new ArrayList<>();
    private transient boolean mWritingToFile;

    private static final int NO_IDENTIFIER = -1;

    public DownloadMissionImpl() {
    }

    public DownloadMissionImpl(String name, String url, String location) {
        if (name == null) throw new NullPointerException("name is null");
        if (name.isEmpty()) throw new IllegalArgumentException("name is empty");
        if (url == null) throw new NullPointerException("url is null");
        if (url.isEmpty()) throw new IllegalArgumentException("url is empty");
        if (location == null) throw new NullPointerException("location is null");
        if (location.isEmpty()) throw new IllegalArgumentException("location is empty");
        this.url = url;
        this.name = name;
        this.location = location;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean hasFinished() {
        return finished;
    }

    @Override
    public long getDone() {
        return done;
    }

    @Override
    public int getErrorCode() {
        return errCode;
    }

    private void checkBlock(long block) {
        if (block < 0 || block >= blocks) {
            throw new IllegalArgumentException("illegal block identifier");
        }
    }

    /**
     * Check if a block is reserved
     *
     * @param block the block identifier
     * @return true if the block is reserved and false if otherwise
     */
    boolean isBlockPreserved(long block) {
        checkBlock(block);
        return blockState.containsKey(block) ? blockState.get(block) : false;
    }

    void preserveBlock(long block) {
        checkBlock(block);
        synchronized (blockState) {
            blockState.put(block, true);
        }
    }

    /**
     * Set the download position of the file
     *
     * @param threadId the identifier of the thread
     * @param position the download position of the thread
     */
    void setPosition(int threadId, long position) {
        threadPositions.set(threadId, position);
    }

    /**
     * Get the position of a thread
     *
     * @param threadId the identifier of the thread
     * @return the position for the thread
     */
    long getPosition(int threadId) {
        return threadPositions.get(threadId);
    }

    synchronized void notifyProgress(long deltaLen) {
        if (!running) return;

        if (recovered) {
            recovered = false;
        }

        done += deltaLen;

        if (done > length) {
            done = length;
        }

        if (done != length) {
            writeThisToFile();
        }

        for (WeakReference<MissionListener> ref : mListeners) {
            final MissionListener listener = ref.get();
            if (listener != null) {
                HANDLER_STORE.get(listener).post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onProgressUpdate(DownloadMissionImpl.this, done, length);
                    }
                });
            }
        }
    }

    /**
     * Called by a download thread when it finished.
     */
    synchronized void notifyFinished() {
        if (errCode > 0) return;

        finishCount++;

        if (finishCount == threadCount) {
            onFinish();
        }
    }

    /**
     * Called when all parts are downloaded
     */
    private void onFinish() {
        if (errCode > 0) return;

        if (DEBUG) {
            Log.d(TAG, "onFinish");
        }

        running = false;
        finished = true;

        deleteThisFromFile();

        for (WeakReference<MissionListener> ref : mListeners) {
            final MissionListener listener = ref.get();
            if (listener != null) {
                HANDLER_STORE.get(listener).post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onFinish(DownloadMissionImpl.this);
                    }
                });
            }
        }
    }

    synchronized void notifyError(int err) {
        errCode = err;

        writeThisToFile();

        for (WeakReference<MissionListener> ref : mListeners) {
            final MissionListener listener = ref.get();
            HANDLER_STORE.get(listener).post(new Runnable() {
                @Override
                public void run() {
                    listener.onError(DownloadMissionImpl.this, errCode);
                }
            });
        }
    }

    public synchronized void addListener(@NonNull MissionListener listener) {
        Handler handler = new Handler(Looper.getMainLooper());
        HANDLER_STORE.put(listener, handler);
        mListeners.add(new WeakReference<>(listener));
    }

    public synchronized void removeListener(@NonNull MissionListener listener) {
        for (Iterator<WeakReference<MissionListener>> iterator = mListeners.iterator();
             iterator.hasNext(); ) {
            WeakReference<MissionListener> weakRef = iterator.next();
            if (listener == weakRef.get()) {
                iterator.remove();
                HANDLER_STORE.remove(listener);
            }
        }
    }

    /**
     * Start downloading with multiple threads.
     */
    public void start() {
        if (!running && !finished) {
            running = true;

            if (!fallback) {
                for (int i = 0; i < threadCount; i++) {
                    if (threadPositions.size() <= i && !recovered) {
                        threadPositions.add((long) i);
                    }
                    new Thread(new DownloadRunnable(this, i)).start();
                }
            } else {
                // In fallback mode, resuming is not supported.
                threadCount = 1;
                done = 0;
                blocks = 0;
                new Thread(new DownloadRunnableFallback(this)).start();
            }
        }
    }

    public void pause() {
        if (running) {
            running = false;
            recovered = true;

            // TODO: Notify & Write state to info file
            // if (err)
        }
    }

    /**
     * Removes the file and the meta file
     */
    public void delete() {
        deleteThisFromFile();
        new File(location, name).delete();
    }

    /**
     * Write this {@link DownloadMissionImpl} to the meta file asynchronously
     * if no thread is already running.
     */
    private void writeThisToFile() {
        if (!mWritingToFile) {
            mWritingToFile = true;
            new Thread() {
                @Override
                public void run() {
                    doWriteThisToFile();
                    mWritingToFile = false;
                }
            }.start();
        }
    }

    /**
     * Write this {@link DownloadMissionImpl} to the meta file.
     */
    private void doWriteThisToFile() {
        synchronized (blockState) {
            Utility.writeToFile(getMetaFilename(), this);
        }
    }

    private void readObject(ObjectInputStream inputStream)
            throws java.io.IOException, ClassNotFoundException {
        inputStream.defaultReadObject();
        mListeners = new ArrayList<>();
    }

    private void deleteThisFromFile() {
        new File(getMetaFilename()).delete();
    }

    /**
     * Get the path of the meta file
     *
     * @return the path to the meta file
     */
    private String getMetaFilename() {
        return location + "/" + name + ".giga";
    }

    File getDownloadedFile() {
        return new File(location, name);
    }

}
