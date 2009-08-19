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

package org.hornetq.core.server;

import org.hornetq.core.remoting.Channel;
import org.hornetq.core.remoting.Packet;
import org.hornetq.core.remoting.RemotingConnection;
import org.hornetq.core.remoting.impl.wireformat.CreateQueueMessage;
import org.hornetq.core.remoting.impl.wireformat.PacketsConfirmedMessage;
import org.hornetq.core.remoting.impl.wireformat.RollbackMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionAcknowledgeMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionBindingQueryMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionConsumerCloseMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionConsumerFlowCreditMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionCreateConsumerMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionDeleteQueueMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionExpiredMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionQueueQueryMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionSendContinuationMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionSendLargeMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionSendMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXACommitMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXAEndMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXAForgetMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXAJoinMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXAPrepareMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXAResumeMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXARollbackMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXASetTimeoutMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXAStartMessage;
import org.hornetq.core.remoting.impl.wireformat.replication.SessionReplicateDeliveryMessage;
import org.hornetq.core.server.impl.ServerSessionPacketHandler;

/**
 *
 * A ServerSession
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:andy.taylor@jboss.org>Andy Taylor</a>
 *
 */
public interface ServerSession
{
   String getName();

   long getID();

   String getUsername();

   String getPassword();

   int getMinLargeMessageSize();

   Object getConnectionID();

   void removeConsumer(ServerConsumer consumer) throws Exception;

   void close() throws Exception;

   void promptDelivery(Queue queue);

   void handleAcknowledge(final SessionAcknowledgeMessage packet);

   void handleExpired(final SessionExpiredMessage packet);

   void handleRollback(RollbackMessage packet);

   void handleCommit(Packet packet);

   void handleXACommit(SessionXACommitMessage packet);

   void handleXAEnd(SessionXAEndMessage packet);

   void handleXAForget(SessionXAForgetMessage packet);

   void handleXAJoin(SessionXAJoinMessage packet);

   void handleXAPrepare(SessionXAPrepareMessage packet);

   void handleXAResume(SessionXAResumeMessage packet);

   void handleXARollback(SessionXARollbackMessage packet);

   void handleXAStart(SessionXAStartMessage packet);

   void handleXASuspend(Packet packet);

   void handleGetInDoubtXids(Packet packet);

   void handleGetXATimeout(Packet packet);

   void handleSetXATimeout(SessionXASetTimeoutMessage packet);

   void handleStart(Packet packet);

   void handleStop(Packet packet);

   void handleCreateQueue(CreateQueueMessage packet);

   void handleDeleteQueue(SessionDeleteQueueMessage packet);

   void handleCreateConsumer(SessionCreateConsumerMessage packet);

   void handleExecuteQueueQuery(SessionQueueQueryMessage packet);

   void handleExecuteBindingQuery(SessionBindingQueryMessage packet);

   void handleCloseConsumer(SessionConsumerCloseMessage packet);

   void handleReceiveConsumerCredits(SessionConsumerFlowCreditMessage packet);

   void handleSendContinuations(SessionSendContinuationMessage packet);

   void handleSend(SessionSendMessage packet);

   void handleSendLargeMessage(SessionSendLargeMessage packet);

   void handleFailedOver(Packet packet);

   void handleClose(Packet packet);

   void handleReplicatedDelivery(SessionReplicateDeliveryMessage packet);
   
   void handlePacketsConfirmed(PacketsConfirmedMessage packet);

   int transferConnection(RemotingConnection newConnection, int lastReceivedCommandID);

   Channel getChannel();
   
   ServerSessionPacketHandler getHandler();
   
   void setHandler(ServerSessionPacketHandler handler);

}