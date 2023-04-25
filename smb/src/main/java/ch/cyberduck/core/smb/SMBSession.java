package ch.cyberduck.core.smb;

/*
 * Copyright (c) 2002-2013 David Kocher. All rights reserved.
 * http://cyberduck.ch/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Bug fixes, suggestions and comments should be sent to feedback@cyberduck.ch
 */

import ch.cyberduck.core.Host;
import ch.cyberduck.core.HostKeyCallback;
import ch.cyberduck.core.LoginCallback;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.proxy.Proxy;
import ch.cyberduck.core.ssl.X509KeyManager;
import ch.cyberduck.core.ssl.X509TrustManager;
import ch.cyberduck.core.threading.CancelCallback;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

import com.hierynomus.protocol.transport.TransportException;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

public class SMBSession extends ch.cyberduck.core.Session<SMBClient> {

    protected Connection connection;
    protected DiskShare share;
    protected Session session;

    protected SMBSession(final Host h) {
        super(h);
        client = new SMBClient();
    }

    public SMBSession(final Host h, final X509TrustManager trust, final X509KeyManager key) {
        super(h);
        client = new SMBClient();
    }

    private static final Logger log = LogManager.getLogger(SMBSession.class);

    @Override
    protected SMBClient connect(Proxy proxy, HostKeyCallback key, LoginCallback prompt, CancelCallback cancel)
            throws BackgroundException {

        try {
            this.connection = client.connect(getHost().getHostname(), getHost().getPort());
        }
        catch(Exception e) {
            try {
                connection.close();
                throw new RuntimeException(e);
            }
            catch(IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        return client;
    }

    @Override
    public void login(Proxy proxy, LoginCallback prompt, CancelCallback cancel) throws BackgroundException {
        if(connection == null) {
            throw new BackgroundException(new IllegalStateException("Connection must be established before login"));
        }
        String domain, username, shareString;

        String[] parts = host.getCredentials().getUsername().split("/", 0);
        String domainUsername = parts[0];
        shareString = parts[1];
        parts = domainUsername.split("@", 0);
        if(parts.length == 1) {
            domain = "WORKGROUP";
            username = parts[0];
        }
        else {
            username = parts[0];
            domain = parts[1];
        }

        AuthenticationContext ac = new AuthenticationContext(username, host.getCredentials().getPassword().toCharArray(), domain);
        try {
            session = connection.authenticate(ac);
            share = (DiskShare) session.connectShare(shareString);
        } catch(Exception e) {
            throw new BackgroundException(e);
        }
    }

    @Override
    protected void logout() throws BackgroundException {
        if(session != null) {
            try {
                session.logoff();
            }
            catch(TransportException e) {
                throw new BackgroundException(e);
            }
        }
    }

    @Override
    protected void disconnect() {
        super.disconnect();
        try {
            if(connection != null) {
                connection.close();
                connection = null;
            }
            if(client != null) {
                client.close();
                client = null;
            }
        }
        catch(IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public boolean isConnected() {
        return connection != null && connection.isConnected();
    }

}