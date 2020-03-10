package com.zaxxer.hikari;


import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ConcurrentHashMap;

public class TestReferenceQueue {

   public static ConcurrentHashMap<TestPhantomReference, TestPhantomReference> connectionPhantomRefs = new ConcurrentHashMap<>();
   public static ReferenceQueue<Object> refQueue = new ReferenceQueue<>();


   public static void main(String[] args) {
      // 类比创建一个数据库连接对象
      Object instanceA = new Object();
      //将数据库连接池对象封装到connectionPhantomRefs对象中
      TestPhantomReference phantomRef = new TestPhantomReference(instanceA,refQueue);
      connectionPhantomRefs.put(phantomRef,phantomRef);
      // 数据库连接超过最大存活时间,将数据库连接关闭,已经不需要再使用了，可以被回收了
      instanceA = null;
      try {
         //未发生gc之前查看幽灵引用中的数据
         Reference<?> remove = refQueue.remove(1000);
         System.out.println(remove);
         // GC发现了这个对象可以被回收，如果对象覆盖了finalize方法还需要执行finalize方法
         //发生GC之后查看幽灵引用中的数据
         System.gc();
         Reference<?> reference = refQueue.remove(5000);
         System.out.println(reference);
      } catch (InterruptedException e) {
         e.printStackTrace();
      }

   }

   /**
    * 数据库连接对象的封装
    */
   static class TestPhantomReference extends PhantomReference<Object> {

      TestPhantomReference(Object Object, ReferenceQueue<Object> q) {
         super(Object, q);

      }

   }

}
