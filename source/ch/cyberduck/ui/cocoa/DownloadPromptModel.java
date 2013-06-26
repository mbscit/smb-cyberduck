package ch.cyberduck.ui.cocoa;

/*
 *  Copyright (c) 2007 David Kocher. All rights reserved.
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

import ch.cyberduck.core.Filter;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.formatter.SizeFormatterFactory;
import ch.cyberduck.core.transfer.Transfer;
import ch.cyberduck.ui.cocoa.application.NSImage;
import ch.cyberduck.ui.cocoa.foundation.NSAttributedString;
import ch.cyberduck.ui.cocoa.foundation.NSObject;
import ch.cyberduck.ui.resources.IconCacheFactory;

import org.apache.log4j.Logger;

/**
 * @version $Id$
 */
public class DownloadPromptModel extends TransferPromptModel {
    private static final Logger log = Logger.getLogger(DownloadPromptModel.class);

    public DownloadPromptModel(TransferPromptController c, Transfer transfer) {
        super(c, transfer);
    }

    /**
     * Filtering what files are displayed. Used to decide which files to include in the prompt dialog
     */
    private PromptFilter filter = new PromptFilter() {
        /**
         * @param path File
         * @return True for files that already exist in the download folder
         */
        @Override
        public boolean accept(final Path path) {
            if(path.getLocal().exists()) {
                if(path.attributes().isFile()) {
                    if(path.getLocal().attributes().getSize() == 0) {
                        if(log.isDebugEnabled()) {
                            log.debug(String.format("Skip prompt for zero sized file %s", path.getName()));
                        }
                        // Do not prompt for zero sized files
                        return false;
                    }
                }
                return super.accept(path);
            }
            return false;
        }
    };

    @Override
    protected Filter<Path> filter() {
        return filter;
    }

    @Override
    protected NSObject objectValueForItem(final Path item, final String identifier) {
        final NSObject cached = tableViewCache.get(item, identifier);
        if(null == cached) {
            if(identifier.equals(TransferPromptModel.SIZE_COLUMN)) {
                return tableViewCache.put(item, identifier, NSAttributedString.attributedStringWithAttributes(
                        SizeFormatterFactory.get().format(item.getLocal().attributes().getSize()),
                        TableCellAttributes.browserFontRightAlignment()));
            }
            if(identifier.equals(TransferPromptModel.WARNING_COLUMN)) {
                if(item.attributes().isFile()) {
                    if(item.attributes().getSize() == 0) {
                        return tableViewCache.put(item, identifier, IconCacheFactory.<NSImage>get().iconNamed("alert.tiff"));
                    }
                    if(item.getLocal().attributes().getSize() > item.attributes().getSize()) {
                        return tableViewCache.put(item, identifier, IconCacheFactory.<NSImage>get().iconNamed("alert.tiff"));
                    }
                }
                return null;
            }
            return super.objectValueForItem(item, identifier);
        }
        return cached;
    }
}