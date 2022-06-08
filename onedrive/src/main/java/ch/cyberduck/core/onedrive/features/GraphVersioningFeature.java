package ch.cyberduck.core.onedrive.features;/*
 * Copyright (c) 2002-2022 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

import ch.cyberduck.core.*;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.UnsupportedException;
import ch.cyberduck.core.features.Versioning;
import ch.cyberduck.core.onedrive.GraphExceptionMappingService;
import ch.cyberduck.core.onedrive.GraphSession;
import org.nuxeo.onedrive.client.Files;
import org.nuxeo.onedrive.client.OneDriveAPIException;
import org.nuxeo.onedrive.client.types.DriveItem;
import org.nuxeo.onedrive.client.types.DriveItemVersion;

import java.io.IOException;
import java.util.List;

public class GraphVersioningFeature implements Versioning {

    private final GraphSession session;
    private final GraphFileIdProvider fileid;
    private final GraphAttributesFinderFeature attributes;

    public GraphVersioningFeature(final GraphSession session, final GraphFileIdProvider fileid) {

        this.session = session;
        this.fileid = fileid;
        this.attributes = new GraphAttributesFinderFeature(session, fileid);
    }

    @Override
    public VersioningConfiguration getConfiguration(Path container) {
        return new VersioningConfiguration(true);
    }

    @Override
    public void setConfiguration(Path container, PasswordCallback prompt, VersioningConfiguration configuration) throws BackgroundException {
        throw new UnsupportedException();
    }

    @Override
    public void revert(Path file) throws BackgroundException {
        throw new UnsupportedException();
    }

    @Override
    public boolean isRevertable(Path file) {
        return false;
    }

    @Override
    public AttributedList<Path> list(Path file, ListProgressListener listener) throws BackgroundException {
        final AttributedList<Path> versions = new AttributedList<>();
        final DriveItem item = session.getItem(file);
        try {
            final DriveItem.Metadata parentMetadata = item.getMetadata();
            final List<DriveItemVersion> versionList = Files.versions(item);
            for (final DriveItemVersion version : versionList) {
                versions.add(new Path(file).withAttributes(attributes.toAttributes(parentMetadata, version)));
            }
        } catch (OneDriveAPIException e) {
            throw new GraphExceptionMappingService(fileid).map("Failure to read attributes of {0}", e, file);
        } catch (IOException e) {
            throw new DefaultIOExceptionMappingService().map("Failure to read attributes of {0}", e, file);
        }
        return versions;
    }
}