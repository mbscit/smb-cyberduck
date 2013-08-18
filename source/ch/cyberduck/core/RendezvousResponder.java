package ch.cyberduck.core;

/*
 *  Copyright (c) 2005 David Kocher. All rights reserved.
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

import com.apple.dnssd.BrowseListener;
import com.apple.dnssd.DNSSD;
import com.apple.dnssd.DNSSDException;
import com.apple.dnssd.DNSSDService;
import com.apple.dnssd.ResolveListener;
import com.apple.dnssd.TXTRecord;

import ch.cyberduck.core.threading.ActionOperationBatcher;
import ch.cyberduck.core.threading.ActionOperationBatcherFactory;

import org.apache.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @version $Id$
 */
public final class RendezvousResponder extends AbstractRendezvous implements BrowseListener, ResolveListener {
    private static Logger log = Logger.getLogger(RendezvousResponder.class);

    private Map<String, DNSSDService> browsers;

    public static void register() {
        RendezvousFactory.addFactory(Factory.NATIVE_PLATFORM, new Factory());
    }

    private static class Factory extends RendezvousFactory {
        @Override
        protected Rendezvous create() {
            return new RendezvousResponder();
        }
    }

    public RendezvousResponder() {
        this.browsers = new ConcurrentHashMap<String, DNSSDService>();
    }

    @Override
    public void init() {
        if(log.isDebugEnabled()) {
            log.debug("Initialize responder by browsing DNSSD");
        }
        try {
            for(String protocol : this.getServiceTypes()) {
                if(log.isInfoEnabled()) {
                    log.info(String.format("Adding service listener for %s", protocol));
                }
                this.browsers.put(protocol, DNSSD.browse(protocol, this));
            }
        }
        catch(DNSSDException e) {
            log.error(String.format("Failure initializing Bonjour discovery: %s", e.getMessage()), e);
            this.quit();
        }
    }

    @Override
    public void quit() {
        for(String protocol : this.getServiceTypes()) {
            if(log.isInfoEnabled()) {
                log.info(String.format("Removing service listener for %s", protocol));
            }
            final DNSSDService service = this.browsers.get(protocol);
            if(null == service) {
                continue;
            }
            service.stop();
        }
    }

    @Override
    public void serviceFound(DNSSDService browser, int flags, int ifIndex, String serviceName,
                             String regType, String domain) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Browser found service at %s not yet resolved", serviceName));
        }
        try {
            DNSSD.resolve(flags, ifIndex, serviceName, regType, domain, this);
        }
        catch(DNSSDException e) {
            log.error(String.format("Failure resolving service %s: %s", serviceName, e.getMessage()), e);
        }
    }

    @Override
    public void serviceLost(DNSSDService browser, int flags, int ifIndex, String serviceName,
                            String regType, String domain) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Service lost for %s", serviceName));
        }
        final ActionOperationBatcher autorelease = ActionOperationBatcherFactory.get();
        try {
            final String identifier = DNSSD.constructFullName(serviceName, regType, domain);
            this.remove(identifier);
        }
        catch(DNSSDException e) {
            log.error(String.format("Failure removing service %s: %s", serviceName, e.getMessage()), e);
        }
        finally {
            autorelease.operate();
        }
    }

    @Override
    public void operationFailed(DNSSDService resolver, int errorCode) {
        log.warn(String.format("Operation failed with error code %d", errorCode));
        resolver.stop();
    }

    @Override
    public void serviceResolved(DNSSDService resolver, int flags, int ifIndex,
                                final String fullname, final String hostname, int port, TXTRecord txtRecord) {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Resolved service with name %s to %s", fullname, hostname));
        }
        final ActionOperationBatcher autorelease = ActionOperationBatcherFactory.get();
        try {
            String user = null;
            String password = null;
            String path = null;
            if(log.isDebugEnabled()) {
                log.debug(String.format("TXT Record %s", txtRecord));
            }
            if(txtRecord.contains("u")) {
                user = txtRecord.getValueAsString("u");
            }
            if(txtRecord.contains("p")) {
                password = txtRecord.getValueAsString("p");
            }
            if(txtRecord.contains("path")) {
                path = txtRecord.getValueAsString("path");
            }
            this.add(fullname, hostname, port, user, password, path);
        }
        finally {
            // Note: When the desired results have been returned, the client MUST terminate
            // the resolve by calling DNSSDService.stop().
            resolver.stop();
            autorelease.operate();
        }
    }
}