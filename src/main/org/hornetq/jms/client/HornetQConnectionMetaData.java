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

package org.hornetq.jms.client;

import java.util.Enumeration;
import java.util.Vector;

import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;

import org.hornetq.core.version.Version;

/**
 * HornetQ implementation of a JMS ConnectionMetaData.
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 *
 * $Id$
 */
public class HornetQConnectionMetaData implements ConnectionMetaData
{
   // Constants -----------------------------------------------------

   public static final String JBOSS_MESSAGING = "HornetQ";

   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   private final Version serverVersion;

   // Constructors --------------------------------------------------

   /**
    * Create a new HornetQConnectionMetaData object.
    */
   public HornetQConnectionMetaData(final Version serverVersion)
   {
      this.serverVersion = serverVersion;
   }

   // ConnectionMetaData implementation -----------------------------

   public String getJMSVersion() throws JMSException
   {
      return "1.1";
   }

   public int getJMSMajorVersion() throws JMSException
   {
      return 1;
   }

   public int getJMSMinorVersion() throws JMSException
   {
      return 1;
   }

   public String getJMSProviderName() throws JMSException
   {
      return HornetQConnectionMetaData.JBOSS_MESSAGING;
   }

   public String getProviderVersion() throws JMSException
   {
      return serverVersion.getFullVersion();
   }

   public int getProviderMajorVersion() throws JMSException
   {
      return serverVersion.getMajorVersion();
   }

   public int getProviderMinorVersion() throws JMSException
   {
      return serverVersion.getMinorVersion();
   }

   public Enumeration getJMSXPropertyNames() throws JMSException
   {
      Vector v = new Vector();
      v.add("JMSXGroupID");
      // v.add("JMSXGroupSeq");
      v.add("JMSXDeliveryCount");
      return v.elements();
   }

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
