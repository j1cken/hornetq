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
package org.jboss.test.messaging.jms;

import org.jboss.test.messaging.MessagingTestCase;
import org.jboss.test.messaging.tools.jmx.rmi.Server;
import org.jboss.test.messaging.tools.jmx.rmi.LocalTestServer;
import org.jboss.test.messaging.tools.ServerManagement;
import org.jboss.logging.Logger;

import javax.naming.InitialContext;
import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.jms.Connection;
import javax.jms.Session;
import javax.jms.MessageConsumer;
import javax.jms.TextMessage;
import java.io.ObjectOutputStream;
import java.io.FileOutputStream;
import java.io.File;

/**
 * A test that makes sure that a Messaging client gracefully exists after the last connection is
 * closed. Test for http://jira.jboss.org/jira/browse/JBMESSAGING-417.
 *
 * This is not technically a crash test, but it uses the same type of topology as the crash tests
 * (local server, remote VM client).
 *
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version 1.1
 *
 * $Id$
 */
public class ClientExitTest extends MessagingTestCase
{
   // Constants ------------------------------------------------------------------------------------

   public static final String SERIALIZED_CF_FILE_NAME = "CFandQueue.ser";
   public static final String MESSAGE_TEXT = "kolowalu";

   // Static ---------------------------------------------------------------------------------------

   private static final Logger log = Logger.getLogger(ClientExitTest.class);

   // Attributes -----------------------------------------------------------------------------------

   // Constructors ---------------------------------------------------------------------------------

   public ClientExitTest(String name)
   {
      super(name);
   }

   // Public ---------------------------------------------------------------------------------------

   public void testGracefulClientExit() throws Exception
   {
      if (ServerManagement.isRemote())
      {
         // doesn't make any sense to run in remote mode, since we'll start our won external VM
         return;
      }

      Server localServer = null;
      File serialized = null;

      try
      {
         localServer = new LocalTestServer();

         localServer.start("all", true);
         localServer.deployQueue("Queue", null, false);

         // lookup the connection factory and the queue which we'll send to the client VM via a
         // serialized instances saved in file

         InitialContext ic = new InitialContext(ServerManagement.getJNDIEnvironment());
         ConnectionFactory cf = (ConnectionFactory)ic.lookup("/ConnectionFactory");
         Queue queue = (Queue)ic.lookup("/queue/Queue");

         serialized = writeToFile(cf, queue);

         // spawn a JVM that creates a JMS client, which sends a test message

         Process p = spawnVM(serialized);

         // read the message from the queue

         Connection conn = cf.createConnection();
         conn.start();
         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons = sess.createConsumer(queue);
         TextMessage tm = (TextMessage)cons.receive(5000);

         assertNotNull(tm);
         assertEquals(MESSAGE_TEXT, tm.getText());

         // the client VM should exit by itself. If it doesn't, that means we have a problem
         // and the test will timeout
         log.info("waiting for the client VM to exit ...");
         p.waitFor();

         assertEquals(0, p.exitValue());

      }
      finally
      {
         // TODO delete the file
         if (serialized != null)
         {
            serialized.delete();
         }

         localServer.undeployDestination(true, "Queue");
         localServer.stopServerPeer();
         localServer.stop();
      }
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   protected void setUp() throws Exception
   {
      super.setUp();
   }

   protected void tearDown() throws Exception
   {
      super.tearDown();
   }

   // Private --------------------------------------------------------------------------------------

   private Process spawnVM(File serialized) throws Exception
   {
      StringBuffer sb = new StringBuffer();

      sb.append("java").append(' ');

      String classPath = System.getProperty("java.class.path");

      if (System.getProperty("os.name").equals("Linux"))
      {
         sb.append("-cp").append(" ").append(classPath).append(" ");
      }
      else
      {
         sb.append("-cp").append(" \"").append(classPath).append("\" ");
      }

      sb.append("org.jboss.test.messaging.jms.GracefulClient ");

      // the first argument
      sb.append(serialized.getAbsolutePath());

      String commandLine = sb.toString();

      //System.out.println(commandLine);

      Process process = Runtime.getRuntime().exec(commandLine);

      log.trace("process: " + process);

//      final boolean verbose = true;
//
//      final BufferedReader rs = new BufferedReader(new InputStreamReader(process.getInputStream()));
//      final BufferedReader re = new BufferedReader(new InputStreamReader(process.getErrorStream()));
//
//      new Thread(new Runnable()
//      {
//         public void run()
//         {
//            try
//            {
//               String line;
//
//               while((line = rs.readLine()) != null)
//               {
//                  if (verbose)
//                  {
//                     System.out.println("GRACEFUL CLIENT STDOUT: " + line);
//                  }
//               }
//            }
//            catch(Exception e)
//            {
//               log.error("exception", e);
//            }
//         }
//
//      }, "GRACEFUL CLIENT STDOUT reader thread").start();
//
//      new Thread(new Runnable()
//      {
//         public void run()
//         {
//            try
//            {
//               String line;
//
//               while((line = re.readLine()) != null)
//               {
//                  if (verbose)
//                  {
//                     System.out.println("GRACEFUL CLIENT  STDERR: " + line);
//                  }
//               }
//            }
//            catch(Exception e)
//            {
//               log.error("exception", e);
//            }
//         }
//
//      }, "GRACEFUL CLIENT STDERR reader thread").start();

      return process;
   }

   private File writeToFile(ConnectionFactory cf, Queue queue) throws Exception
   {
      String moduleOutput = System.getProperty("module.output");
      if (moduleOutput == null)
      {
         throw new Exception("Can't find 'module.output'");
      }
      File dir = new File(moduleOutput);

      if (!dir.isDirectory() || !dir.canWrite())
      {
         throw new Exception(dir + " is either not a directory or not writable");
      }

      File file = new File(dir, SERIALIZED_CF_FILE_NAME);

      ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
      oos.writeObject(cf);
      oos.writeObject(queue);
      oos.flush();
      oos.close();

      return file;
   }

   // Inner classes --------------------------------------------------------------------------------

}
