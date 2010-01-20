/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.core.server.impl;

import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.CREATESESSION;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.CREATE_QUEUE;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.CREATE_REPLICATION;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.REATTACH_SESSION;

import org.hornetq.api.core.HornetQException;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.remoting.Channel;
import org.hornetq.core.remoting.ChannelHandler;
import org.hornetq.core.remoting.CoreRemotingConnection;
import org.hornetq.core.remoting.Packet;
import org.hornetq.core.remoting.impl.wireformat.CreateQueueMessage;
import org.hornetq.core.remoting.impl.wireformat.CreateReplicationSessionMessage;
import org.hornetq.core.remoting.impl.wireformat.CreateSessionMessage;
import org.hornetq.core.remoting.impl.wireformat.HornetQExceptionMessage;
import org.hornetq.core.remoting.impl.wireformat.NullResponseMessage;
import org.hornetq.core.remoting.impl.wireformat.ReattachSessionMessage;
import org.hornetq.core.replication.ReplicationEndpoint;
import org.hornetq.core.server.HornetQServer;

/**
 * A packet handler for all packets that need to be handled at the server level
 * 
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="ataylor@redhat.com">Andy Taylor</a>
 */
public class HornetQPacketHandler implements ChannelHandler
{
   private static final Logger log = Logger.getLogger(HornetQPacketHandler.class);

   private final HornetQServer server;

   private final Channel channel1;

   private final CoreRemotingConnection connection;

   public HornetQPacketHandler(final HornetQServer server, final Channel channel1, final CoreRemotingConnection connection)
   {
      this.server = server;

      this.channel1 = channel1;

      this.connection = connection;
   }

   public void handlePacket(final Packet packet)
   {
      byte type = packet.getType();

      // All these operations need to be idempotent since they are outside of the session
      // reliability replay functionality
      switch (type)
      {
         case CREATESESSION:
         {
            CreateSessionMessage request = (CreateSessionMessage)packet;

            handleCreateSession(request);

            break;
         }
         case REATTACH_SESSION:
         {
            ReattachSessionMessage request = (ReattachSessionMessage)packet;

            handleReattachSession(request);

            break;
         }
         case CREATE_QUEUE:
         {
            // Create queue can also be fielded here in the case of a replicated store and forward queue creation

            CreateQueueMessage request = (CreateQueueMessage)packet;

            handleCreateQueue(request);

            break;
         }
         case CREATE_REPLICATION:
         {
            // Create queue can also be fielded here in the case of a replicated store and forward queue creation

            CreateReplicationSessionMessage request = (CreateReplicationSessionMessage)packet;

            handleCreateReplication(request);

            break;
         }
         default:
         {
            HornetQPacketHandler.log.error("Invalid packet " + packet);
         }
      }
   }

   private void handleCreateSession(final CreateSessionMessage request)
   {
      boolean incompatibleVersion = false;
      Packet response;
      try
      {
         response = server.createSession(request.getName(),
                                         request.getSessionChannelID(),
                                         request.getUsername(),
                                         request.getPassword(),
                                         request.getMinLargeMessageSize(),
                                         request.getVersion(),
                                         connection,
                                         request.isAutoCommitSends(),
                                         request.isAutoCommitAcks(),
                                         request.isPreAcknowledge(),
                                         request.isXA(),
                                         request.getWindowSize());
      }
      catch (Exception e)
      {
         if (e instanceof HornetQException)
         {
            response = new HornetQExceptionMessage((HornetQException)e);
            
            if (((HornetQException)e).getCode() == HornetQException.INCOMPATIBLE_CLIENT_SERVER_VERSIONS)
            {
               incompatibleVersion = true;
            }
         }
         else
         {
            HornetQPacketHandler.log.error("Failed to create session", e);
            response = new HornetQExceptionMessage(new HornetQException(HornetQException.INTERNAL_ERROR));
         }
      }
      
      // send the exception to the client and destroy
      // the connection if the client and server versions
      // are not compatible
      if (incompatibleVersion)
      {
         channel1.sendAndFlush(response);
      }
      else
      {
         channel1.send(response);
      }
   }

   private void handleReattachSession(final ReattachSessionMessage request)
   {
      Packet response;

      try
      {
         response = server.reattachSession(connection, request.getName(), request.getLastConfirmedCommandID());
      }
      catch (Exception e)
      {
         if (e instanceof HornetQException)
         {
            response = new HornetQExceptionMessage((HornetQException)e);
         }
         else
         {
            HornetQPacketHandler.log.error("Failed to reattach session", e);

            response = new HornetQExceptionMessage(new HornetQException(HornetQException.INTERNAL_ERROR));
         }
      }

      channel1.send(response);
   }

   private void handleCreateQueue(final CreateQueueMessage request)
   {
      try
      {
         server.createQueue(request.getAddress(),
                            request.getQueueName(),
                            request.getFilterString(),
                            request.isDurable(),
                            request.isTemporary());
      }
      catch (Exception e)
      {
         HornetQPacketHandler.log.error("Failed to handle create queue", e);
      }
   }

   private void handleCreateReplication(final CreateReplicationSessionMessage request)
   {
      Packet response;

      try
      {
         Channel channel = connection.getChannel(request.getSessionChannelID(), -1);

         ReplicationEndpoint endpoint = server.connectToReplicationEndpoint(channel);

         channel.setHandler(endpoint);

         response = new NullResponseMessage();
      }
      catch (Exception e)
      {
         if (e instanceof HornetQException)
         {
            response = new HornetQExceptionMessage((HornetQException)e);
         }
         else
         {
            HornetQPacketHandler.log.warn(e.getMessage(), e);
            response = new HornetQExceptionMessage(new HornetQException(HornetQException.INTERNAL_ERROR));
         }
      }

      channel1.send(response);
   }

}