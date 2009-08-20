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

package org.hornetq.jmstests.util;

import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;

import org.hornetq.jmstests.HornetQServerTestCase;
import org.hornetq.utils.JNDIUtil;


/**
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 */
public class JNDIUtilTest extends HornetQServerTestCase
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   private InitialContext ic;

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testRebind_1() throws Exception
   {
      try
      {
         ic.lookup("/nosuchsubcontext");
         fail("the name is not supposed to be there");
      }
      catch(NameNotFoundException e)
      {
         // OK
      }

      JNDIUtil.rebind(ic, "/nosuchsubcontext/sub1/sub2/sub3/name", new Integer(7));

      assertEquals(7, ((Integer)ic.lookup("/nosuchsubcontext/sub1/sub2/sub3/name")).intValue());
   }

   public void testRebind_2() throws Exception
   {
      try
      {
         ic.lookup("/doesnotexistyet");
         fail("the name is not supposed to be there");
      }
      catch(NameNotFoundException e)
      {
         // OK
      }

      JNDIUtil.rebind(ic, "/doesnotexistyet", new Integer(8));

      assertEquals(8, ((Integer)ic.lookup("/doesnotexistyet")).intValue());

      ic.unbind("doesnotexistyet");
   }

   public void testRebind_3() throws Exception
   {
      try
      {
         ic.lookup("doesnotexistyet");
         fail("the name is not supposed to be there");
      }
      catch(NameNotFoundException e)
      {
         // OK
      }

      JNDIUtil.rebind(ic, "doesnotexistyet", new Integer(9));

      assertEquals(9, ((Integer)ic.lookup("/doesnotexistyet")).intValue());

      ic.unbind("doesnotexistyet");
   }



   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   protected void setUp() throws Exception
   {
      super.setUp();

      //ServerManagement.start("none");

      ic = getInitialContext();

      log.debug("setup done");
   }


   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
