package ch.cyberduck.core.ctera;

import ch.cyberduck.core.AlphanumericRandomStringService;
import ch.cyberduck.core.DisabledConnectionCallback;
import ch.cyberduck.core.DisabledListProgressListener;
import ch.cyberduck.core.DisabledLoginCallback;
import ch.cyberduck.core.Local;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathAttributes;
import ch.cyberduck.core.dav.DAVAttributesFinderFeature;
import ch.cyberduck.core.dav.DAVDeleteFeature;
import ch.cyberduck.core.dav.DAVDirectoryFeature;
import ch.cyberduck.core.dav.DAVListService;
import ch.cyberduck.core.dav.DAVReadFeature;
import ch.cyberduck.core.dav.DAVUploadFeature;
import ch.cyberduck.core.dav.DAVWriteFeature;
import ch.cyberduck.core.features.Delete;
import ch.cyberduck.core.features.Find;
import ch.cyberduck.core.http.HttpUploadFeature;
import ch.cyberduck.core.io.BandwidthThrottle;
import ch.cyberduck.core.io.DisabledStreamListener;
import ch.cyberduck.core.shared.DefaultHomeFinderService;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.test.IntegrationTest;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;

import static org.junit.Assert.*;

@Category(IntegrationTest.class)
public class CteraWriteFeatureTest extends AbstractCteraTest {

    @Test
    public void testReadWrite() throws Exception {
        final Path root = new DefaultHomeFinderService(session).find();
        final String rootEtag = new DAVAttributesFinderFeature(session).find(root).getETag();
        assertEquals(rootEtag, new DAVAttributesFinderFeature(session).find(root).getETag());
        final Path folder = new DAVDirectoryFeature(session).mkdir(new Path(root,
                new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.directory)), new TransferStatus());
        final String folderEtag = new DAVAttributesFinderFeature(session).find(folder).getETag();
        assertEquals(folderEtag, new DAVAttributesFinderFeature(session).find(folder).getETag());
        final TransferStatus status = new TransferStatus();
        final Local local = new Local(System.getProperty("java.io.tmpdir"), new AlphanumericRandomStringService().random());
        final byte[] content = "test".getBytes(StandardCharsets.UTF_8);
        final OutputStream out = local.getOutputStream(false);
        IOUtils.write(content, out);
        out.close();
        status.setLength(content.length);
        final Path test = new Path(folder, new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.file));
        final HttpUploadFeature upload = new DAVUploadFeature(session);
        upload.upload(test, local, new BandwidthThrottle(BandwidthThrottle.UNLIMITED),
                new DisabledStreamListener(), status, new DisabledConnectionCallback());
        assertTrue(session.getFeature(Find.class).find(test));
        assertEquals(content.length, new DAVListService(session).list(test.getParent(), new DisabledListProgressListener()).get(test).attributes().getSize(), 0L);
        assertEquals(content.length, new DAVWriteFeature(session).append(test, status.withRemote(new DAVAttributesFinderFeature(session).find(test))).size, 0L);
        {
            final byte[] buffer = new byte[content.length];
            IOUtils.readFully(new DAVReadFeature(session).read(test, new TransferStatus(), new DisabledConnectionCallback()), buffer);
            assertArrayEquals(content, buffer);
        }
        {
            final byte[] buffer = new byte[content.length - 1];
            final InputStream in = new DAVReadFeature(session).read(test, new TransferStatus().withLength(content.length - 1L).append(true).withOffset(1L), new DisabledConnectionCallback());
            IOUtils.readFully(in, buffer);
            in.close();
            final byte[] reference = new byte[content.length - 1];
            System.arraycopy(content, 1, reference, 0, content.length - 1);
            assertArrayEquals(reference, buffer);
        }
        assertNotEquals(folderEtag, new DAVAttributesFinderFeature(session).find(folder).getETag());
        assertNotEquals(rootEtag, new DAVAttributesFinderFeature(session).find(root).getETag());
        new DAVDeleteFeature(session).delete(Arrays.asList(test, folder), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testReplaceContent() throws Exception {
        final Path root = new DefaultHomeFinderService(session).find();
        final Path folder = new DAVDirectoryFeature(session).mkdir(new Path(root,
                new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.directory)), new TransferStatus());
        final Local local = new Local(System.getProperty("java.io.tmpdir"), new AlphanumericRandomStringService().random());
        final Path test = new Path(folder, new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.file));
        final HttpUploadFeature upload = new DAVUploadFeature(session);
        {
            final byte[] content = RandomUtils.nextBytes(100);
            final OutputStream out = local.getOutputStream(false);
            IOUtils.write(content, out);
            out.close();
            final TransferStatus status = new TransferStatus();
            status.setLength(content.length);
            upload.upload(test, local, new BandwidthThrottle(BandwidthThrottle.UNLIMITED),
                    new DisabledStreamListener(), status, new DisabledConnectionCallback());
        }
        final PathAttributes attr1 = new DAVAttributesFinderFeature(session).find(test);
        {
            final byte[] content = RandomUtils.nextBytes(101);
            final OutputStream out = local.getOutputStream(false);
            IOUtils.write(content, out);
            out.close();
            final TransferStatus status = new TransferStatus();
            status.setLength(content.length);
            upload.upload(test, local, new BandwidthThrottle(BandwidthThrottle.UNLIMITED),
                    new DisabledStreamListener(), status, new DisabledConnectionCallback());
        }
        final PathAttributes attr2 = new DAVAttributesFinderFeature(session).find(test);
        assertEquals(101L, attr2.getSize());
        assertNotEquals(attr1.getETag(), attr2.getETag());
        assertNotEquals(attr1.getModificationDate(), attr2.getModificationDate());
        new DAVDeleteFeature(session).delete(Arrays.asList(test, folder), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }
}
