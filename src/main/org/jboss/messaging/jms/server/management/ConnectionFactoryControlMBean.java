/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.messaging.jms.server.management;

import java.util.List;

/**
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 * @author <a href="mailto:fox@redhat.com">Tim Fox</a>
 * 
 * @version <tt>$Revision$</tt>
 * 
 */
public interface ConnectionFactoryControlMBean
{   
   String getName();
   
   List<String> getBindings();

   String getClientID();
   
   long getPingPeriod();
   
   long getCallTimeout();
   
   int getDupsOKBatchSize();

   int getConsumerMaxRate();

   int getConsumerWindowSize();

   int getProducerMaxRate();

   int getProducerWindowSize();

   boolean isBlockOnAcknowledge();

   boolean isBlockOnPersistentSend();

   boolean isBlockOnNonPersistentSend();

   boolean isPreAcknowledge();

   long getConnectionTTL();

   long getTransactionBatchSize();

   long getMinLargeMessageSize();

   boolean isAutoGroup();

   int getMaxConnections();

   long getRetryInterval();

   double getRetryIntervalMultiplier();

   int getMaxRetriesBeforeFailover();

   int getMaxRetriesAfterFailover();

}
