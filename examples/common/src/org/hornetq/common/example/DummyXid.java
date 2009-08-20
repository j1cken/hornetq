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
package org.hornetq.common.example;

import org.hornetq.utils.Base64;

import javax.transaction.xa.Xid;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class DummyXid implements Xid
{
   private static final long serialVersionUID = 407053232840068514L;

   private byte[] branchQualifier;

   private int formatId;

   private byte[] globalTransactionId;

   private int hash;

   private boolean hashCalculated;

   // Static --------------------------------------------------------

   public static String toBase64String(final Xid xid)
   {
      return Base64.encodeBytes(toByteArray(xid));
   }

   private static byte[] toByteArray(final Xid xid)
   {
      byte[] branchQualifier = xid.getBranchQualifier();
      byte[] globalTransactionId = xid.getGlobalTransactionId();
      int formatId = xid.getFormatId();

      byte[] hashBytes = new byte[branchQualifier.length + globalTransactionId.length + 4];
      System.arraycopy(branchQualifier, 0, hashBytes, 0, branchQualifier.length);
      System.arraycopy(globalTransactionId, 0, hashBytes, branchQualifier.length, globalTransactionId.length);
      byte[] intBytes = new byte[4];
      for (int i = 0; i < 4; i++)
      {
         intBytes[i] = (byte)((formatId >> (i * 8)) % 0xFF);
      }
      System.arraycopy(intBytes, 0, hashBytes, branchQualifier.length + globalTransactionId.length, 4);
      return hashBytes;
   }

   // Constructors --------------------------------------------------

   /**
    * Standard constructor
    * @param branchQualifier
    * @param formatId
    * @param globalTransactionId
    */
   public DummyXid(final byte[] branchQualifier, final int formatId, final byte[] globalTransactionId)
   {
      this.branchQualifier = branchQualifier;
      this.formatId = formatId;
      this.globalTransactionId = globalTransactionId;
   }

   /**
    * Copy constructor
    * @param other
    */
   public DummyXid(final Xid other)
   {
      this.branchQualifier = copyBytes(other.getBranchQualifier());
      this.formatId = other.getFormatId();
      this.globalTransactionId = copyBytes(other.getGlobalTransactionId());
   }

   // Xid implementation ------------------------------------------------------------------

   public byte[] getBranchQualifier()
   {
      return branchQualifier;
   }

   public int getFormatId()
   {
      return formatId;
   }

   public byte[] getGlobalTransactionId()
   {
      return globalTransactionId;
   }

   // Public -------------------------------------------------------------------------------

   public int hashCode()
   {
      if (!hashCalculated)
      {
         calcHash();
      }
      return hash;
   }

   public boolean equals(Object other)
   {
      if (this == other)
      {
         return true;
      }
      if (!(other instanceof Xid))
      {
         return false;
      }
      Xid xother = (Xid)other;
      if (xother.getFormatId() != formatId)
      {
         return false;
      }
      if (xother.getBranchQualifier().length != this.branchQualifier.length)
      {
         return false;
      }
      if (xother.getGlobalTransactionId().length != this.globalTransactionId.length)
      {
         return false;
      }
      for (int i = 0; i < this.branchQualifier.length; i++)
      {
         byte[] otherBQ = xother.getBranchQualifier();
         if (this.branchQualifier[i] != otherBQ[i])
         {
            return false;
         }
      }
      for (int i = 0; i < this.globalTransactionId.length; i++)
      {
         byte[] otherGtx = xother.getGlobalTransactionId();
         if (this.globalTransactionId[i] != otherGtx[i])
         {
            return false;
         }
      }
      return true;
   }

   public String toString()
   {
     	return "XidImpl (" + System.identityHashCode(this) + " bq:" + stringRep(branchQualifier) +
     	" formatID:" + formatId + " gtxid:" + stringRep(globalTransactionId);
   }

   // Private -------------------------------------------------------------------------------

   private String stringRep(final byte[] bytes)
   {
      StringBuffer buff = new StringBuffer();
      for (int i = 0; i < bytes.length; i++)
      {
         byte b = bytes[i];

         buff.append(b);

         if (i != bytes.length - 1)
         {
            buff.append('.');
         }
      }

      return buff.toString();
   }

   private void calcHash()
   {
      byte[] hashBytes = toByteArray(this);
      String s = new String(hashBytes);
      hash = s.hashCode();
      hashCalculated = true;
   }

   private byte[] copyBytes(byte[] other)
   {
      byte[] bytes = new byte[other.length];

      System.arraycopy(other, 0, bytes, 0, other.length);

      return bytes;
   }
}
