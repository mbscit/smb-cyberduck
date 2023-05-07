package ch.cyberduck.core.smb;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.share.File;

import ch.cyberduck.core.ConnectionCallback;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.features.Read;
import ch.cyberduck.core.transfer.TransferStatus;

public class SMBReadFeature implements Read {

    private final SMBSession session;

    public SMBReadFeature(SMBSession session) {
        this.session = session;
    }

    @Override
    public InputStream read(Path file, TransferStatus status, ConnectionCallback callback) throws BackgroundException {
        
        Set<SMB2ShareAccess> shareAccessSet = new HashSet<>();
        shareAccessSet.add(SMB2ShareAccess.FILE_SHARE_READ);

        Set<FileAttributes> fileAttributes = new HashSet<>();
        fileAttributes.add(FileAttributes.FILE_ATTRIBUTE_NORMAL);
        Set<SMB2CreateOptions> createOptions = new HashSet<>();
        SMB2CreateDisposition smb2CreateDisposition = SMB2CreateDisposition.FILE_OPEN_IF;

        Set<AccessMask> accessMask = new HashSet<>();
        accessMask.add(AccessMask.MAXIMUM_ALLOWED);

        createOptions.add(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE);

        File fileEntry = session.share.openFile(file.getAbsolute(), accessMask, fileAttributes, shareAccessSet, smb2CreateDisposition, createOptions);

        return new SMBInputStream(fileEntry.getInputStream(), fileEntry);
    }

    public final class SMBInputStream extends InputStream {

        private InputStream stream;
        private File file;


        public SMBInputStream(InputStream stream, File file) {
            this.stream = stream;
            this.file = file;
        }

        @Override
        public int read() throws IOException {
            return stream.read();
        }

        @Override
        public void close() throws IOException {
            super.close();
            file.close();
        }

    }
    
}
