File Manager Servlet
====================

![File Manager Servlet](https://github.com/kristian/file-manager-servlet/raw/master/FOLDER.png "Screenshot File Manager Servlet")

This project provides a very simple, single file, easy to use Java Servlet featuring a fully functional server side file manager. The user interface is very minimalistic and resembles an old command line file manager, including:

 - Browsing all root partitions
 - Display of files and folders
 - File up- and download (ZIP file support)
 - Recursive file name search
 - Deleting files and setting file flags (+rwx)

Usage
-----

You have to add the servlet class `lc.kra.servlet.FileManagerServlet` you your dynamic web project. Additionally you'll need to add the [ZT-ZIP](https://github.com/zeroturnaround/zt-zip) and [Commons FileUpload](https://commons.apache.org/proper/commons-fileupload/) libraries to your project. Last, provide a servlet mapping in your `WEB-INF/web.xml` configuration, like:

```xml
<servlet>
  <display-name>File Manager Servlet</display-name>
  <servlet-name>FileManagerServlet</servlet-name>
  <servlet-class>lc.kra.servlet.FileManagerServlet</servlet-class>
</servlet>
<servlet-mapping>
  <servlet-name>FileManagerServlet</servlet-name>
  <url-pattern>/</url-pattern>
</servlet-mapping>
```

**Attention**: The servlet is unprotected by default and provides full access to the file system. Be sure to protect the servlet path or just use it in securely protected environments. Consider adding any security constraint to your `WEB-INF/web.xml` file:

```xml
<security-constraint>
  <web-resource-collection>
    <web-resource-name>File Manager Servlet</web-resource-name>
    <url-pattern>/*</url-pattern>
  </web-resource-collection>
  <auth-constraint>
    <role-name>Administrator</role-name>
  </auth-constraint>
</security-constraint>
<security-role>
  <description>Administrators</description>
  <role-name>Administrator</role-name>
</security-role>
```

### Maven Dependency
Even easier, you can include file-manager-servlet from this GitHub repository by adding this dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>lc.kra.servlet</groupId>
  <artifactId>file-manager-servlet</artifactId>
  <version>0.1.0</version>
</dependency>
```

Additionally you will have to add the following repository to your `pom.xml`:

```xml
<repositories>
  <repository>
    <id>file-manager-servlet-mvn-repo</id>
    <url>https://raw.github.com/kristian/file-manager-servlet/mvn-repo/</url>
    <snapshots>
      <enabled>true</enabled>
      <updatePolicy>always</updatePolicy>
    </snapshots>
  </repository>
</repositories>
```

Build
-----

To build file-manager-servlet on your machine, checkout the repository, `cd` into it, and call:
```
mvn clean install
```

License
-------

The code is available under the terms of the [MIT License](http://opensource.org/licenses/MIT).