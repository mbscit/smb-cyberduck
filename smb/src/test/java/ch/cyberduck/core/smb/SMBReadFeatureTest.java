package ch.cyberduck.core.smb;

/*
 * Copyright (c) 2002-2017 iterate GmbH. All rights reserved.
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

import ch.cyberduck.core.DisabledConnectionCallback;
import ch.cyberduck.core.DisabledListProgressListener;
import ch.cyberduck.core.ListService;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.features.AttributesFinder;
import ch.cyberduck.core.features.Find;
import ch.cyberduck.core.features.Read;
import ch.cyberduck.core.features.Write;
import ch.cyberduck.core.io.StreamCopier;
import ch.cyberduck.core.shared.DefaultHomeFinderService;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.test.IntegrationTest;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;

import static org.junit.Assert.*;

@Category(IntegrationTest.class)
public class SMBReadFeatureTest extends AbstractSMBTest {

    @Test
    public void testReadRange() throws Exception {
        final TransferStatus status = new TransferStatus();
        final int length = 140000;
        final byte[] content = RandomUtils.nextBytes(length);
        status.setLength(content.length);
        final Path home = new DefaultHomeFinderService(session).find();
        final Path folder = new Path(home, "folder", EnumSet.of(Path.Type.file));
        final Path test = new Path(folder, "L0-file.txt", EnumSet.of(Path.Type.file));
        final Write writer = session.getFeature(Write.class);
        status.setChecksum(writer.checksum(test, status).compute(new ByteArrayInputStream(content), status));
        final OutputStream out = writer.write(test, status, new DisabledConnectionCallback());
        assertNotNull(out);
        new StreamCopier(status, status).transfer(new ByteArrayInputStream(content), out);
        assertTrue(session.getFeature(Find.class).find(test));
        assertEquals(content.length, session.getFeature(ListService.class).list(test.getParent(), new DisabledListProgressListener()).get(test).attributes().getSize());
        assertEquals(content.length, writer.append(test, status.withRemote(session.getFeature(AttributesFinder.class).find(test))).size, 0L);
        {
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream(40000);
            final TransferStatus read = new TransferStatus();
            read.setOffset(23); // offset within chunk
            read.setAppend(true);
            read.withLength(40000); // ensure to read at least two chunks
            final InputStream in = session.getFeature(Read.class).read(test, read, new DisabledConnectionCallback());
            new StreamCopier(read, read).withLimit(40000L).transfer(in, buffer);
            final byte[] reference = new byte[40000];
            System.arraycopy(content, 23, reference, 0, reference.length);
            assertArrayEquals(reference, buffer.toByteArray());
        }
        {
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream(40000);
            final TransferStatus read = new TransferStatus();
            read.setOffset(65536); // offset at the beginning of a new chunk
            read.setAppend(true);
            read.withLength(40000); // ensure to read at least two chunks
            final InputStream in = session.getFeature(Read.class).read(test, read, new DisabledConnectionCallback());
            new StreamCopier(read, read).withLimit(40000L).transfer(in, buffer);
            final byte[] reference = new byte[40000];
            System.arraycopy(content, 65536, reference, 0, reference.length);
            assertArrayEquals(reference, buffer.toByteArray());
        }
        {
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream(40000);
            final TransferStatus read = new TransferStatus();
            read.setOffset(65537); // offset at the beginning+1 of a new chunk
            read.setAppend(true);
            read.withLength(40000); // ensure to read at least two chunks
            final InputStream in = session.getFeature(Read.class).read(test, read, new DisabledConnectionCallback());
            new StreamCopier(read, read).withLimit(40000L).transfer(in, buffer);
            final byte[] reference = new byte[40000];
            System.arraycopy(content, 65537, reference, 0, reference.length);
            assertArrayEquals(reference, buffer.toByteArray());
        }
    }
}
