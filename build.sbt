import net.virtualvoid.sbt.graph.Plugin
import org.scalastyle.sbt.ScalastylePlugin
import NewmanReleaseSteps._
import sbtrelease._
import ReleaseStateTransformations._
import ReleasePlugin._
import ReleaseKeys._
import sbt._

name := "newman"

organization := "com.stackmob"

scalaVersion := "2.11.6"

crossScalaVersions := Seq("2.10.4", "2.11.6")

scalacOptions := Seq("-unchecked", "-deprecation", "-feature")

libraryDependencies ++= {
  val httpCoreVersion = "4.2.5"
  val httpClientVersion = "4.2.5"
  val scalaCheckVersion = "1.11.3"
  val specs2Version = "2.3.13"
  val liftJsonVersion = "2.6"
  val sprayVersion = "1.3.3"
  val akkaVersion = "2.3.9"
  Seq(
    "org.scalaz" %% "scalaz-core" % "7.0.6",
    "org.apache.httpcomponents" % "httpcore" % httpCoreVersion,
    "org.apache.httpcomponents" % "httpclient" % httpClientVersion exclude("org.apache.httpcomponents", "httpcore"),
    "io.spray" %% "spray-client" % sprayVersion,
    "io.spray" %% "spray-caching" % sprayVersion,
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.twitter" %% "finagle-http" % "6.24.1-MONOCACHE" exclude("commons-codec", "commons-codec"),
    "net.liftweb" %% "lift-json-scalaz7" % liftJsonVersion exclude ("org.scalaz", "scalaz-core_2.10"),
    "org.scalacheck" %% "scalacheck" % scalaCheckVersion % "test",
    "org.specs2" %% "specs2" % specs2Version % "test" exclude("org.parboiled", "parboiled-core")
  )
}

testOptions in Test += Tests.Argument("html", "console")

conflictManager := ConflictManager.strict

dependencyOverrides <+= scalaVersion { vsn => "org.scala-lang" % "scala-library" % vsn }

dependencyOverrides <+= scalaVersion { vsn => "org.scala-lang" % "scala-compiler" % vsn }

dependencyOverrides <+= scalaVersion { vsn => "org.scala-lang" % "scala-reflect" % vsn }

dependencyOverrides += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2"

dependencyOverrides += "org.scala-lang.modules" %% "scala-xml" % "1.0.2"

logBuffered := false

ScalastylePlugin.Settings

Plugin.graphSettings

releaseSettings

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  setReadmeReleaseVersion,
  tagRelease,
  publishArtifacts.copy(action = publishSignedAction),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

publishTo <<= version { version: String =>
  val nexus = "https://oss.sonatype.org/"
  if (version.trim.endsWith("SNAPSHOT")) {
    Some("snapshots" at nexus + "content/repositories/snapshots")
   } else {
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}

publishMavenStyle := true

publishArtifact in Test := true

homepage := Some(url("https://github.com/stackmob/newman"))

licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

pomExtra := (
  <scm>
    <url>git@github.com:stackmob/newman.git</url>
    <connection>scm:git:git@github.com:stackmob/newman.git</connection>
  </scm>
  <developers>
    <developer>
      <id>arschles</id>
      <name>Aaron Schlesinger</name>
      <url>http://www.stackmob.com</url>
    </developer>
    <developer>
      <id>devmage</id>
      <name>Andrew Harris</name>
      <url>http://www.stackmob.com</url>
    </developer>
    <developer>
      <id>taylorleese</id>
      <name>Taylor Leese</name>
      <url>http://www.stackmob.com</url>
    </developer>
    <developer>
      <id>kelseyq</id>
      <name>Kelsey Innis</name>
      <url>http://www.stackmob.com</url>
    </developer>
    <developer>
      <id>wpalmeri</id>
      <name>Will Palmeri</name>
      <url>http://www.stackmob.com</url>
    </developer>
    <developer>
      <id>jrwest</id>
      <name>Jordan West</name>
      <url>http://github.com/jrwest</url>
    </developer>
  </developers>
)
