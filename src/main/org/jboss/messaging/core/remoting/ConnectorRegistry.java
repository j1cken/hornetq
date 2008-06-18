/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */ 

package org.jboss.messaging.core.remoting;

import org.jboss.messaging.core.client.ConnectionParams;
import org.jboss.messaging.core.client.Location;
import org.jboss.messaging.core.config.Configuration;
import org.jboss.messaging.core.remoting.impl.invm.INVMConnector;


/**
 * The ConnectorRegistry keeps track of Configurations and NIOConnectors.
 * <p/>
 * When a {@link org.jboss.messaging.core.remoting.impl.RemotingServiceImpl} is started, it register its {@link Configuration}.
 * <p/>
 * When a client is created, it gets its {@link RemotingConnector} from the
 * ConnectorRegistry using the {@link Configuration} corresponding to the server
 * it wants to connect to. If the ConnectionRegistry contains this Configuration, it
 * implies that the Client is in the same VM than the server. In that case, we
 * optimize by returning a {@link INVMConnector} regardless of the transport
 * type defined by the Configuration
 *
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 * @version <tt>$Revision$</tt>
 */
public interface ConnectorRegistry
{

   /**
    * @return <code>true</code> if this Configuration has not already been
    *         registered, <code>false</code> else
    */
   boolean register(Location location, PacketDispatcher serverDispatcher);

   /**
    * @return <code>true</code> if this Configuration was registered,
    *         <code>false</code> else
    */
   boolean unregister(Location location);

   RemotingConnector getConnector(Location location, ConnectionParams connectionParams);

   /**
    * Decrement the number of references on the NIOConnector corresponding to
    * the Configuration.
    * <p/>
    * If there is only one reference, remove it from the connectors Map and
    * returns it. Otherwise return null.
    *
    * @param location a Location
    * @return the NIOConnector if there is no longer any references to it or
    *         <code>null</code>
    * @throws IllegalStateException if no NIOConnector were created for the given Configuration
    */
   RemotingConnector removeConnector(Location location);

   int getRegisteredConfigurationSize();

   int getConnectorCount(Location location);

   void clear();
}