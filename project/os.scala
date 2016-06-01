package org.scalameta
package os

import java.io._
import java.nio.file._
import scala.sys.process._
import scala.compat.Platform.EOL

object shell {
  def exec(command: String, cwd: String = "."): (Int, String, String) = {
    def slurp(stream: InputStream): String = {
      val reader = new BufferedReader(new InputStreamReader(stream))
      val builder = new StringBuilder()
      var done = false
      while (!done) {
        val line = reader.readLine()
        if (line != null) builder.append(line + EOL)
        else done = true
      }
      builder.toString
    }
    val p = Runtime.getRuntime.exec(command, null, new File(cwd))
    val exitcode = p.waitFor()
    val stdout = slurp(p.getInputStream) // lol at the naming
    val stderr = slurp(p.getErrorStream)
    (exitcode, stdout, stderr)
  }

  def call(command: String, cwd: String = "."): Unit = {
    val sfwCommand = command.replaceAll("https://(.*?):(.*)@", "https://***:***@");
    println("running " + sfwCommand + " in " + new File(cwd).getAbsolutePath)
    val (exitcode, stdout, stderr) = shell.exec(command, cwd)
    if (exitcode != 0) sys.error(s"$command returned $exitcode:$EOL$stdout$EOL$stderr")
  }
}

object secret {
  def obtain(domain: String): Option[(String, String)] = {
    val credentialsFile = System.getProperty(domain + ".settings.file")
    if (credentialsFile != null) {
      try {
        import scala.xml._
        val settings = XML.loadFile(credentialsFile)
        def readServerConfig(key: String) = (settings \\ "settings" \\ "servers" \\ "server" \\ key).head.text
        Some((readServerConfig("username"), readServerConfig("password")))
      } catch {
        case ex: Exception => None
      }
    } else {
      for {
        username <- sys.env.get(s"${domain.toUpperCase}_USERNAME")
        password <- sys.env.get(s"${domain.toUpperCase}_PASSWORD")
      } yield {
        (username, password)
      }
    }
  }
}

object temp {
  def mkdir(): File = {
    val temp = File.createTempFile("temp", System.nanoTime.toString)
    if (!temp.delete) sys.error("failed to create a temporary directory: can't delete " + temp.getAbsolutePath)
    if (!temp.mkdir) sys.error("failed to create a temporary directory: can't mkdir " + temp.getAbsolutePath)
    temp
  }
}

object shutil {
  def rmtree(file: File): Unit = {
    if (file.isDirectory) file.listFiles.foreach(rmtree)
    if (!file.delete) sys.error(s"failed to delete ${file.getAbsolutePath}")
  }

  def copytree(src: File, dest: File): Unit = {
    if (src.isDirectory) {
      if (!dest.mkdirs) sys.error(s"failed to create ${dest.getAbsolutePath}")
      src.listFiles.foreach(srcsub => {
        val destsub = new File(dest.getAbsolutePath + File.separator + srcsub.getName)
        copytree(srcsub, destsub)
      })
    } else {
      Files.copy(src.toPath, dest.toPath)
    }
  }
}