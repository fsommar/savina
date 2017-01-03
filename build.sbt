organization := "edu.rice.habanero"
name := "savina"
version := "0.0.1-SNAPSHOT"
scalaVersion := "2.11.8"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-target:jvm-1.8",
  "-unchecked",
  "-language:postfixOps",
  "-P:lacasa:enable"
)

scalacOptions in (Compile, doc) ++= baseDirectory.map {
  (bd: File) => Seq[String](
     "-sourcepath", bd.getAbsolutePath,
     "-doc-source-url", "https://github.com/mslinn/changeMe/tree/master€{FILE_PATH}.scala"
  )
}.value

javacOptions ++= Seq(
  "-Xlint:deprecation",
  "-Xlint:unchecked",
  "-source", "1.8",
  "-target", "1.8",
  "-g:vars"
)

resolvers ++= Seq(
  "Scala-Tools Maven2 Repository" at "http://scala-tools.org/repo-releases",
  "Habanero Repository - Rice University" at "http://www.cs.rice.edu/~vs3/hjlib/code/maven-repo"
)

autoCompilerPlugins := true

addCompilerPlugin("io.github.phaller" % "lacasa-plugin_2.11.8" % "0.1.0-SNAPSHOT")

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % scalaVersion.value,
  "org.scala-lang" % "scala-actors" % scalaVersion.value,
  "org.apache.velocity" % "velocity" % "1.7",
  "habanero-java-lib" % "habanero-java-lib" % "0.1.3",
  "com.typesafe.akka" % "akka-actor_2.11" % "2.4.11",
  "org.jetlang" % "jetlang" % "0.2.12",
  "org.scalaz" % "scalaz-concurrent_2.11" % "7.1.0-M7",
  "net.liftweb" % "lift-webkit_2.11" % "2.6-M4",
  "org.functionaljava" % "functionaljava" % "4.1",
  "org.codehaus.groovy" % "groovy-all" % "2.3.4",
  "org.codehaus.gpars" % "gpars" % "1.2.1",
  "fi.jumi" % "jumi-actors" % "0.1.196",
  "junit" % "junit" % "4.8.1" % "test",
  "org.scalatest" % "scalatest_2.11" % "2.1.3" % "test"
)

libraryDependencies += "io.github.phaller" % "akka-playground_2.11.8" % "0.1.0-SNAPSHOT"

logLevel := Level.Info
logLevel in compile := Level.Info
logLevel in test := Level.Info

cancelable := true
