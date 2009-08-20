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

package org.hornetq.tests.integration.management;

import org.hornetq.core.config.TransportConfiguration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory;
import org.hornetq.core.server.HornetQ;
import org.hornetq.core.server.HornetQServer;

/**
 * A SecurityManagementTest
 *
 * @author <a href="jmesnil@redhat.com">Jeff Mesnil</a>
 */
public class SecurityManagementWithModifiedConfigurationTest extends SecurityManagementTestBase
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private String configuredClusterPassword = "this is not the default password";
   
   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testSendManagementMessageWithModifiedClusterAdminUser() throws Exception
   {
      doSendManagementMessage(ConfigurationImpl.DEFAULT_MANAGEMENT_CLUSTER_USER, 
                              configuredClusterPassword, true);
   }

   public void testSendManagementMessageWithDefaultClusterAdminUser() throws Exception
   {
      doSendManagementMessage(ConfigurationImpl.DEFAULT_MANAGEMENT_CLUSTER_USER, 
                              ConfigurationImpl.DEFAULT_MANAGEMENT_CLUSTER_PASSWORD, false);
   }

   public void testSendManagementMessageWithGuest() throws Exception
   {
      doSendManagementMessage("guest", "guest", false);
   }

   public void testSendManagementMessageWithoutUserCredentials() throws Exception
   {
      doSendManagementMessage(null, null, false);
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   @Override
   protected HornetQServer setupAndStartHornetQServer() throws Exception
   {
      ConfigurationImpl conf = new ConfigurationImpl();
      conf.setSecurityEnabled(true);
      conf.setManagementClusterPassword(configuredClusterPassword);
      conf.getAcceptorConfigurations().add(new TransportConfiguration(InVMAcceptorFactory.class.getName()));
      HornetQServer server = HornetQ.newHornetQServer(conf, false);
      server.start();
      
      return server;
   }
   
   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
