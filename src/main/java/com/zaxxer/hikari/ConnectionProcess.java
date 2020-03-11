package com.zaxxer.hikari;


import com.mysql.cj.conf.ConnectionUrl;
import com.mysql.cj.conf.HostInfo;
import com.mysql.cj.jdbc.ConnectionImpl;
import com.mysql.cj.jdbc.NonRegisteringDriver;
import com.zaxxer.hikari.pool.HikariPool;
import com.zaxxer.hikari.util.DriverDataSource;

import java.lang.ref.Reference;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Properties;


public class ConnectionProcess {

   public static void main(String[] args) {
      /**
       * 1.调用{@link HikariDataSource#getConnection()} 方法，
       * 然后调用{@link com.zaxxer.hikari.pool.HikariPool#HikariPool(HikariConfig)}的构造器创建一个HikariPool对象。
       * 核心代码如下：
       */
      HikariPool hikariPool = new HikariPool(new HikariDataSource());
      /**
       * 2.HikariPool {@link HikariPool#HikariPool(HikariConfig)}继承自
       * {@link PoolBase},创建HikariPool对象是会调用{@link com.zaxxer.hikari.pool.PoolBase#PoolBase(HikariConfig)}方法。
       * PoolBase构造器中有一个关键的方法是{@link com.zaxxer.hikari.pool.PoolBase#initializeDataSource()}方法。
       * 这个方法用来寻找对应的驱动从而初始化对应的{@link DriverDataSource}对象。找到的最终驱动为{@link com.mysql.cj.jdbc.Driver}
       * 寻找驱动的核心代码如下：
       */
      Enumeration<Driver> drivers = DriverManager.getDrivers();
      while (drivers.hasMoreElements()) {
         Driver d = drivers.nextElement();
         System.out.println(d.getClass().getName());
      }
      /**
       * 3.初始化完成{@link DriverDataSource}对象之后，回到{@link HikariPool#HikariPool(HikariConfig)}方法，会经历一个很重要的方法
       * 也是出现问题的地方。{@link HikariPool#checkFailFast()}方法，这个方法用来快速创建数据库连接,将数据库连接封装成一个{@link com.zaxxer.hikari.pool.PoolEntry}对象。
       * 对象封装了数据库连接以及上次访问和上次借用的时间，方便进行超时销毁。
       * 核心代码如下：
       */
      //PoolEntry poolEntry = newPoolEntry();//PoolEntry为非public对象，故只能将核心代码以注释的形式体现
      /**
       * 4.在调用{@link com.zaxxer.hikari.pool.PoolBase#newPoolEntry()}方法时
       * 会调用{@link com.zaxxer.hikari.pool.PoolBase#newConnection()}方法创建一个数据库连接对象。
       * 核心代码如下：
       */
//      connection = (username == null) ? dataSource.getConnection() : dataSource.getConnection(username, password);

      /**
       * 5.创建数据库连接调用是{@link DriverDataSource#getConnection()}或{@link DriverDataSource#getConnection(String, String)}方法。
       * 进而最终调用到了数据库驱动的方法，即{@link com.mysql.cj.jdbc.Driver}的方法。由于{@link com.mysql.cj.jdbc.Driver}继承字自{@link com.mysql.cj.jdbc.NonRegisteringDriver}
       * 因此调用了{@link com.mysql.cj.jdbc.NonRegisteringDriver#connect(String, Properties)}方法。
       * 根据jdbcUrl的不同会走不同的switch case.非集群形式的mysql为单一类型
       * 核心代码如下：
       */
      String url = "jdbcUrl";
      Properties info = new Properties();
      ConnectionUrl conStr = ConnectionUrl.getConnectionUrlInstance(url, info);
      try {
         switch (conStr.getType()) {
            case SINGLE_CONNECTION:
               com.mysql.cj.jdbc.ConnectionImpl.getInstance(conStr.getMainHost());
         }
      } catch (SQLException e) {
         e.printStackTrace();
      }
      /**
       * 6.这个地方是最终出现内存泄露的地方。
       * {@link com.mysql.cj.jdbc.ConnectionImpl#getInstance(HostInfo)}方法会调用{@link ConnectionImpl#ConnectionImpl(HostInfo)}的构造器方法。
       * 在构造器中会调用一个方法，这个方法导致了最终内存泄露的出现。
       * 核心代码如下：
       */
      NonRegisteringDriver.trackConnection(this);

      /**
       * 每次创建新的连接，都会将其放入{@link NonRegisteringDriver#connectionPhantomRefs}中,并创建一个虚连接。
       * 由于默认配置的maxPoolSize,minIdle,maxLifetime等参数过小，导致反复创建数据库连接，
       * 进而创建了大量数据库连接，从而导致 connectionPhantomRefs 元素不断增加。
       * 具体出现内存泄露的原因参见 {@link TestReferenceQueue}代码。
       */
      /**
       * {@link NonRegisteringDriver#connectionPhantomRefs}数据源的清楚是{@link com.mysql.cj.jdbc.AbandonedConnectionCleanupThread}负责的。
       * 数据库连接关闭后，数据库连接对象将会被GC清除，
       * 被GC清除后的数据库连接的虚引用就会自动进入到入NonRegisteringDriver.refQueue 队列。
       * 然后执行下面的程序，不断从队列中获取连接的虚引用。
       * 下面程序中的关键代码是cleanup()方法，cleanup()方法的主要作用是关闭网络资源NetworkResources。
       * 最后一步再从connectionPhantomRefs中移除虚引用。
       */
      public void run() {
         for (;;) {
            try {
               checkContextClassLoaders();
               Reference<? extends ConnectionImpl> ref = NonRegisteringDriver.refQueue.remove(5000);
               if (ref != null) {
                  try {
                     ((NonRegisteringDriver.ConnectionPhantomReference) ref).cleanup();
                  } finally {
                     NonRegisteringDriver.connectionPhantomRefs.remove(ref);
                  }
               }

            } catch (InterruptedException e) {
               threadRef = null;
               return;

            } catch (Exception ex) {
               // Nowhere to really log this.
            }
         }
      }
      /**
       * 解决方案：
       * 1.手动chSystem.gc() 方法触发GC。
       * 3.将相关配置时间设置变长，避免数据库连接的反复创建
       */

   }






}
