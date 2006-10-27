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
package org.jboss.test.messaging.tools.jmx.rmi;

import org.jboss.logging.Logger;

import java.rmi.Naming;
import java.rmi.ConnectException;

/**
 * A utility to stop runaway rmi servers.
 *
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 */
public class StopRMIServer
{
   // Constants -----------------------------------------------------

   private static final Logger log = Logger.getLogger(StopRMIServer.class);

   // Static --------------------------------------------------------

   public static void main(String[] args) throws Exception
   {

      String host = System.getProperty("test.bind.address");
      if (host == null)
      {
         host = "localhost";
      }
      
      int index;
      String registryIndex = System.getProperty("test.registry.index");
      if (registryIndex == null)
      {
         //Use the 0th port
         index = 0;
      }
      else
      {
         index = Integer.parseInt(registryIndex);         
      }
      int port = RMITestServer.RMI_REGISTRY_PORTS[index];

      String name =
         "//" + host + ":" + port + "/" + RMITestServer.RMI_SERVER_NAME;

      log.info("Stopping " + name);

      Server server;
      try
      {
         server = (Server)Naming.lookup(name);
      }
      catch(ConnectException e)
      {
         log.info("Cannot contact the registry, the server is probably shut down already");
         return;
      }

      //We should shut down cleanly - not kill the process like we are currently doing
      
      server.destroy();
      log.info("RMI server stopped");

   }

   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
