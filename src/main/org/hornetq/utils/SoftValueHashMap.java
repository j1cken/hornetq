/*
 * Copyright 2010 Red Hat, Inc.
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

package org.hornetq.utils;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A SoftValueConcurrentHashMap
 *
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 *
 *
 */
public class SoftValueHashMap<K, V> implements Map<K, V>
{
   // The soft references that are already good.
   // too bad there's no way to override the queue method on ReferenceQueue, so I wouldn't need this
   private final ReferenceQueue<V> refQueue = new ReferenceQueue<V>();

   private final Map<K, AggregatedSoftReference> mapDelegate = new HashMap<K, AggregatedSoftReference>();

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   /**
    * @return
    * @see java.util.Map#size()
    */
   public int size()
   {
      processQueue();
      return mapDelegate.size();
   }

   /**
    * @return
    * @see java.util.Map#isEmpty()
    */
   public boolean isEmpty()
   {
      processQueue();
      return mapDelegate.isEmpty();
   }

   /**
    * @param key
    * @return
    * @see java.util.Map#containsKey(java.lang.Object)
    */
   public boolean containsKey(final Object key)
   {
      processQueue();
      return mapDelegate.containsKey(key);
   }

   /**
    * @param value
    * @return
    * @see java.util.Map#containsValue(java.lang.Object)
    */
   public boolean containsValue(final Object value)
   {
      processQueue();
      for (AggregatedSoftReference valueIter : mapDelegate.values())
      {
         V valueElement = valueIter.get();
         if (valueElement != null && value.equals(valueElement))
         {
            return true;
         }

      }
      return false;
   }

   /**
    * @param key
    * @return
    * @see java.util.Map#get(java.lang.Object)
    */
   public V get(final Object key)
   {
      processQueue();
      AggregatedSoftReference value = mapDelegate.get(key);
      if (value != null)
      {
         return value.get();
      }
      else
      {
         return null;
      }
   }

   /**
    * @param key
    * @param value
    * @return
    * @see java.util.Map#put(java.lang.Object, java.lang.Object)
    */
   public V put(final K key, final V value)
   {
      processQueue();
      AggregatedSoftReference refPut = mapDelegate.put(key, createReference(key, value));
      if (refPut != null)
      {
         return refPut.get();
      }
      else
      {
         return null;
      }
   }

   /**
    * @param key
    * @return
    * @see java.util.Map#remove(java.lang.Object)
    */
   public V remove(final Object key)
   {
      processQueue();
      AggregatedSoftReference ref = mapDelegate.remove(key);
      if (ref != null)
      {
         return ref.get();
      }
      else
      {
         return null;
      }
   }

   /**
    * @param m
    * @see java.util.Map#putAll(java.util.Map)
    */
   public void putAll(final Map<? extends K, ? extends V> m)
   {
      processQueue();
      for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
      {
         put(e.getKey(), e.getValue());
      }
   }

   /**
    * 
    * @see java.util.Map#clear()
    */
   public void clear()
   {
      mapDelegate.clear();
   }

   /**
    * @return
    * @see java.util.Map#keySet()
    */
   public Set<K> keySet()
   {
      processQueue();
      return mapDelegate.keySet();
   }

   /**
    * @return
    * @see java.util.Map#values()
    */
   public Collection<V> values()
   {
      processQueue();
      ArrayList<V> list = new ArrayList<V>();

      for (AggregatedSoftReference refs : mapDelegate.values())
      {
         V value = refs.get();
         if (value != null)
         {
            list.add(value);
         }
      }

      return list;
   }

   /**
    * @return
    * @see java.util.Map#entrySet()
    */
   public Set<java.util.Map.Entry<K, V>> entrySet()
   {
      processQueue();
      HashSet<Map.Entry<K, V>> set = new HashSet<Map.Entry<K, V>>();
      for (Map.Entry<K, AggregatedSoftReference> pair : mapDelegate.entrySet())
      {
         V value = pair.getValue().get();
         if (value != null)
         {
            set.add(new EntryElement<K,V>(pair.getKey(), value));
         }
      }
      return set;
   }

   /**
    * @param o
    * @return
    * @see java.util.Map#equals(java.lang.Object)
    */
   @Override
   public boolean equals(final Object o)
   {
      processQueue();
      return mapDelegate.equals(o);
   }

   /**
    * @return
    * @see java.util.Map#hashCode()
    */
   @Override
   public int hashCode()
   {
      return mapDelegate.hashCode();
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   @SuppressWarnings("unchecked")
   private void processQueue()
   {
      AggregatedSoftReference ref = null;
      while ((ref = (AggregatedSoftReference)this.refQueue.poll()) != null)
      {
         mapDelegate.remove(ref.key);
      }
   }

   private AggregatedSoftReference createReference(final K key, final V value)
   {
      AggregatedSoftReference ref = new AggregatedSoftReference(key, value);
      return ref;
   }

   // Inner classes -------------------------------------------------

   class AggregatedSoftReference extends SoftReference<V>
   {
      final K key;

      public AggregatedSoftReference(final K key, final V referent)
      {
         super(referent, refQueue);
         this.key = key;
      }
   }

   static final class EntryElement<K, V> implements Map.Entry<K, V>
   {
      final K key;

      volatile V value;

      EntryElement(final K key, final V value)
      {
         this.key = key;
         this.value = value;
      }

      /* (non-Javadoc)
       * @see java.util.Map.Entry#getKey()
       */
      public K getKey()
      {
         return key;
      }

      /* (non-Javadoc)
       * @see java.util.Map.Entry#getValue()
       */
      public V getValue()
      {
         return value;
      }

      /* (non-Javadoc)
       * @see java.util.Map.Entry#setValue(java.lang.Object)
       */
      public V setValue(final V value)
      {
         this.value = value;
         return value;
      }
   }

}
