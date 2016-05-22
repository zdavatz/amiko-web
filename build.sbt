name := """AmiKoWeb"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  javaJdbc,
  cache,
  javaWs
)

lazy val root = (project in file(".")).enablePlugins(PlayJava)

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

fork in run := false

