// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.9.2")

// Web plugins
addSbtPlugin("com.github.sbt" % "sbt-coffeescript" % "2.0.1")
addSbtPlugin("com.github.sbt" % "sbt-less" % "2.0.1")
addSbtPlugin("com.github.sbt" % "sbt-jshint" % "2.0.1")
addSbtPlugin("com.github.sbt" % "sbt-rjs" % "2.0.0")
addSbtPlugin("com.github.sbt" % "sbt-digest" % "2.0.0")
addSbtPlugin("com.github.sbt" % "sbt-mocha" % "2.0.0")
addSbtPlugin("io.github.irundaia" % "sbt-sassify" % "1.5.2")

addSbtPlugin("com.github.sbt" % "sbt-web" % "1.5.8")
addSbtPlugin("com.github.platypii" % "sbt-typescript" % "5.3.2")

// Autoprefixer plugin
// lazy val sbtAutoprefixer = uri("https://github.com/matthewrennie/sbt-autoprefixer.git")
// lazy val root = project.in(file(".")).dependsOn(sbtAutoprefixer)

// Play Ebean support, to enable, uncomment this line, and enable in your build.sbt using
// enablePlugins(PlayEbean).
// addSbtPlugin("com.typesafe.sbt" % "sbt-play-ebean" % "1.0.0")
