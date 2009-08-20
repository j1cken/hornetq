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

package org.hornetq.jmstests;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.IllegalStateException;
import javax.jms.JMSSecurityException;
import javax.jms.Session;
import java.util.ArrayList;

/**
 * Test JMS Security.
 *
 * This test must be run with the Test security config. on the server
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 *
 * Much of the basic idea of the tests come from SecurityUnitTestCase.java in JBossMQ by:
 * @author <a href="pra@tim.se">Peter Antman</a>
 *
 *
 * @version <tt>$Revision$</tt>
 *
 */
public class SecurityTest extends JMSTestCase
{
   // Constants -----------------------------------------------------


   // Static --------------------------------------------------------

   // Attributes --------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   /**
    * Login with no user, no password
    * Should allow login (equivalent to guest)
    */
   public void testLoginNoUserNoPassword() throws Exception
   {

      Connection conn1 = null;
      Connection conn2 = null;
      try
      {
         conn1 = cf.createConnection();
         conn2 = cf.createConnection(null, null);
      }
      finally
      {
         if (conn1 != null)
            conn1.close();
         if (conn2 != null)
            conn2.close();
      }
   }

   /**
    * Login with no user, no password
    * Should allow login (equivalent to guest)
    */
   public void testLoginNoUserNoPasswordWithNoGuest() throws Exception
   {

      Connection conn1 = null;
      Connection conn2 = null;
      try
      {
         conn1 = cf.createConnection();
         conn2 = cf.createConnection(null, null);
      }
      finally
      {
         if (conn1 != null)
            conn1.close();
         if (conn2 != null)
            conn2.close();
      }
   }

   /**
    * Login with valid user and password
    * Should allow
    */
   public void testLoginValidUserAndPassword() throws Exception
   {
      Connection conn1 = null;
      try
      {
         conn1 = cf.createConnection("guest", "guest");
      }
      finally
      {
         if (conn1 != null)
            conn1.close();
      }
   }

   /**
    * Login with valid user and invalid password
    * Should allow
    */
   public void testLoginValidUserInvalidPassword() throws Exception
   {
      Connection conn1 = null;
      try
      {
         conn1 = cf.createConnection("guest", "not.the.valid.password");
         fail();
      }
      catch (JMSSecurityException e)
      {
         // Expected
      }
      finally
      {
         if (conn1 != null)
            conn1.close();
      }
   }

   /**
    * Login with invalid user and invalid password
    * Should allow
    */
   public void testLoginInvalidUserInvalidPassword() throws Exception
   {
      Connection conn1 = null;
      try
      {
         conn1 = cf.createConnection("not.the.valid.user", "not.the.valid.password");
         fail();
      }
      catch (JMSSecurityException e)
      {
         // Expected
      }
      finally
      {
         if (conn1 != null)
            conn1.close();
      }
   }

   /* Now some client id tests */

   /**
    * user/pwd with preconfigured clientID, should return preconf
    */
   public void testPreConfClientID() throws Exception
   {
      Connection conn = null;
      try
      {
         ArrayList<String> bindings = new ArrayList<String>();
         bindings.add("preConfcf");
         deployConnectionFactory("dilbert-id", "preConfcf", bindings);
         ConnectionFactory cf = (ConnectionFactory)getInitialContext().lookup("preConfcf");
         conn = cf.createConnection("guest", "guest");
         String clientID = conn.getClientID();
         assertEquals("Invalid ClientID", "dilbert-id", clientID);
      }
      finally
      {
         if (conn != null)
            conn.close();
         undeployConnectionFactory("preConfcf");
      }
   }

   /**
    * Try setting client ID
    */
   public void testSetClientID() throws Exception
   {
      Connection conn = null;
      try
      {
         conn = cf.createConnection();
         conn.setClientID("myID");
         String clientID = conn.getClientID();
         assertEquals("Invalid ClientID", "myID", clientID);
      }
      finally
      {
         if (conn != null)
            conn.close();
      }
   }

   /**
    * Try setting client ID on preconfigured connection - should throw exception
    */
   public void testSetClientIDPreConf() throws Exception
   {
      Connection conn = null;
      try
      {
         ArrayList<String> bindings = new ArrayList<String>();
         bindings.add("preConfcf");
         deployConnectionFactory("dilbert-id", "preConfcf", bindings);
         ConnectionFactory cf = (ConnectionFactory)getInitialContext().lookup("preConfcf");
         conn = cf.createConnection("guest", "guest");
         conn.setClientID("myID");
         fail();
      }
      catch (IllegalStateException e)
      {
         // Expected
      }
      finally
      {
         if (conn != null)
            conn.close();
         undeployConnectionFactory("preConfcf");
      }
   }

   /*
    * Try setting client ID after an operation has been performed on the connection
    */
   public void testSetClientIDAfterOp() throws Exception
   {
      Connection conn = null;
      try
      {
         conn = cf.createConnection();
         conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         conn.setClientID("myID");
         fail();
      }
      catch (IllegalStateException e)
      {
         // Expected
      }
      finally
      {
         if (conn != null)
            conn.close();
      }
   }



   // Private -------------------------------------------------------


   
   // Inner classes -------------------------------------------------

}
