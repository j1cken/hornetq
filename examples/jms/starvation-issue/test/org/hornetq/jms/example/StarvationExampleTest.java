/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.jms.example;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by IntelliJ IDEA.
 * User: torben
 * Date: 12/15/10
 * Time: 9:40 AM
 * To change this template use File | Settings | File Templates.
 */
public class StarvationExampleTest {

    @Test
    public void testQNameFromAddressName() {
        assertEquals("String not correct!", "DLQ", new StarvationExample().getQName("jms.queue.DLQ"));
        assertEquals("String not correct!", "f3209e6-0829-11e0-a832-0022fae1ee", new StarvationExample().getQName("sf.my-cluster.f3209e6-0829-11e0-a832-0022fae1ee"));
    }

    @Test
    public void testIsQueue() {
        assertTrue("Not a interesting Q (sf...) !", new StarvationExample().isInteresting("sf.my-cluster.aae3f742-082a-11e0-9d51-0022fae1eea2"));
        assertTrue("Not a interesting Q (jms.q...) !", new StarvationExample().isInteresting("jms.queue.ExpiryQueue"));
        assertFalse("Not a interesting Q (hornetq....) !", new StarvationExample().isInteresting("hornetq.notifications"));
    }
}
