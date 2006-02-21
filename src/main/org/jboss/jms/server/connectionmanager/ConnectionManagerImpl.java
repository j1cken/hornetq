/*
  * JBoss, Home of Professional Open Source
  * Copyright 2005, JBoss Inc., and individual contributors as indicated
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
package org.jboss.jms.server.connectionmanager;

import java.util.Map;

import javax.jms.JMSException;

import org.jboss.jms.server.ConnectionManager;
import org.jboss.jms.server.endpoint.ServerConnectionEndpoint;
import org.jboss.logging.Logger;
import org.jboss.remoting.Client;
import org.jboss.remoting.ClientDisconnectedException;
import org.jboss.remoting.ConnectionListener;

import EDU.oswego.cs.dl.util.concurrent.ConcurrentReaderHashMap;

/**
 * 
 * A ConnectionManagerImpl.
 * 
 * @author <a href="tim.fox@jboss.com">Tim Fox</a>
 * @version 1.1
 *
 * ConnectionManagerImpl.java,v 1.1 2006/02/21 07:44:00 timfox Exp
 */
public class ConnectionManagerImpl implements ConnectionManager, ConnectionListener
{
   private static final Logger log = Logger.getLogger(ConnectionManagerImpl.class);
   
   protected Map connections;
   
   public ConnectionManagerImpl()
   {
      connections = new ConcurrentReaderHashMap();
   }

   public void registerConnection(String clientSessionId, ServerConnectionEndpoint endpoint)
   {
      connections.put(clientSessionId, endpoint);
      log.trace("Registered connection:" + clientSessionId);
   }

   public ServerConnectionEndpoint unregisterConnection(String clientSessionId)
   {      
      log.trace("Unregistered connection:" + clientSessionId);
      return (ServerConnectionEndpoint)connections.remove(clientSessionId);
   }
   
   public ServerConnectionEndpoint getConnection(String clientSessionId)
   {
      return (ServerConnectionEndpoint)connections.get(clientSessionId);
   }
   
   public void handleConnectionException(Throwable t, Client client)
   {
      String sessionId = client.getSessionId();
      if (sessionId == null)
      {         
         log.warn("Session id is null for client " + client);
      }
      else
      {            
         if (t instanceof ClientDisconnectedException)
         {
            //This is ok
            if (log.isTraceEnabled()) { log.trace("Client " + client + " has disconnected"); } 
         }
         else
         {
            log.info("Broken client connection, clearing up it's state" + sessionId, t);
            
            if (log.isTraceEnabled()) { log.trace("Clearing up server resources for this connection"); }
            
            ServerConnectionEndpoint endpoint = 
               (ServerConnectionEndpoint)connections.remove(sessionId);
            
            if (endpoint == null)
            {
               log.error("Cannot find ServerConnectionEndpoint for session id " + sessionId);
            }
            else
            {       
               try
               {
                  endpoint.close();
               }
               catch (JMSException e)
               {
                  log.error("Failed to close connection", e);
               }
            }
            
            log.info("Cleared up state for connection");            
         }
      }  
   } 

}
