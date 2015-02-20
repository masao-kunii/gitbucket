import sbt._
import Keys._
import org.scalatra.sbt._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys
import play.twirl.sbt.SbtTwirl
import play.twirl.sbt.Import.TwirlKeys._

object MyBuild extends Build {
  val Organization = "jp.sf.amateras"
  val Name = "gitbucket"
  val Version = "0.0.1"
  val ScalaVersion = "2.11.2"
  val ScalatraVersion = "2.3.0"

  lazy val project = Project (
    "gitbucket",
    file(".")
  )
  .settings(ScalatraPlugin.scalatraWithJRebel: _*)
  .settings(
    sourcesInBase := false,
    organization := Organization,
    name := Name,
    version := Version,
    scalaVersion := ScalaVersion,
    resolvers ++= Seq(
      Classpaths.typesafeReleases,
      "amateras-repo" at "http://amateras.sourceforge.jp/mvn/"
    ),
      classpathTypes ~= (_ + "orbit"),
    resolvers += "Akka Repo" at "http://repo.akka.io/repository",
    scalacOptions := Seq("-deprecation", "-language:postfixOps"),
    libraryDependencies ++= Seq(
      "org.eclipse.jgit" % "org.eclipse.jgit.http.server" % "3.4.1.201406201815-r",
      "org.eclipse.jgit" % "org.eclipse.jgit.archive" % "3.4.1.201406201815-r",
      "org.scalatra" %% "scalatra" % ScalatraVersion,
      "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
      "org.scalatra" %% "scalatra-json" % ScalatraVersion,
      "org.scalatra" %% "scalatra-atmosphere" % ScalatraVersion,
      "org.json4s" %% "json4s-jackson" % "3.2.10",
      "jp.sf.amateras" %% "scalatra-forms" % "0.1.0",
      "commons-io" % "commons-io" % "2.4",
      "org.pegdown" % "pegdown" % "1.4.1",
      "org.apache.commons" % "commons-compress" % "1.5",
      "org.apache.commons" % "commons-email" % "1.3.1",
      "org.apache.httpcomponents" % "httpclient" % "4.3",
      "org.apache.sshd" % "apache-sshd" % "0.11.0",
      "com.typesafe.slick" %% "slick" % "2.1.0",
      "com.novell.ldap" % "jldap" % "2009-10-07",
      "com.h2database" % "h2" % "1.4.180",
      "ch.qos.logback" % "logback-classic" % "1.0.13" % "runtime",
      "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "container;provided",
      "org.eclipse.jetty" % "jetty-websocket" % "8.1.8.v20121106",
      "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts Artifact("javax.servlet", "jar", "jar"),
      "junit" % "junit" % "4.11" % "test",
      "com.mchange" % "c3p0" % "0.9.5",
      "com.typesafe" % "config" % "1.2.1",
      "com.typesafe.play" %% "twirl-compiler" % "1.0.2",
      "com.typesafe.akka" %% "akka-actor" % "2.3.9",
      "com.typesafe.akka" %% "akka-testkit" % "2.3.9"
    ),
    EclipseKeys.withSource := true,
    javacOptions in compile ++= Seq("-target", "7", "-source", "7"),
    testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console"),
    packageOptions += Package.MainClass("JettyLauncher")
  ).enablePlugins(SbtTwirl)
}
