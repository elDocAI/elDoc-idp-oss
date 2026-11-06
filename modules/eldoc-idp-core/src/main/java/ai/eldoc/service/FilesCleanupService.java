/*
 *
 *  * Copyright (C) 2022-2026 elDoc Limited
 *  *
 *  * This file is part of elDoc IDP OSS.
 *  *
 *  * elDoc IDP OSS is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU Affero General Public License as published
 *  * by the Free Software Foundation, version 3.
 *  *
 *  * elDoc IDP OSS is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero
 *  * General Public License for more details.
 *  *
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *
 */
package ai.eldoc.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static ai.eldoc.util.FileUtils.deleteQuietly;


/**
 * Files Cleanup Services<br>
 * Provides functionality for temporary files queue handling and removal
 */
@ApplicationScoped
public class FilesCleanupService {

    private static final Logger LOG = LoggerFactory.getLogger(FilesCleanupService.class);

    private final DelayQueue<DelayedPath> _filesQueue;
    private final AtomicBoolean _busy;


    //
    // Constructors -------------------------------------------------------------------------------
    //
    public FilesCleanupService() {
        _filesQueue = new DelayQueue<>();
        _busy = new AtomicBoolean(false);
    }


    //
    // Methods ------------------------------------------------------------------------------------
    //
    /**
     * Adds path to file on the file-system for clean-up
     *
     * @param item
     * @return
     */
    public boolean addQueueItem(Path item) {
        return _filesQueue.add(new DelayedPath(item));
    }


    /**
     * Adds path to file on the file-system for clean-up after the given delay
     *
     * @param item
     * @param delayInHours delay in hours when file will become ready for clean-up
     * @return
     */
    public boolean addQueueItem(Path item, long delayInHours) {
        return _filesQueue.add(new DelayedPath(item, TimeUnit.MILLISECONDS.convert(delayInHours, TimeUnit.HOURS)));
    }


    public void processQueue() {
        processQueueInner();
    }


    private void processQueueInner() {
        LOG.trace("Files cleanup queue processing START");
        if (!_busy.compareAndSet(false, true)) {
            LOG.debug("Files cleanup queue already running, skipping request to trigger launch");
            return;
        }
        DelayedPath fileToBeRemoved = _filesQueue.poll();
        while (fileToBeRemoved != null) {
            try {
                if (Files.exists(fileToBeRemoved.getPath())) {
                    // Check if file still exists (at may already gone what is acceptable)
                    if (deleteQuietly(fileToBeRemoved.getPath())) {
                        LOG.trace("Temp file deleted: {}", fileToBeRemoved.getPath().toAbsolutePath().toString());
                    } else {
                        LOG.warn("Cannot delete temp file: {}", fileToBeRemoved.getPath().toAbsolutePath().toString());
                    }
                } else {
                    LOG.trace("Temp file not exists: {}", fileToBeRemoved.getPath().toAbsolutePath().toString());
                }
            } catch (SecurityException se) {
                LOG.warn("Error checking if file exists ({}), skipping temp file deletion: {}", fileToBeRemoved.getPath(), se.getMessage());
            } catch (Exception e) {
                LOG.warn("Error occured while processing temporary file from the queue, skipping temp file deletion for the entry: {}", fileToBeRemoved.getPath(), e);
            }
            fileToBeRemoved = _filesQueue.poll();
        }
        _busy.set(false);
        LOG.trace("Files cleanup queue processing END");
    }


    public static FilesCleanupService getInstance() {
        return CDI.current().select(FilesCleanupService.class).get();
    }


    //
    // Inner Classes ------------------------------------------------------------------------------
    //
    /**
     * DelayedPath Model Class
     */
    private static final class DelayedPath implements Delayed, Serializable {

        private static final long serialVersionUID = -7875562917397231182L;

        private final Path _path;
        private final long _readyTimestamp;


        //
        // Constructors ---------------------------------------------------------------------------
        //
        public DelayedPath(Path path) {
            this(path, 0);
        }


        public DelayedPath(Path path, long delayInMillis) {
            _path = path;
            _readyTimestamp = System.currentTimeMillis() + delayInMillis;
        }


        //
        // Methods --------------------------------------------------------------------------------
        //
        @Override
        public int compareTo(Delayed o) {
            return Long.compare(_readyTimestamp, ((DelayedPath) o)._readyTimestamp);
        }


        @Override
        public long getDelay(TimeUnit unit) {
            long diff = _readyTimestamp - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }


        //
        // Getters --------------------------------------------------------------------------------
        //
        public Path getPath() {
            return _path;
        }

    }

}
