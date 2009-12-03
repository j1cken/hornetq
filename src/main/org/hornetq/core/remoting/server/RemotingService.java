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

package org.hornetq.core.remoting.server;

import java.util.Set;

import org.hornetq.core.remoting.Interceptor;
import org.hornetq.core.remoting.RemotingConnection;
import org.hornetq.core.server.HornetQComponent;

/**
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 * @author <a href="mailto:ataylor@redhat.com">Andy Taylor</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision$</tt>
 */
public interface RemotingService extends HornetQComponent
{
   /**
    * Remove a connection from the connections held by the remoting service.
    * <strong>This method must be used only from the management API.
    * RemotingConnections are removed from the remoting service when their connectionTTL is hit.</strong>
    * @param remotingConnectionID the ID of the RemotingConnection to removed
    * @return the removed RemotingConnection
    */
   RemotingConnection removeConnection(Object remotingConnectionID);

   Set<RemotingConnection> getConnections();

   void addInterceptor(Interceptor interceptor);

   boolean removeInterceptor(Interceptor interceptor);

   void freeze();

   RemotingConnection getServerSideReplicatingConnection();
}
