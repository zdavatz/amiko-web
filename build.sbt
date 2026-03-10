name := """AmiKoWeb"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.13"

libraryDependencies ++= Seq(
  javaJdbc,
  ehcache,
  javaWs,
  guice,
  "org.xerial" % "sqlite-jdbc" % "3.45.2.0"
)

resolvers += Resolver.bintrayRepo("webjars","maven")
libraryDependencies ++= Seq(
    "org.webjars.npm" % "types__jquery" % "3.5.32",
    "org.webjars.npm" % "types__sizzle" % "2.3.9"
)
resolveFromWebjarsNodeModulesDir := true
// typescript / logLevel := Level.Debug

lazy val root = (project in file(".")).enablePlugins(PlayJava).enablePlugins(SbtWeb)

// Set engine type to NODE (see also SBT_OPTS)
// Checks for package.json in project's base directory then causes npm to run
JsEngineKeys.engineType := JsEngineKeys.EngineType.Node

// pipelineStages in Assets := Seq(autoprefixer)
// includeFilter in autoprefixer := GlobFilter("*.css")

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

fork in run := false
