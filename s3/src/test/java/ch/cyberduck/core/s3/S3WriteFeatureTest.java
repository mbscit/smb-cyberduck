package ch.cyberduck.core.s3;

import ch.cyberduck.core.DisabledConnectionCallback;
import ch.cyberduck.core.DisabledLoginCallback;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathAttributes;
import ch.cyberduck.core.exception.InteroperabilityException;
import ch.cyberduck.core.features.Delete;
import ch.cyberduck.core.features.Write;
import ch.cyberduck.core.http.HttpResponseOutputStream;
import ch.cyberduck.core.io.SHA256ChecksumCompute;
import ch.cyberduck.core.io.StreamCopier;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.test.IntegrationTest;

import org.apache.commons.text.RandomStringGenerator;
import org.jets3t.service.model.StorageObject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;

import static org.junit.Assert.*;

@Category(IntegrationTest.class)
public class S3WriteFeatureTest extends AbstractS3Test {

    @Test
    public void testAppendBelowLimit() throws Exception {
        final S3WriteFeature feature = new S3WriteFeature(session);
        final Write.Append append = feature.append(new Path("/p", EnumSet.of(Path.Type.file)), new TransferStatus().withLength(0L));
        assertFalse(append.append);
    }

    @Test
    public void testSize() throws Exception {
        final S3WriteFeature feature = new S3WriteFeature(session);
        final Write.Append append = feature.append(new Path("/p", EnumSet.of(Path.Type.file)), new TransferStatus().withLength(0L).withRemote(new PathAttributes().withSize(3L)));
        assertFalse(append.append);
        assertEquals(0L, append.size, 0L);
    }

    @Test
    public void testAppendNoMultipartFound() throws Exception {
        final Path container = new Path("test-us-east-1-cyberduck", EnumSet.of(Path.Type.directory, Path.Type.volume));
        assertFalse(new S3WriteFeature(session).append(new Path(container, UUID.randomUUID().toString(), EnumSet.of(Path.Type.file)), new TransferStatus().withLength(Long.MAX_VALUE)).append);
        assertEquals(Write.override, new S3WriteFeature(session).append(new Path(container, UUID.randomUUID().toString(), EnumSet.of(Path.Type.file)), new TransferStatus().withLength(Long.MAX_VALUE)));
        assertEquals(Write.override, new S3WriteFeature(session).append(new Path(container, UUID.randomUUID().toString(), EnumSet.of(Path.Type.file)), new TransferStatus().withLength(0L)));
    }

    @Test(expected = InteroperabilityException.class)
    public void testWriteChunkedTransferAWS2SignatureFailure() throws Exception {
        session.setSignatureVersion(S3Protocol.AuthenticationHeaderSignatureVersion.AWS2);
        final S3WriteFeature feature = new S3WriteFeature(session);
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.volume, Path.Type.directory));
        final TransferStatus status = new TransferStatus();
        status.setLength(-1L);
        final Path file = new Path(container, UUID.randomUUID().toString(), EnumSet.of(Path.Type.file));
        feature.write(file, status, new DisabledConnectionCallback());
    }

    @Test(expected = InteroperabilityException.class)
    public void testWriteChunkedTransferAWS4Signature() throws Exception {
        final S3WriteFeature feature = new S3WriteFeature(session);
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.volume, Path.Type.directory));
        final TransferStatus status = new TransferStatus();
        status.setLength(-1L);
        final Path file = new Path(container, UUID.randomUUID().toString(), EnumSet.of(Path.Type.file));
        final byte[] content = new RandomStringGenerator.Builder().build().generate(5 * 1024 * 1024).getBytes(StandardCharsets.UTF_8);
        status.setChecksum(new SHA256ChecksumCompute().compute(new ByteArrayInputStream(content), status));
        try {
            feature.write(file, status, new DisabledConnectionCallback());
        }
        catch(InteroperabilityException e) {
            assertEquals("A header you provided implies functionality that is not implemented. Please contact your web hosting service provider for assistance.", e.getDetail());
            throw e;
        }
    }

    @Test
    public void testWriteAWS4Signature() throws Exception {
        final S3WriteFeature feature = new S3WriteFeature(session);
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.volume, Path.Type.directory));
        final Path file = new Path(container, UUID.randomUUID().toString(), EnumSet.of(Path.Type.file));
        final byte[] content = new RandomStringGenerator.Builder().build().generate(5 * 1024 * 1024).getBytes(StandardCharsets.UTF_8);
        final TransferStatus status = new TransferStatus();
        status.setLength(content.length);
        status.setChecksum(new SHA256ChecksumCompute().compute(new ByteArrayInputStream(content), status));
        final HttpResponseOutputStream<StorageObject> out = feature.write(file, status, new DisabledConnectionCallback());
        new StreamCopier(status, status).transfer(new ByteArrayInputStream(content), out);
        out.close();
        assertEquals(content.length, new S3AttributesFinderFeature(session).find(file).getSize());
        new S3DefaultDeleteFeature(session).delete(Collections.singletonList(file), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testWriteVersionedBucket() throws Exception {
        final S3WriteFeature feature = new S3WriteFeature(session);
        final Path container = new Path("versioning-test-eu-central-1-cyberduck", EnumSet.of(Path.Type.volume, Path.Type.directory));
        final Path file = new Path(container, UUID.randomUUID().toString(), EnumSet.of(Path.Type.file));
        final byte[] content = new RandomStringGenerator.Builder().build().generate(5 * 1024 * 1024).getBytes(StandardCharsets.UTF_8);
        final TransferStatus status = new TransferStatus();
        status.setLength(content.length);
        status.setChecksum(new SHA256ChecksumCompute().compute(new ByteArrayInputStream(content), status));
        final HttpResponseOutputStream<StorageObject> out = feature.write(file, status, new DisabledConnectionCallback());
        new StreamCopier(status, status).transfer(new ByteArrayInputStream(content), out);
        out.close();
        assertNotNull(file.attributes().getVersionId());
        assertEquals(content.length, new S3AttributesFinderFeature(session).find(file).getSize());
        new S3DefaultDeleteFeature(session).delete(Collections.singletonList(file), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }
}
