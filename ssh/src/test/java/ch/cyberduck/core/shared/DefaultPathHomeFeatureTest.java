package ch.cyberduck.core.shared;

/*
 * Copyright (c) 2002-2023 iterate GmbH. All rights reserved.
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

import ch.cyberduck.core.sftp.AbstractSFTPTest;
import ch.cyberduck.test.IntegrationTest;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@Category(IntegrationTest.class)
public class DefaultPathHomeFeatureTest extends AbstractSFTPTest {

    @Test
    public void testFind() throws Exception {
        final DefaultPathHomeFeature feature = new DefaultPathHomeFeature(session);
        assertNull(feature.find());
        session.getHost().setDefaultPath("~/d");
        assertEquals("/d", feature.find().getAbsolute());
    }
}