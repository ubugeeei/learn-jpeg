ThisBuild / scalaVersion := "3.7.1"
ThisBuild / organization := "io.github.ubugeeei"
ThisBuild / version      := "0.1.0-SNAPSHOT"

lazy val root = project.in(file(".")).settings(
  name                                   := "learn-jpeg",
  // Production code and its tests live together by JPEG concept. The suffix
  // decides the configuration, so MUnit remains absent from runtime artifacts.
  Compile / unmanagedSourceDirectories   := Seq.empty,
  Compile / unmanagedSources             := ((baseDirectory.value / "src") ** "*.scala").get
    .filterNot(_.getName.endsWith("Suite.scala")),
  Test / unmanagedSourceDirectories      := Seq.empty,
  Test / unmanagedSources                := ((baseDirectory.value / "src") ** "*Suite.scala").get,
  libraryDependencies += "org.scalameta" %% "munit" % "1.1.1" % Test,
  Test / parallelExecution               := true,
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Werror")
)
