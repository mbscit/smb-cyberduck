package ch.cyberduck.core.local;

/*
 *  Copyright (c) 2009 David Kocher. All rights reserved.
 *  http://cyberduck.ch/
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Bug fixes, suggestions and comments should be sent to:
 *  dkocher@cyberduck.ch
 */

import ch.cyberduck.core.threading.ActionOperationBatcher;
import ch.cyberduck.core.threading.ActionOperationBatcherFactory;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.barbarysoftware.watchservice.ClosedWatchServiceException;
import com.barbarysoftware.watchservice.WatchEvent;
import com.barbarysoftware.watchservice.WatchKey;
import com.barbarysoftware.watchservice.WatchService;
import com.barbarysoftware.watchservice.WatchableFile;

import static com.barbarysoftware.watchservice.StandardWatchEventKind.*;

/**
 * @version $Id$
 */
public class FileWatcher {
    private static Logger log = Logger.getLogger(FileWatcher.class);

    private WatchService monitor;
    private Local file;
    private WatchableFile watchable;

    public FileWatcher(final Local file) {
        this.file = file;
        this.watchable = new WatchableFile(new File(file.getParent().getAbsolute()));
        this.monitor = WatchService.newWatchService();
    }

    public void register() {
        try {
            if(log.isDebugEnabled()) {
                log.debug(String.format("Register file %s", watchable.getFile()));
            }
            watchable.register(monitor, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        }
        catch(IOException e) {
            log.error(String.format("Failure registering file watcher monitor for %s", watchable.getFile()), e);
        }
        final AtomicReference<Thread> consumer = new AtomicReference<Thread>(new Thread(new Runnable() {
            public void run() {
                while(true) {
                    final ActionOperationBatcher autorelease = ActionOperationBatcherFactory.get();
                    try {
                        // wait for key to be signaled
                        WatchKey key;
                        try {
                            key = monitor.take();
                        }
                        catch(ClosedWatchServiceException e) {
                            // If this watch service is closed
                            return;
                        }
                        catch(InterruptedException e) {
                            return;
                        }
                        for(WatchEvent<?> event : key.pollEvents()) {
                            final WatchEvent.Kind<?> kind = event.kind();
                            log.info(String.format("Detected file system event %s", kind.name()));
                            if(kind == OVERFLOW) {
                                continue;
                            }
                            // The filename is the context of the event.
                            final WatchEvent<File> ev = (WatchEvent<File>) event;
                            if(ev.context().equals(new File(file.getAbsolute()).getCanonicalFile())) {
                                if(ENTRY_MODIFY == kind) {
                                    for(FileWatcherListener l : listeners.toArray(new FileWatcherListener[listeners.size()])) {
                                        l.fileWritten(LocalFactory.createLocal(ev.context()));
                                    }
                                }
                                else if(ENTRY_DELETE == kind) {
                                    for(FileWatcherListener l : listeners.toArray(new FileWatcherListener[listeners.size()])) {
                                        l.fileDeleted(LocalFactory.createLocal(ev.context()));
                                    }
                                }
                                else if(ENTRY_CREATE == kind) {
                                    for(FileWatcherListener l : listeners.toArray(new FileWatcherListener[listeners.size()])) {
                                        l.fileCreated(LocalFactory.createLocal(ev.context()));
                                    }
                                }
                                else {
                                    log.debug(String.format("Ignored file system event %s for %s", kind.name(), ev.context()));
                                }
                            }
                            else {
                                log.debug(String.format("Ignored file system event for unknown file %s", ev.context()));
                            }
                        }
                        // Reset the key -- this step is critical to receive further watch events.
                        boolean valid = key.reset();
                        if(!valid) {
                            // The key is no longer valid and the loop can exit.
                            break;
                        }
                    }
                    catch(IOException e) {
                        log.error(e.getMessage());
                    }
                    finally {
                        autorelease.operate();
                    }
                }
            }
        }));
        consumer.get().start();
    }

    private Set<FileWatcherListener> listeners
            = Collections.synchronizedSet(new HashSet<FileWatcherListener>());

    public void addListener(final FileWatcherListener listener) {
        listeners.add(listener);
    }

    public void removeListener(final FileWatcherListener listener) {
        listeners.remove(listener);
        if(listeners.isEmpty()) {
            if(log.isDebugEnabled()) {
                log.debug(String.format("Unwatch file %s", watchable.getFile()));
            }
            try {
                monitor.close();
            }
            catch(IOException e) {
                log.error(String.format("Failure closing file watcher monitor for %s", watchable.getFile()), e);
            }
        }
    }
}